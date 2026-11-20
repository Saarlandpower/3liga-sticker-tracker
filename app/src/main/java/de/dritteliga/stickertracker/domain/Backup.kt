package de.dritteliga.stickertracker.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Format der Komplett-Sicherung (Export/Import über das Storage Access Framework). */
@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val formatVersion: Int = 1,
    val exportedAt: String,
    val checklistName: String = "",
    val clubs: List<BackupClub>,
    val stickers: List<BackupSticker>,
) {
    companion object {
        const val APP_ID = "3liga-sticker-tracker"
    }
}

@Serializable
data class BackupClub(val id: Int, val name: String)

@Serializable
data class BackupSticker(
    val number: String,
    val name: String = "",
    val clubId: Int? = null,
    val category: String = Category.SPIELER.name,
    val count: Int = 0,
)

object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(exportedAt: String, checklistName: String, clubs: List<Club>, stickers: List<Sticker>): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                exportedAt = exportedAt,
                checklistName = checklistName,
                clubs = clubs.sortedBy { it.position }.map { BackupClub(it.id, it.name) },
                stickers = stickers.sortedBy { it.position }.map {
                    BackupSticker(it.number, it.name, it.clubId, it.category.name, it.count)
                },
            ),
        )

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text.removePrefix("﻿"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Keine gültige Sicherungsdatei.")
        }
        require(file.app == BackupFile.APP_ID) { "Die Datei stammt nicht vom Sticker-Tracker." }
        require(file.stickers.isNotEmpty()) { "Die Sicherung enthält keine Sticker." }
        return file
    }

    fun toDomain(file: BackupFile): Pair<List<Club>, List<Sticker>> {
        val clubs = file.clubs.mapIndexed { i, c -> Club(c.id, c.name, i) }
        val clubIds = clubs.mapTo(HashSet()) { it.id }
        val seen = HashSet<String>()
        val stickers = file.stickers
            .filter { seen.add(StickerNumber.normalize(it.number)) }
            .mapIndexed { i, s ->
                Sticker(
                    number = StickerNumber.normalize(s.number),
                    position = i,
                    name = s.name,
                    clubId = s.clubId?.takeIf { it in clubIds },
                    category = runCatching { Category.valueOf(s.category) }.getOrElse { Category.parse(s.category) },
                    count = s.count.coerceAtLeast(0),
                )
            }
        return clubs to stickers
    }
}
