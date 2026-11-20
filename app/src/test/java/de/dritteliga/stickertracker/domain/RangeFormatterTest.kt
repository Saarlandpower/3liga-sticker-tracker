package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeFormatterTest {
    @Test fun consecutiveNumbersBecomeRanges() {
        assertEquals("3, 17, 45-48", RangeFormatter.format(listOf("3", "17", "45", "46", "47", "48")))
    }

    @Test fun pairsAreRangesToo() {
        assertEquals("1-2, 5", RangeFormatter.format(listOf("1", "2", "5")))
    }

    @Test fun singleAndEmpty() {
        assertEquals("7", RangeFormatter.format(listOf("7")))
        assertEquals("", RangeFormatter.format(emptyList()))
    }

    @Test fun prefixedNumbersOnlyJoinWithSamePrefix() {
        assertEquals("399-400, S1-S3, FW1", RangeFormatter.format(listOf("399", "400", "S1", "S2", "S3", "FW1")))
        assertEquals("9, S10", RangeFormatter.format(listOf("9", "S10")))
    }

    @Test fun nonAscendingOrderBreaksRanges() {
        assertEquals("5, 4, 3", RangeFormatter.format(listOf("5", "4", "3")))
    }

    @Test fun duplicatesWithCounts() {
        val entries = listOf("12" to 2, "88" to 1, "89" to 1, "90" to 1, "100" to 1, "150" to 3)
        assertEquals("12 (2x), 88-90, 100, 150 (3x)", RangeFormatter.formatWithCounts(entries))
    }

    @Test fun duplicatesWithZeroAreSkipped() {
        assertEquals("5", RangeFormatter.formatWithCounts(listOf("4" to 0, "5" to 1)))
    }
}
