package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Prüft die mitgelieferte Checkliste (assets/checklist.json) auf Vollständigkeit. */
class BundledChecklistTest {
    private val parsed = ChecklistParser.parse(File("src/main/assets/checklist.json").readText(), "checklist.json")

    @Test fun containsAllNumbersFrom1To423InOrder() {
        assertEquals((1..423).map { it.toString() }, parsed.stickers.map { it.number })
        assertEquals(emptyList<String>(), parsed.warnings)
    }

    @Test fun everyStickerBelongsToAGroup() {
        assertEquals(23, parsed.clubs.size) // 20 Vereine + Intro, Heimat der 3. Liga, Stars
        assertEquals(0, parsed.stickers.count { it.clubName == null })
    }

    @Test fun clubsHave18StickersEach() {
        val byClub = parsed.stickers.groupingBy { it.clubName }.eachCount()
        val clubs = parsed.clubs.filterNot { it in setOf("Intro", "Heimat der 3. Liga", "Die Stars der 3. Liga") }
        assertEquals(20, clubs.size)
        clubs.forEach { assertEquals(it, 18, byClub[it]) }
        assertEquals(40, byClub["Heimat der 3. Liga"])
        assertEquals(20, byClub["Die Stars der 3. Liga"])
    }

    @Test fun sampleAssignments() {
        val club = parsed.stickers.associate { it.number to it.clubName }
        assertEquals("Alemannia Aachen", club["44"])
        assertEquals("Fortuna Düsseldorf", club["98"])
        assertEquals("1. FC Saarbrücken", club["331"])
        assertEquals("FC Würzburger Kickers", club["403"])
        assertEquals("Die Stars der 3. Liga", club["404"])
    }

    @Test fun lastStickerOfEachClubIsTheCoach() {
        val coaches = parsed.stickers.filter { it.category == Category.TRAINER }
        assertEquals((61..403 step 18).map { it.toString() }, coaches.map { it.number })
        assertEquals(20 * 17, parsed.stickers.count { it.category == Category.SPIELER })
        assertEquals(63, parsed.stickers.count { it.category == Category.SONDER })
    }
}
