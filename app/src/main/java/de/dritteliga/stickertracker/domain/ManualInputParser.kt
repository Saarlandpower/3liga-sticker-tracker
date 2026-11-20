package de.dritteliga.stickertracker.domain

/**
 * Parst manuelle Eingaben wie "12, 45, 45, 201", "45-48", "S1-S3" oder "45x3".
 * Wiederholungen bleiben erhalten (jede Nennung zählt einmal).
 */
object ManualInputParser {
    data class Result(val numbers: List<String>, val invalid: List<String>)

    private val separators = Regex("[,;\\s]+")
    private val rangePattern = Regex("^([A-Z]*)(\\d+)[-–]([A-Z]*)(\\d+)$")
    private val repeatPattern = Regex("^(.+?)[X*](\\d{1,2})$")
    private const val MAX_RANGE = 1000

    fun parse(input: String, known: Set<String>? = null): Result {
        val numbers = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        // "45 - 48" → "45-48"
        val text = input.uppercase().replace(Regex("\\s*[-–]\\s*"), "-")
        for (token in text.split(separators).filter { it.isNotBlank() }) {
            val expanded = expand(token)
            if (expanded == null) { invalid += token; continue }
            for (n in expanded) {
                if (known == null || n in known) numbers += n else invalid += n
            }
        }
        return Result(numbers, invalid.distinct())
    }

    private fun expand(token: String): List<String>? {
        repeatPattern.matchEntire(token)?.let { m ->
            val times = m.groupValues[2].toInt()
            val base = expand(m.groupValues[1]) ?: return null
            if (times !in 1..50) return null
            return List(times) { base }.flatten()
        }
        rangePattern.matchEntire(token)?.let { m ->
            val (p1, a, p2, b) = m.destructured
            if (p2.isNotEmpty() && p2 != p1) return null
            val from = a.toInt()
            val to = b.toInt()
            if (to < from || to - from > MAX_RANGE) return null
            return (from..to).map { p1 + it }
        }
        val n = StickerNumber.normalize(token)
        return if (n.matches(Regex("^[A-Z]*\\d+$|^[A-Z]+$"))) listOf(n) else null
    }
}
