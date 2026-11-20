package de.dritteliga.stickertracker.domain

/**
 * Fasst fortlaufende Nummern zu Bereichen zusammen: [3, 17, 45, 46, 47, 48] → "3, 17, 45-48".
 * Die Eingabereihenfolge (Checklisten-Reihenfolge) bleibt erhalten; zusammengefasst werden
 * nur direkt aufeinanderfolgende Einträge mit gleichem Präfix und Wert+1 (z. B. "S1-S3").
 */
object RangeFormatter {
    /** Ab wie vielen aufeinanderfolgenden Nummern ein Bereich gebildet wird. */
    const val MIN_RANGE = 2

    fun format(numbers: List<String>, separator: String = ", "): String =
        groupRanges(numbers).joinToString(separator) { it.label }

    data class Range(val first: String, val last: String, val size: Int) {
        val label: String get() = if (size == 1) first else "$first-$last"
    }

    fun groupRanges(numbers: List<String>): List<Range> {
        val result = mutableListOf<Range>()
        var start = 0
        while (start < numbers.size) {
            var end = start
            while (end + 1 < numbers.size && isSuccessor(numbers[end], numbers[end + 1])) end++
            val size = end - start + 1
            if (size >= MIN_RANGE) {
                result += Range(numbers[start], numbers[end], size)
            } else {
                for (i in start..end) result += Range(numbers[i], numbers[i], 1)
            }
            start = end + 1
        }
        return result
    }

    private fun isSuccessor(a: String, b: String): Boolean {
        val pa = StickerNumber.split(a)
        val pb = StickerNumber.split(b)
        return pa.value != null && pb.value != null && pa.prefix == pb.prefix && pb.value == pa.value + 1
    }

    /**
     * Doppelte mit Anzahl: (12, 1 Doppelter), (13, 1), (88, 2) → "12-13, 88 (2x)".
     * Nur Nummern mit genau einem Doppelten werden zu Bereichen zusammengefasst.
     */
    fun formatWithCounts(entries: List<Pair<String, Int>>, separator: String = ", "): String {
        val parts = mutableListOf<String>()
        val run = mutableListOf<String>()
        fun flush() { if (run.isNotEmpty()) { parts += format(run, separator); run.clear() } }
        for ((number, dup) in entries) {
            if (dup <= 0) continue
            if (dup == 1) {
                run += number
            } else {
                flush()
                parts += "$number (${dup}x)"
            }
        }
        flush()
        return parts.joinToString(separator)
    }
}
