package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanGateTest {
    @Test fun numberNeedsTwoHitsBeforeCounting() {
        val g = ScanGate(requiredHits = 2, stabilityWindowMs = 1000, cooldownMs = 2000)
        assertNull(g.onFrame("12", 0))
        assertEquals("12", g.onFrame("12", 100))
    }

    @Test fun sameNumberIsIgnoredWhileVisible() {
        val g = ScanGate(requiredHits = 1, cooldownMs = 2000)
        assertEquals("12", g.onFrame("12", 0))
        // bleibt 5 Sekunden im Bild – jede Sichtung verlängert die Sperre
        var t = 200L
        while (t <= 5000) { assertNull(g.onFrame("12", t)); t += 200 }
    }

    @Test fun sameNumberCountsAgainAfterCooldownOutOfView() {
        val g = ScanGate(requiredHits = 1, cooldownMs = 2000)
        assertEquals("12", g.onFrame("12", 0))
        assertNull(g.onFrame(null, 500))
        assertNull(g.onFrame("12", 1500))       // noch innerhalb 2 s nach letzter Sichtung
        assertEquals("12", g.onFrame("12", 3600)) // > 2 s nicht gesehen → neuer Sticker
    }

    @Test fun differentNumberCountsImmediately() {
        val g = ScanGate(requiredHits = 1, cooldownMs = 2000)
        assertEquals("12", g.onFrame("12", 0))
        assertEquals("13", g.onFrame("13", 100))
        assertNull(g.onFrame("13", 200))
    }

    @Test fun singleMisreadIsFiltered() {
        val g = ScanGate(requiredHits = 2, stabilityWindowMs = 500)
        assertNull(g.onFrame("12", 0))
        assertNull(g.onFrame("72", 100))  // Fehllesung unterbricht
        assertNull(g.onFrame("12", 200))
        assertEquals("12", g.onFrame("12", 300))
    }

    @Test fun hitsTooFarApartDoNotCount() {
        val g = ScanGate(requiredHits = 2, stabilityWindowMs = 500)
        assertNull(g.onFrame("12", 0))
        assertNull(g.onFrame("12", 900))
        assertEquals("12", g.onFrame("12", 1000))
    }

    @Test fun releaseAllowsImmediateRecount() {
        val g = ScanGate(requiredHits = 1, cooldownMs = 2000)
        assertEquals("12", g.onFrame("12", 0))
        g.release("12")
        assertEquals("12", g.onFrame("12", 100))
    }
}
