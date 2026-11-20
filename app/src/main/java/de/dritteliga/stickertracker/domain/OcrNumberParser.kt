package de.dritteliga.stickertracker.domain

import kotlin.math.abs
import kotlin.math.max

/** Rechteck in Bild-/View-Koordinaten (Android-unabhängig, damit testbar). */
data class OcrBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** Ein von der Texterkennung gefundenes Wort samt Position. */
data class OcrToken(val text: String, val box: OcrBox)

data class OcrMatch(val number: String, val raw: String, val score: Float, val corrected: Boolean)

/**
 * Erkennt Stickernummern im OCR-Text. Typische Verwechslungen werden korrigiert
 * ("I25" → "125", "O" → "0", "S" → "5" …), akzeptiert werden aber nur Nummern,
 * die in der Checkliste vorkommen. Exakte Treffer haben Vorrang vor korrigierten,
 * damit z. B. "S1" als Sonderkürzel erhalten bleibt, wenn es in der Liste steht.
 */
object OcrNumberParser {
    private val digitLookalikes: Map<Char, Char> = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0', 'U' to '0',
        'I' to '1', 'L' to '1', '|' to '1', '!' to '1', 'J' to '1', 'T' to '7',
        'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6', 'A' to '4',
        'o' to '0', 'i' to '1', 'l' to '1', 'z' to '2', 's' to '5', 'b' to '6', 'g' to '9', 'q' to '9',
    )
    private const val MAX_LEN = 6
    private val trimChars = charArrayOf('#', '.', ',', ':', ';', '(', ')', '[', ']', '-', '_', '"', '\'', '*', '°', '/')

    /** Alle Buchstaben-Präfixe der Checkliste, z. B. {"S", "FW"} – längste zuerst. */
    fun prefixesOf(known: Collection<String>): List<String> =
        known.map { StickerNumber.split(it).prefix }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }

    /** Bereinigt ein OCR-Wort: Satzzeichen am Rand weg, "Nr."/"No." entfernen. */
    fun clean(raw: String): String {
        var t = raw.trim().trim(*trimChars).replace(" ", "")
        for (p in listOf("NR.", "NR", "NO.", "Nº", "N°")) {
            if (t.length > p.length && t.uppercase().startsWith(p)) { t = t.substring(p.length).trim(*trimChars); break }
        }
        return t
    }

    /** Ersetzt Buchstaben, die wie Ziffern aussehen. Gibt null zurück, wenn ein Zeichen nicht passt. */
    fun digitize(s: String): String? {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c.isDigit() -> sb.append(c)
                c in digitLookalikes -> sb.append(digitLookalikes.getValue(c))
                c.uppercaseChar() in digitLookalikes -> sb.append(digitLookalikes.getValue(c.uppercaseChar()))
                else -> return null
            }
        }
        return sb.toString()
    }

    /**
     * Interpretiert ein einzelnes OCR-Wort als Stickernummer.
     * @return (Nummer, korrigiert?) oder null, wenn nichts zur Checkliste passt.
     */
    fun match(raw: String, known: Set<String>, prefixes: List<String> = prefixesOf(known)): Pair<String, Boolean>? {
        val cleaned = clean(raw)
        if (cleaned.isEmpty() || cleaned.length > MAX_LEN) return null
        // Wenigstens eine Ziffer oder ein ziffernähnliches Zeichen muss enthalten sein
        if (cleaned.none { it.isDigit() } && digitize(cleaned) == null && prefixes.none { cleaned.uppercase().startsWith(it) }) return null

        // 1) exakter Treffer (inkl. Sonderkürzel wie "S1", "FW3")
        val exact = StickerNumber.normalize(cleaned)
        if (exact in known && exact.any { it.isDigit() }) return exact to false

        // 2) bekanntes Präfix + korrigierter Zahlenteil ("SI" → "S1", "FWZ" → "FW2")
        val upper = cleaned.uppercase()
        for (p in prefixes) {
            if (upper.length > p.length && upper.startsWith(p)) {
                val rest = digitize(cleaned.substring(p.length)) ?: continue
                val n = StickerNumber.normalize(p + rest)
                if (n in known) return n to true
            }
        }

        // 3) komplett als Zahl lesen ("I25" → "125", "4O" → "40")
        val digits = digitize(cleaned) ?: return null
        val n = StickerNumber.normalize(digits)
        return if (n in known) n to (n != exact) else null
    }

    /**
     * Wählt aus allen Wörtern im Bild die plausibelste Stickernummer:
     * Das Wort muss im Rahmen liegen; bevorzugt werden große Schrift und Nähe zur Rahmenmitte.
     */
    fun pickBest(tokens: List<OcrToken>, frame: OcrBox?, known: Set<String>): OcrMatch? {
        if (known.isEmpty()) return null
        val prefixes = prefixesOf(known)
        var best: OcrMatch? = null
        for (t in tokens) {
            val (number, corrected) = match(t.text, known, prefixes) ?: continue
            val score = score(t.box, frame) * (if (corrected) 0.8f else 1f)
            if (score <= 0f) continue
            if (best == null || score > best.score) best = OcrMatch(number, t.text, score, corrected)
        }
        return best
    }

    internal fun score(box: OcrBox, frame: OcrBox?): Float {
        if (box.height <= 0f) return 0f
        if (frame == null) return box.height
        if (!frame.contains(box.centerX, box.centerY)) return 0f
        val sizeScore = box.height / frame.height
        val dx = abs(box.centerX - frame.centerX) / (frame.width / 2f)
        val dy = abs(box.centerY - frame.centerY) / (frame.height / 2f)
        val centrality = 1f - 0.5f * max(dx, dy).coerceIn(0f, 1f)
        return sizeScore * centrality
    }
}
