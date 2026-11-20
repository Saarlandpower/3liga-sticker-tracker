package de.dritteliga.stickertracker.domain

/**
 * Hilfen rund um Stickernummern wie "1", "125", "S1", "FW3".
 * Eine Nummer besteht aus einem optionalen Buchstaben-Präfix und einem optionalen Zahlenteil.
 */
object StickerNumber {
    private val pattern = Regex("^([A-Z]*)(\\d*)$")

    /** Normiert eine eingegebene Nummer: Großbuchstaben, ohne Leerzeichen/#, führende Nullen weg. */
    fun normalize(raw: String): String {
        val cleaned = raw.trim().uppercase().replace(" ", "").removePrefix("#").removePrefix("NR.").removePrefix("NR")
        val m = pattern.matchEntire(cleaned) ?: return cleaned
        val (prefix, digits) = m.destructured
        if (digits.isEmpty()) return prefix
        val trimmed = digits.trimStart('0').ifEmpty { "0" }
        return prefix + trimmed
    }

    data class Parts(val prefix: String, val value: Int?)

    fun split(number: String): Parts {
        val m = pattern.matchEntire(number.uppercase()) ?: return Parts(number, null)
        val (prefix, digits) = m.destructured
        return Parts(prefix, digits.toIntOrNull())
    }

    /** Sortierung: erst reine Zahlen numerisch, dann Präfixe alphabetisch und numerisch. */
    val comparator: Comparator<String> = Comparator { a, b ->
        val pa = split(a)
        val pb = split(b)
        when {
            pa.prefix != pb.prefix -> when {
                pa.prefix.isEmpty() -> -1
                pb.prefix.isEmpty() -> 1
                else -> pa.prefix.compareTo(pb.prefix)
            }
            else -> compareValues(pa.value, pb.value).takeIf { it != 0 } ?: a.compareTo(b)
        }
    }
}
