package de.dritteliga.stickertracker.domain

/**
 * Entscheidet im Serienmodus, wann eine erkannte Nummer gezählt wird.
 *
 * - Eine Nummer muss in [requiredHits] Bildern innerhalb von [stabilityWindowMs] erkannt werden
 *   (filtert einzelne Fehllesungen).
 * - Nach dem Zählen wird dieselbe Nummer ignoriert, solange sie weiter im Bild ist bzw. bis sie
 *   [cooldownMs] lang nicht mehr gesehen wurde – so wird ein Sticker nicht doppelt gezählt.
 * - Eine andere Nummer kann sofort gezählt werden.
 */
class ScanGate(
    private val requiredHits: Int = 2,
    private val stabilityWindowMs: Long = 1000,
    private val cooldownMs: Long = 2000,
) {
    private var lastCounted: String? = null
    private var lastCountedSeenAt: Long = Long.MIN_VALUE / 2

    private var candidate: String? = null
    private var candidateHits = 0
    private var candidateLastSeen: Long = Long.MIN_VALUE / 2

    /** @return die Nummer, die jetzt gezählt werden soll, sonst null. */
    @Synchronized
    fun onFrame(number: String?, nowMs: Long): String? {
        if (number == null) return null

        if (number == lastCounted && nowMs - lastCountedSeenAt < cooldownMs) {
            lastCountedSeenAt = nowMs // bleibt im Bild → Sperre verlängern
            return null
        }

        if (number == candidate && nowMs - candidateLastSeen <= stabilityWindowMs) {
            candidateHits++
        } else {
            candidate = number
            candidateHits = 1
        }
        candidateLastSeen = nowMs

        if (candidateHits >= requiredHits) {
            lastCounted = number
            lastCountedSeenAt = nowMs
            candidate = null
            candidateHits = 0
            return number
        }
        return null
    }

    /** Nach "Rückgängig" darf dieselbe Nummer sofort wieder gezählt werden. */
    @Synchronized
    fun release(number: String) {
        if (lastCounted == number) lastCounted = null
    }

    @Synchronized
    fun reset() {
        lastCounted = null
        candidate = null
        candidateHits = 0
    }
}
