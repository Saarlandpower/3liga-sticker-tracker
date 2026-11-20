package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CountLogicTest {
    @Test fun statusIsDerivedFromCount() {
        assertEquals(StickerStatus.FEHLT, CountLogic.status(0))
        assertEquals(StickerStatus.FEHLT, CountLogic.status(-3))
        assertEquals(StickerStatus.VORHANDEN, CountLogic.status(1))
        assertEquals(StickerStatus.DOPPELT, CountLogic.status(2))
        assertEquals(StickerStatus.DOPPELT, CountLogic.status(7))
    }

    @Test fun duplicatesAreCountMinusOne() {
        assertEquals(0, CountLogic.duplicates(0))
        assertEquals(0, CountLogic.duplicates(1))
        assertEquals(1, CountLogic.duplicates(2))
        assertEquals(4, CountLogic.duplicates(5))
    }

    @Test fun incrementAndDecrementNeverGoBelowZero() {
        assertEquals(1, CountLogic.increment(0))
        assertEquals(3, CountLogic.increment(1, 2))
        assertEquals(0, CountLogic.decrement(0))
        assertEquals(0, CountLogic.decrement(1, 5))
        assertEquals(2, CountLogic.decrement(3))
    }

    @Test fun summaryCountsOwnedMissingAndDuplicates() {
        val s = CountLogic.summarize(listOf(0, 1, 2, 3, 0))
        assertEquals(5, s.total)
        assertEquals(3, s.owned)
        assertEquals(2, s.missing)
        assertEquals(3, s.duplicates)
        assertEquals(60, s.percent)
        assertEquals(0.6f, s.progress, 0.0001f)
    }

    @Test fun emptySummaryHasZeroProgress() {
        val s = CountLogic.summarize(emptyList())
        assertEquals(0, s.percent)
        assertEquals(0f, s.progress, 0f)
    }

    @Test fun stickerExposesStatus() {
        val st = Sticker("12", 0, "", null, Category.SPIELER, 3)
        assertEquals(StickerStatus.DOPPELT, st.status)
        assertEquals(2, st.duplicates)
    }
}
