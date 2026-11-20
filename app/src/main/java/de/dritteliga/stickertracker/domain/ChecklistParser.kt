package de.dritteliga.stickertracker.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ParsedSticker(
    val number: String,
    val name: String,
    val clubName: String?,
    val category: Category,
)

data class ParsedChecklist(
    val name: String?,
    /** Vereinsnamen in Reihenfolge; leer = Datei enthält keine Vereinsinfos. */
    val clubs: List<String>,
    val stickers: List<ParsedSticker>,
    val warnings: List<String> = emptyList(),
) {
    val hasClubInfo: Boolean get() = clubs.isNotEmpty() || stickers.any { it.clubName != null }
}

class ChecklistFormatException(message: String) : Exception(message)

/**
 * Liest Checklisten im JSON- oder CSV-Format (Format siehe README).
 */
object ChecklistParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Erkennt das Format am Inhalt (bzw. optional an der Dateiendung). */
    fun parse(content: String, fileName: String? = null, defaultPlaceholderCount: Int = 0): ParsedChecklist {
        val text = content.removePrefix("﻿").trim()
        if (text.isEmpty()) throw ChecklistFormatException("Die Datei ist leer.")
        val looksJson = text.startsWith("{") || text.startsWith("[")
        val isCsvName = fileName?.lowercase()?.let { it.endsWith(".csv") || it.endsWith(".txt") } == true
        return if (looksJson && !isCsvName) parseJson(text, defaultPlaceholderCount) else parseCsv(text)
    }

    fun parseJson(text: String, defaultPlaceholderCount: Int = 0): ParsedChecklist {
        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ChecklistFormatException("Ungültiges JSON: ${e.message?.take(120)}")
        }
        val obj: JsonObject
        val stickerArray: JsonArray?
        when (root) {
            is JsonArray -> { obj = JsonObject(emptyMap()); stickerArray = root }
            is JsonObject -> { obj = root; stickerArray = root["stickers"] as? JsonArray }
            else -> throw ChecklistFormatException("JSON muss ein Objekt oder eine Liste sein.")
        }
        val name = obj.str("name")
        val clubs = (obj["clubs"] as? JsonArray)?.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> el.str("name")
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()

        val warnings = mutableListOf<String>()
        val stickers = mutableListOf<ParsedSticker>()
        stickerArray?.forEachIndexed { i, el ->
            val s = el as? JsonObject
            val number = when {
                s != null -> s.str("number") ?: s.str("nummer") ?: s.str("nr")
                el is JsonPrimitive -> el.contentOrNull
                else -> null
            }?.let(StickerNumber::normalize)
            if (number.isNullOrEmpty()) {
                warnings += "Eintrag ${i + 1}: keine Nummer – übersprungen"
                return@forEachIndexed
            }
            val clubName = s?.let { it.str("club") ?: it.str("verein") }
                ?: s?.let { it.int("clubIndex") ?: it.int("vereinIndex") }?.let { clubs.getOrNull(it) }
            stickers += ParsedSticker(
                number = number,
                name = s?.let { it.str("name") }.orEmpty(),
                clubName = clubName?.trim()?.takeIf { it.isNotEmpty() },
                category = Category.parse(s?.let { it.str("category") ?: it.str("kategorie") }),
            )
        }

        val placeholderCount = obj.int("placeholderCount") ?: defaultPlaceholderCount
        if (stickers.isEmpty() && placeholderCount > 0) {
            stickers += placeholders(placeholderCount)
        }
        if (stickers.isEmpty()) throw ChecklistFormatException("Die Checkliste enthält keine Sticker.")
        return finish(name, clubs, stickers, warnings)
    }

    fun parseCsv(text: String): ParsedChecklist {
        val lines = text.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) throw ChecklistFormatException("Die CSV-Datei ist leer.")
        val delimiter = detectDelimiter(lines.first())
        val rows = lines.map { splitCsvLine(it, delimiter) }
        val header = rows.first().map { it.trim().lowercase() }
        val numberHeaders = setOf("nummer", "number", "nr", "nr.", "no", "#")
        val hasHeader = header.any { it in numberHeaders }
        val idxNumber = if (hasHeader) header.indexOfFirst { it in numberHeaders } else 0
        val idxName = if (hasHeader) header.indexOfFirst { it in setOf("name", "spieler", "titel") } else 1
        val idxClub = if (hasHeader) header.indexOfFirst { it in setOf("verein", "club", "klub", "team") } else 2
        val idxCat = if (hasHeader) header.indexOfFirst { it in setOf("kategorie", "category", "typ", "type") } else 3

        val warnings = mutableListOf<String>()
        val stickers = mutableListOf<ParsedSticker>()
        rows.drop(if (hasHeader) 1 else 0).forEachIndexed { i, cols ->
            val number = cols.getOrNull(idxNumber)?.let(StickerNumber::normalize)
            if (number.isNullOrEmpty()) {
                warnings += "Zeile ${i + 1 + if (hasHeader) 1 else 0}: keine Nummer – übersprungen"
                return@forEachIndexed
            }
            stickers += ParsedSticker(
                number = number,
                name = cols.getOrNull(idxName)?.trim().orEmpty(),
                clubName = cols.getOrNull(idxClub)?.trim()?.takeIf { it.isNotEmpty() },
                category = Category.parse(cols.getOrNull(idxCat)),
            )
        }
        if (stickers.isEmpty()) throw ChecklistFormatException("Die CSV-Datei enthält keine Sticker.")
        return finish(null, emptyList(), stickers, warnings)
    }

    fun placeholders(count: Int): List<ParsedSticker> =
        (1..count).map { ParsedSticker(it.toString(), "", null, Category.SPIELER) }

    private fun finish(
        name: String?,
        clubs: List<String>,
        stickers: List<ParsedSticker>,
        warnings: MutableList<String>,
    ): ParsedChecklist {
        val seen = HashSet<String>()
        val unique = stickers.filter { s ->
            seen.add(s.number).also { added -> if (!added) warnings += "Nummer ${s.number} doppelt – nur erster Eintrag übernommen" }
        }
        // Vereine aus den Stickern ergänzen, die nicht in der Vereinsliste stehen
        val allClubs = LinkedHashSet(clubs)
        unique.mapNotNullTo(allClubs) { it.clubName }
        return ParsedChecklist(name, allClubs.toList(), unique, warnings)
    }

    private fun detectDelimiter(line: String): Char =
        listOf(';', '\t', ',').maxBy { d -> line.count { it == d } }.takeIf { d -> line.contains(d) } ?: ';'

    internal fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}
