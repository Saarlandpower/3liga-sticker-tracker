package de.dritteliga.stickertracker.domain

/** Kategorie eines Stickers. [label] ist der deutsche Anzeigename. */
enum class Category(val label: String) {
    SPIELER("Spieler"),
    WAPPEN("Wappen"),
    MANNSCHAFT("Mannschaftsfoto"),
    SONDER("Sonder/Glitzer");

    companion object {
        /** Tolerantes Parsen aus Checklisten-Dateien (deutsch/englisch, Groß-/Kleinschreibung egal). */
        fun parse(raw: String?): Category {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when {
                v.isEmpty() -> SPIELER
                v.startsWith("spiel") || v == "player" || v == "spieler" -> SPIELER
                v.startsWith("wappen") || v == "badge" || v == "logo" || v == "crest" -> WAPPEN
                v.startsWith("mannschaft") || v.startsWith("team") -> MANNSCHAFT
                v.startsWith("sonder") || v.startsWith("glitzer") || v.startsWith("special") ||
                    v.startsWith("glitter") || v == "foil" -> SONDER
                else -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: SPIELER
            }
        }
    }
}
