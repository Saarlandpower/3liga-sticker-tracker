package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeCodecTest {
    private fun sticker(n: String, pos: Int, count: Int) = Sticker(n, pos, "", null, Category.SPIELER, count)

    private val mine = TradeList.from(
        listOf(
            sticker("3", 0, 0), sticker("12", 1, 3), sticker("17", 2, 0), sticker("45", 3, 0),
            sticker("46", 4, 0), sticker("47", 5, 0), sticker("48", 6, 0), sticker("88", 7, 2),
            sticker("89", 8, 2), sticker("90", 9, 1), sticker("S1", 10, 2),
        ),
    )

    @Test fun tradeListFromStickers() {
        assertEquals(listOf("3", "17", "45", "46", "47", "48"), mine.missing)
        assertEquals(mapOf("12" to 2, "88" to 1, "89" to 1, "S1" to 1), mine.duplicates)
        assertEquals(11, mine.checklistSize)
    }

    @Test fun shareTextUsesRanges() {
        val text = TradeCodec.shareText(mine)
        assertTrue(text, text.contains("Fehlen (6): 3, 17, 45-48"))
        assertTrue(text, text.contains("Doppelt (5): 12 (2x), 88-89, S1"))
    }

    @Test fun shareTextForCompleteAlbum() {
        val text = TradeCodec.shareText(TradeList(emptyList(), emptyMap()))
        assertTrue(text.contains("keine – Album komplett!"))
    }

    @Test fun encodeDecodeRoundTrip() {
        val payload = TradeCodec.encode(mine)
        assertEquals("3LST:1|N:11|M:3,17,45-48|D:12x2,88-89,S1", payload)
        val decoded = TradeCodec.decode(payload)
        assertEquals(mine.missing, decoded.missing)
        assertEquals(mine.duplicates, decoded.duplicates)
        assertEquals(11, decoded.checklistSize)
    }

    @Test fun emptyListsRoundTrip() {
        val decoded = TradeCodec.decode(TradeCodec.encode(TradeList(emptyList(), emptyMap(), 400)))
        assertTrue(decoded.missing.isEmpty())
        assertTrue(decoded.duplicates.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun foreignQrIsRejected() {
        TradeCodec.decode("https://example.com")
    }

    @Test fun matchFindsPairsBothWays() {
        val theirs = TradeList(missing = listOf("12", "88", "100"), duplicates = mapOf("45" to 1, "3" to 2, "200" to 1), checklistSize = 11)
        val order = listOf("3", "12", "17", "45", "46", "47", "48", "88", "89", "90", "S1", "100", "200")
        val m = TradeCodec.match(mine, theirs, order)
        assertEquals(listOf("12", "88"), m.iGive)
        assertEquals(listOf("3", "45"), m.iGet)
        assertEquals(listOf("12" to "3", "88" to "45"), m.pairs)
        assertFalse(m.checklistMismatch)
    }

    @Test fun differentChecklistSizesAreFlagged() {
        val theirs = TradeList(emptyList(), emptyMap(), checklistSize = 500)
        assertTrue(TradeCodec.match(mine, theirs, emptyList()).checklistMismatch)
    }
}
