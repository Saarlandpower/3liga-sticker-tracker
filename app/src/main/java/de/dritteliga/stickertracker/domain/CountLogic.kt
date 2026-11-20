package de.dritteliga.stickertracker.domain

/**
 * Zähllogik: 0 = fehlt, 1 = vorhanden, >1 = doppelt (Doppelte = anzahl - 1).
 */
object CountLogic {
    fun status(count: Int): StickerStatus = when {
        count <= 0 -> StickerStatus.FEHLT
        count == 1 -> StickerStatus.VORHANDEN
        else -> StickerStatus.DOPPELT
    }

    fun duplicates(count: Int): Int = (count - 1).coerceAtLeast(0)

    fun increment(count: Int, by: Int = 1): Int = (count.coerceAtLeast(0) + by).coerceAtLeast(0)

    fun decrement(count: Int, by: Int = 1): Int = (count - by).coerceAtLeast(0)

    data class Summary(
        val total: Int,
        val owned: Int,
        val missing: Int,
        val duplicates: Int,
    ) {
        /** Fortschritt 0.0 … 1.0 */
        val progress: Float get() = if (total == 0) 0f else owned.toFloat() / total
        val percent: Int get() = if (total == 0) 0 else (owned * 100) / total
    }

    fun summarize(counts: Collection<Int>): Summary {
        val owned = counts.count { it > 0 }
        return Summary(
            total = counts.size,
            owned = owned,
            missing = counts.size - owned,
            duplicates = counts.sumOf { duplicates(it) },
        )
    }
}
