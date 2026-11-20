package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    @Test fun roundTripKeepsEverything() {
        val clubs = listOf(Club(1, "1. FC Saarbrücken", 0), Club(2, "SV Waldhof Mannheim", 1))
        val stickers = listOf(
            Sticker("1", 0, "Wappen", 1, Category.WAPPEN, 1),
            Sticker("2", 1, "Spieler", 2, Category.SPIELER, 3),
            Sticker("S1", 2, "Glitzer", null, Category.SONDER, 0),
        )
        val text = BackupCodec.encode("20.11.2026 10:00", "Test", clubs, stickers)
        val file = BackupCodec.decode(text)
        assertEquals("Test", file.checklistName)
        val (c, s) = BackupCodec.toDomain(file)
        assertEquals(clubs, c)
        assertEquals(stickers, s)
    }

    @Test(expected = IllegalArgumentException::class)
    fun foreignJsonIsRejected() {
        BackupCodec.decode("""{"app":"andere-app","exportedAt":"x","clubs":[],"stickers":[{"number":"1"}]}""")
    }

    @Test fun unknownClubIdsAreDropped() {
        val file = BackupCodec.decode(
            """{"app":"3liga-sticker-tracker","exportedAt":"x","clubs":[{"id":1,"name":"A"}],
               "stickers":[{"number":"1","clubId":9,"count":-2}]}"""
        )
        val (_, s) = BackupCodec.toDomain(file)
        assertEquals(null, s.single().clubId)
        assertEquals(0, s.single().count)
    }
}
