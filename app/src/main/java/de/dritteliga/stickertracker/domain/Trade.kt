package de.dritteliga.stickertracker.domain

/** Fehlende und doppelte Sticker einer Person (Doppelte = Anzahl übriger Exemplare). */
data class TradeList(
    val missing: List<String>,
    val duplicates: Map<String, Int>,
    val checklistSize: Int? = null,
) {
    companion object {
        fun from(stickers: List<Sticker>): TradeList {
            val sorted = stickers.sortedBy { it.position }
            return TradeList(
                missing = sorted.filter { it.count <= 0 }.map { it.number },
                duplicates = sorted.filter { it.duplicates > 0 }.associate { it.number to it.duplicates },
                checklistSize = stickers.size,
            )
        }
    }
}

data class TradeMatch(
    /** Meine Doppelten, die der Partner braucht. */
    val iGive: List<String>,
    /** Doppelte des Partners, die mir fehlen. */
    val iGet: List<String>,
    val checklistMismatch: Boolean,
) {
    /** 1:1-Tauschpaare (so viele wie beide Seiten hergeben). */
    val pairs: List<Pair<String, String>> get() = iGive.zip(iGet)
}

object TradeCodec {
    const val PREFIX = "3LST"
    const val VERSION = 1

    /**
     * Teilbarer Text: "Fehlen: 3, 17, 45-48 / Doppelt: 12 (2x), 88".
     */
    fun shareText(list: TradeList, title: String = "Meine Tauschliste – 3. Liga Sticker"): String = buildString {
        appendLine(title)
        appendLine()
        append("Fehlen (${list.missing.size}): ")
        appendLine(if (list.missing.isEmpty()) "keine – Album komplett!" else RangeFormatter.format(list.missing))
        appendLine()
        val dupTotal = list.duplicates.values.sum()
        append("Doppelt ($dupTotal): ")
        append(if (list.duplicates.isEmpty()) "keine" else RangeFormatter.formatWithCounts(list.duplicates.toList()))
    }

    /** Kompakter QR-Inhalt, z. B. "3LST:1|N:400|M:3,17,45-48|D:12x2,88-90". */
    fun encode(list: TradeList): String = buildString {
        append(PREFIX).append(':').append(VERSION)
        list.checklistSize?.let { append("|N:").append(it) }
        append("|M:").append(RangeFormatter.format(list.missing, ","))
        append("|D:").append(encodeDuplicates(list.duplicates.toList()))
    }

    private fun encodeDuplicates(entries: List<Pair<String, Int>>): String {
        val parts = mutableListOf<String>()
        val run = mutableListOf<String>()
        fun flush() { if (run.isNotEmpty()) { parts += RangeFormatter.format(run, ","); run.clear() } }
        for ((n, d) in entries) {
            when {
                d <= 0 -> Unit
                d == 1 -> run += n
                else -> { flush(); parts += "${n}x$d" }
            }
        }
        flush()
        return parts.joinToString(",")
    }

    fun decode(payload: String): TradeList {
        val text = payload.trim()
        require(text.startsWith("$PREFIX:")) { "Das ist kein Tausch-Code des Sticker-Trackers." }
        val fields = text.split('|').associate { f ->
            val i = f.indexOf(':')
            if (i < 0) f to "" else f.substring(0, i) to f.substring(i + 1)
        }
        val version = fields[PREFIX]?.toIntOrNull() ?: 0
        require(version in 1..VERSION) { "Tausch-Code aus einer neueren App-Version – bitte App aktualisieren." }
        val missing = ManualInputParser.parse(fields["M"].orEmpty()).numbers.distinct()
        val dupNumbers = ManualInputParser.parse(fields["D"].orEmpty()).numbers
        val duplicates = LinkedHashMap<String, Int>()
        dupNumbers.forEach { duplicates[it] = (duplicates[it] ?: 0) + 1 }
        return TradeList(missing, duplicates, fields["N"]?.toIntOrNull())
    }

    fun match(mine: TradeList, theirs: TradeList, order: List<String>): TradeMatch {
        val pos = order.withIndex().associate { it.value to it.index }
        val theirMissing = theirs.missing.toHashSet()
        val myMissing = mine.missing.toHashSet()
        val iGive = mine.duplicates.keys.filter { it in theirMissing }.sortedBy { pos[it] ?: Int.MAX_VALUE }
        val iGet = theirs.duplicates.keys.filter { it in myMissing }.sortedBy { pos[it] ?: Int.MAX_VALUE }
        val mismatch = mine.checklistSize != null && theirs.checklistSize != null && mine.checklistSize != theirs.checklistSize
        return TradeMatch(iGive, iGet, mismatch)
    }
}
