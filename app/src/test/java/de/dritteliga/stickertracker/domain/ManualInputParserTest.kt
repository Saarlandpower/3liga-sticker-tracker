package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualInputParserTest {
    private val known = (1..300).map { it.toString() }.toSet() + setOf("S1", "S2", "S3", "FW3")

    @Test fun commaSeparatedWithRepetitions() {
        val r = ManualInputParser.parse("12, 45, 45, 201", known)
        assertEquals(listOf("12", "45", "45", "201"), r.numbers)
        assertEquals(emptyList<String>(), r.invalid)
    }

    @Test fun spacesSemicolonsAndNewlines() {
        val r = ManualInputParser.parse("1 2;3\n4", known)
        assertEquals(listOf("1", "2", "3", "4"), r.numbers)
    }

    @Test fun rangesAreExpanded() {
        assertEquals(listOf("45", "46", "47", "48"), ManualInputParser.parse("45-48", known).numbers)
        assertEquals(listOf("45", "46"), ManualInputParser.parse("45 – 46", known).numbers)
        assertEquals(listOf("S1", "S2", "S3"), ManualInputParser.parse("s1-s3", known).numbers)
        assertEquals(listOf("S1", "S2"), ManualInputParser.parse("S1-2", known).numbers)
    }

    @Test fun repeatSyntax() {
        assertEquals(listOf("45", "45", "45"), ManualInputParser.parse("45x3", known).numbers)
    }

    @Test fun specialCodesAndLeadingZerosAndHash() {
        assertEquals(listOf("FW3", "7", "12"), ManualInputParser.parse("fw3, 007, #12", known).numbers)
    }

    @Test fun unknownAndGarbageAreReported() {
        val r = ManualInputParser.parse("5, 999, abc-, 12", known)
        assertEquals(listOf("5", "12"), r.numbers)
        assertEquals(listOf("999", "ABC-"), r.invalid)
    }

    @Test fun withoutKnownSetEverythingValidIsAccepted() {
        assertEquals(listOf("999"), ManualInputParser.parse("999").numbers)
    }
}
