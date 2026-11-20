package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrNumberParserTest {
    private val known: Set<String> = (1..400).map { it.toString() }.toSet()
    private val knownWithSpecials: Set<String> = known + setOf("S1", "S2", "FW3")

    private fun m(raw: String, k: Set<String> = known) = OcrNumberParser.match(raw, k)?.first

    @Test fun plainNumbers() {
        assertEquals("125", m("125"))
        assertEquals("7", m("7"))
        assertEquals("7", m("007"))
    }

    @Test fun lookalikeLettersAreCorrected() {
        assertEquals("125", m("I25"))
        assertEquals("125", m("l25"))
        assertEquals("125", m("|25"))
        assertEquals("100", m("1OO"))
        assertEquals("40", m("4O"))
        assertEquals("205", m("2O5"))
        assertEquals("55", m("S5"))
        assertEquals("28", m("2B"))
        assertEquals("22", m("ZZ"))
    }

    @Test fun correctionIsFlagged() {
        assertEquals("125" to true, OcrNumberParser.match("I25", known))
        assertEquals("125" to false, OcrNumberParser.match("125", known))
    }

    @Test fun punctuationAndPrefixesAreStripped() {
        assertEquals("125", m("#125"))
        assertEquals("125", m("125."))
        assertEquals("125", m("(125)"))
        assertEquals("12", m("Nr.12"))
        assertEquals("12", m("No.12"))
    }

    @Test fun numbersOutsideChecklistAreRejected() {
        assertNull(m("401"))
        assertNull(m("2025"))
        assertNull(m("0"))
        assertNull(m("1234567"))
    }

    @Test fun wordsAreRejected() {
        assertNull(m("PANINI"))
        assertNull(m("Liga"))
        assertNull(m("Italy"))
        assertNull(m(""))
        assertNull(m("--"))
    }

    @Test fun specialCodesKeepTheirPrefix() {
        assertEquals("S1", m("S1", knownWithSpecials))
        assertEquals("S1", m("s1", knownWithSpecials))
        assertEquals("S1", m("SI", knownWithSpecials))
        assertEquals("FW3", m("FW3", knownWithSpecials))
        assertEquals("FW3", m("fw3", knownWithSpecials))
        // "S" vor einer Zahl ohne passendes Sonderkürzel → als 5 lesen
        assertEquals("57", m("S7", knownWithSpecials))
    }

    @Test fun pickBestPrefersLargeCenteredTextInsideFrame() {
        val frame = OcrBox(100f, 100f, 500f, 700f)
        val tokens = listOf(
            OcrToken("12", OcrBox(120f, 120f, 160f, 140f)),       // klein, Ecke
            OcrToken("125", OcrBox(240f, 330f, 360f, 430f)),      // groß, mittig
            OcrToken("300", OcrBox(10f, 10f, 200f, 90f)),         // groß, aber außerhalb
            OcrToken("PANINI", OcrBox(200f, 600f, 400f, 640f)),
        )
        val best = OcrNumberParser.pickBest(tokens, frame, known)
        assertEquals("125", best?.number)
    }

    @Test fun pickBestPrefersExactOverCorrectedOfSimilarSize() {
        val frame = OcrBox(0f, 0f, 400f, 400f)
        val tokens = listOf(
            OcrToken("I7", OcrBox(150f, 150f, 250f, 250f)),
            OcrToken("18", OcrBox(150f, 150f, 250f, 250f)),
        )
        assertEquals("18", OcrNumberParser.pickBest(tokens, frame, known)?.number)
    }

    @Test fun pickBestReturnsNullWithoutValidNumber() {
        val frame = OcrBox(0f, 0f, 400f, 400f)
        val tokens = listOf(OcrToken("PANINI", OcrBox(10f, 10f, 300f, 100f)), OcrToken("999", OcrBox(100f, 100f, 300f, 300f)))
        assertNull(OcrNumberParser.pickBest(tokens, frame, known))
    }

    @Test fun pickBestWithoutFrameUsesSize() {
        val tokens = listOf(
            OcrToken("5", OcrBox(0f, 0f, 10f, 10f)),
            OcrToken("6", OcrBox(0f, 0f, 50f, 50f)),
        )
        assertEquals("6", OcrNumberParser.pickBest(tokens, null, known)?.number)
    }
}
