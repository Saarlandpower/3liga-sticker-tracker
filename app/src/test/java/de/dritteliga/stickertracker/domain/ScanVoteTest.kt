package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanVoteTest {
    @Test fun clearMajorityIsSure() {
        val r = ScanVote.decide(listOf("125", "125", "125", null, "125"))
        assertEquals(ScanVote.Result.Sure("125", 4, 5), r)
    }

    @Test fun competingReadsAreUnsure() {
        val r = ScanVote.decide(listOf("125", "126", "125", "126"))
        assertEquals(ScanVote.Result.Unsure("125", 4), r)
    }

    @Test fun singleOutlierIsTolerated() {
        val r = ScanVote.decide(listOf("125", "125", "125", "126", "125"))
        assertEquals("125", (r as ScanVote.Result.Sure).number)
    }

    @Test fun singleHitIsNotEnough() {
        assertEquals(ScanVote.Result.Unsure("12", 1), ScanVote.decide(listOf("12")))
    }

    @Test fun mostlyEmptyFramesAreUnsure() {
        val r = ScanVote.decide(listOf("12", "12", null, null, null, null))
        assertEquals(ScanVote.Result.Unsure("12", 6), r)
    }

    @Test fun nothingRecognized() {
        assertEquals(ScanVote.Result.Nothing, ScanVote.decide(listOf(null, null, null)))
        assertEquals(ScanVote.Result.Nothing, ScanVote.decide(emptyList()))
    }

    @Test fun stableLiveNumber() {
        assertEquals("7", ScanVote.stable(listOf("7", "7", null, "7", "7", "1")))
        assertNull(ScanVote.stable(listOf("7", "1", "7", "1", null, null)))
        assertNull(ScanVote.stable(listOf("7", "7")))
    }
}
