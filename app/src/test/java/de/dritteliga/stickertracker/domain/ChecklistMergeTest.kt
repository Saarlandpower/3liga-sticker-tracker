package de.dritteliga.stickertracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistMergeTest {
    private val clubs = listOf(Club(1, "Alt A", 0), Club(2, "Alt B", 1))
    private val existing = listOf(
        Sticker("1", 0, "", 1, Category.SPIELER, 2),
        Sticker("2", 1, "", 2, Category.SPIELER, 0),
        Sticker("3", 2, "", null, Category.SPIELER, 1),
        Sticker("400", 3, "", null, Category.SPIELER, 1),
    )

    @Test fun countsArePreservedByNumber() {
        val parsed = ChecklistParser.parse("""{"clubs":["Neu"],"stickers":[
            {"number":"3","name":"Spieler Drei","club":"Neu"},
            {"number":"1","name":"Wappen","club":"Neu","category":"Wappen"},
            {"number":"S1","name":"Glitzer"}]}""")
        val r = ChecklistMerge.merge(existing, clubs, parsed)
        assertEquals(listOf("3", "1", "S1"), r.stickers.map { it.number })
        assertEquals(listOf(1, 2, 0), r.stickers.map { it.count })
        assertEquals(listOf(0, 1, 2), r.stickers.map { it.position })
        assertEquals(listOf("Neu"), r.clubs.map { it.name })
        assertEquals(1, r.stickers[0].clubId)
        assertEquals(listOf("400"), r.droppedWithCount)
        assertEquals(2, r.keptCounts)
    }

    @Test fun listWithoutClubInfoKeepsClubsAndAssignments() {
        val parsed = ParsedChecklist(null, emptyList(), ChecklistParser.placeholders(3))
        val r = ChecklistMerge.merge(existing, clubs, parsed)
        assertEquals(clubs, r.clubs)
        assertEquals(listOf(1, 2, null), r.stickers.map { it.clubId })
        assertEquals(listOf("400"), r.droppedWithCount)
    }
}
