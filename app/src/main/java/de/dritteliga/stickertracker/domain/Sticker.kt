package de.dritteliga.stickertracker.domain

data class Club(
    val id: Int,
    val name: String,
    val position: Int,
)

data class Sticker(
    val number: String,
    val position: Int,
    val name: String,
    val clubId: Int?,
    val category: Category,
    val count: Int,
) {
    val status: StickerStatus get() = CountLogic.status(count)
    val duplicates: Int get() = CountLogic.duplicates(count)
}

enum class StickerStatus { FEHLT, VORHANDEN, DOPPELT }
