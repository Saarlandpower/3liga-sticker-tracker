package de.dritteliga.stickertracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.dritteliga.stickertracker.domain.Category
import de.dritteliga.stickertracker.domain.Club
import de.dritteliga.stickertracker.domain.Sticker

@Entity(tableName = "stickers", indices = [Index("position"), Index("clubId")])
data class StickerEntity(
    @PrimaryKey val number: String,
    val position: Int,
    val name: String,
    val clubId: Int?,
    val category: String,
    val count: Int,
) {
    fun toDomain() = Sticker(number, position, name, clubId, runCatching { Category.valueOf(category) }.getOrDefault(Category.SPIELER), count)

    companion object {
        fun from(s: Sticker) = StickerEntity(s.number, s.position, s.name, s.clubId, s.category.name, s.count)
    }
}

@Entity(tableName = "clubs")
data class ClubEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val position: Int,
) {
    fun toDomain() = Club(id, name, position)

    companion object {
        fun from(c: Club) = ClubEntity(c.id, c.name, c.position)
    }
}
