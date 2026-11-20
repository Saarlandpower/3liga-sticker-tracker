package de.dritteliga.stickertracker.domain

/**
 * Berechnet, wie eine neue Checkliste mit dem vorhandenen Bestand zusammengeführt wird.
 * Anzahlen bleiben für alle Nummern erhalten, die auch in der neuen Liste vorkommen.
 */
object ChecklistMerge {
    data class Result(
        val clubs: List<Club>,
        val stickers: List<Sticker>,
        /** Nummern mit Bestand (>0), die in der neuen Liste fehlen und entfernt würden. */
        val droppedWithCount: List<String>,
        val keptCounts: Int,
    )

    fun merge(existingStickers: List<Sticker>, existingClubs: List<Club>, parsed: ParsedChecklist): Result {
        val oldByNumber = existingStickers.associateBy { it.number }
        val keepClubs = !parsed.hasClubInfo
        val clubs: List<Club> = if (keepClubs) existingClubs
        else parsed.clubs.mapIndexed { i, n -> Club(id = i + 1, name = n, position = i) }
        val clubIdByName = clubs.associate { it.name.lowercase() to it.id }

        var kept = 0
        val stickers = parsed.stickers.mapIndexed { i, p ->
            val old = oldByNumber[p.number]
            if (old != null && old.count > 0) kept++
            Sticker(
                number = p.number,
                position = i,
                name = p.name.ifEmpty { if (keepClubs) old?.name.orEmpty() else "" },
                clubId = if (keepClubs) old?.clubId else p.clubName?.let { clubIdByName[it.lowercase()] },
                category = if (keepClubs && p.name.isEmpty() && old != null) old.category else p.category,
                count = old?.count ?: 0,
            )
        }
        val newNumbers = stickers.mapTo(HashSet()) { it.number }
        val dropped = existingStickers.filter { it.count > 0 && it.number !in newNumbers }.map { it.number }
        return Result(clubs, stickers, dropped, kept)
    }
}
