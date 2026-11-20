package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistParserTest {
    @Test fun jsonWithStickersAndClubs() {
        val json = """
            {
              "name": "Test",
              "clubs": ["Verein A", {"name": "Verein B"}],
              "stickers": [
                {"number": "1", "name": "Wappen A", "club": "Verein A", "category": "Wappen"},
                {"number": "002", "name": "Spieler", "club": "Verein B", "category": "spieler"},
                {"number": "s1", "name": "Glitzer", "category": "Glitzer"},
                {"number": "FW3", "name": "Fan", "club": "Neu", "category": "Mannschaftsfoto"}
              ]
            }
        """.trimIndent()
        val p = ChecklistParser.parse(json, "liste.json")
        assertEquals("Test", p.name)
        assertEquals(listOf("1", "2", "S1", "FW3"), p.stickers.map { it.number })
        assertEquals(listOf("Verein A", "Verein B", "Neu"), p.clubs)
        assertEquals(Category.WAPPEN, p.stickers[0].category)
        assertEquals(Category.SONDER, p.stickers[2].category)
        assertEquals(Category.MANNSCHAFT, p.stickers[3].category)
        assertNull(p.stickers[2].clubName)
    }

    @Test fun jsonPlaceholderCountGeneratesNumbers() {
        val p = ChecklistParser.parse("""{"placeholderCount": 5, "clubs": ["A"], "stickers": []}""")
        assertEquals(listOf("1", "2", "3", "4", "5"), p.stickers.map { it.number })
        assertEquals(listOf("A"), p.clubs)
    }

    @Test fun jsonFallsBackToDefaultPlaceholderCount() {
        val p = ChecklistParser.parse("""{"stickers": []}""", defaultPlaceholderCount = 3)
        assertEquals(3, p.stickers.size)
    }

    @Test fun plainJsonArrayOfNumbers() {
        val p = ChecklistParser.parse("""["1", "2", "S1"]""")
        assertEquals(listOf("1", "2", "S1"), p.stickers.map { it.number })
        assertFalse(p.hasClubInfo)
    }

    @Test fun duplicateNumbersProduceWarning() {
        val p = ChecklistParser.parse("""{"stickers":[{"number":"1"},{"number":"01"},{"number":"2"}]}""")
        assertEquals(listOf("1", "2"), p.stickers.map { it.number })
        assertEquals(1, p.warnings.size)
    }

    @Test(expected = ChecklistFormatException::class)
    fun invalidJsonThrows() {
        ChecklistParser.parse("{ kaputt", "x.json")
    }

    @Test fun csvWithHeaderAndSemicolons() {
        val csv = """
            nummer;name;verein;kategorie
            1;Wappen;1. FC Saarbrücken;Wappen
            2;"Müller; Thomas";1. FC Saarbrücken;Spieler
            S1;Glitzer-Logo;;Sonder
        """.trimIndent()
        val p = ChecklistParser.parse(csv, "liste.csv")
        assertEquals(listOf("1", "2", "S1"), p.stickers.map { it.number })
        assertEquals("Müller; Thomas", p.stickers[1].name)
        assertEquals(listOf("1. FC Saarbrücken"), p.clubs)
        assertEquals(Category.SONDER, p.stickers[2].category)
        assertTrue(p.hasClubInfo)
    }

    @Test fun csvWithCommasAndWithoutHeader() {
        val p = ChecklistParser.parse("10,Spieler X,Verein\n11,Spieler Y,Verein\n", "a.csv")
        assertEquals(listOf("10", "11"), p.stickers.map { it.number })
        assertEquals("Spieler Y", p.stickers[1].name)
    }

    @Test fun csvNumbersOnly() {
        val p = ChecklistParser.parse("nummer\n1\n2\n3", "nur.csv")
        assertEquals(3, p.stickers.size)
        assertFalse(p.hasClubInfo)
    }

    @Test fun categoryAliases() {
        assertEquals(Category.SONDER, Category.parse("Glitzer"))
        assertEquals(Category.SONDER, Category.parse("special"))
        assertEquals(Category.WAPPEN, Category.parse("badge"))
        assertEquals(Category.MANNSCHAFT, Category.parse("Teamfoto"))
        assertEquals(Category.SPIELER, Category.parse(null))
        assertEquals(Category.SPIELER, Category.parse("unbekannt"))
    }
}
