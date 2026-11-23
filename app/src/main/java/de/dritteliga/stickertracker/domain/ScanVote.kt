package de.dritteliga.stickertracker.domain

/**
 * Mehrheitsentscheid über mehrere Kamerabilder.
 *
 * Beim manuellen Auslösen werden die Erkennungen aus einem kurzen Zeitfenster gesammelt
 * (null = in diesem Bild keine Nummer). Gezählt wird nur, wenn eine Nummer klar überwiegt –
 * einzelne Fehllesungen ("125" vs. "126") führen so nicht zu einer falschen Zählung.
 */
object ScanVote {
    sealed interface Result {
        /** Eindeutig erkannt. */
        data class Sure(val number: String, val hits: Int, val frames: Int) : Result
        /** Es wurde etwas erkannt, aber nicht eindeutig genug. */
        data class Unsure(val best: String?, val frames: Int) : Result
        /** In keinem Bild eine passende Nummer. */
        data object Nothing : Result
    }

    /**
     * @param minHits wie oft die Nummer mindestens erkannt worden sein muss
     * @param minShareOfHits Anteil an allen Erkennungen (Konkurrenz durch andere Nummern)
     * @param minShareOfFrames Anteil an allen Bildern (auch Bilder ohne Treffer zählen mit)
     */
    fun decide(
        samples: List<String?>,
        minHits: Int = 2,
        minShareOfHits: Float = 0.75f,
        minShareOfFrames: Float = 0.5f,
    ): Result {
        val hits = samples.filterNotNull()
        if (hits.isEmpty()) return Result.Nothing
        val (best, count) = hits.groupingBy { it }.eachCount().maxBy { it.value }.toPair()
        val sure = count >= minHits &&
            count >= minShareOfHits * hits.size &&
            count >= minShareOfFrames * samples.size
        return if (sure) Result.Sure(best, count, samples.size) else Result.Unsure(best, samples.size)
    }

    /** Stabile Live-Anzeige: Nummer, die in den letzten Bildern deutlich überwiegt, sonst null. */
    fun stable(recent: List<String?>, minHits: Int = 3): String? =
        (decide(recent, minHits = minHits, minShareOfHits = 0.75f, minShareOfFrames = 0.5f) as? Result.Sure)?.number
}
