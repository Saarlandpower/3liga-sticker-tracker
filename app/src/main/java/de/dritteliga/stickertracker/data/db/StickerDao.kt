package de.dritteliga.stickertracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM stickers ORDER BY position")
    fun observeStickers(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers ORDER BY position")
    suspend fun getStickers(): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE number = :number")
    suspend fun getSticker(number: String): StickerEntity?

    @Query("SELECT COUNT(*) FROM stickers")
    suspend fun stickerCount(): Int

    @Query("UPDATE stickers SET count = MAX(0, count + :delta) WHERE number = :number")
    suspend fun addToCount(number: String, delta: Int): Int

    @Query("UPDATE stickers SET count = MAX(0, :count) WHERE number = :number")
    suspend fun setCount(number: String, count: Int)

    @Query("UPDATE stickers SET count = 0")
    suspend fun resetAllCounts()

    @Update
    suspend fun updateSticker(sticker: StickerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Query("DELETE FROM stickers")
    suspend fun deleteAllStickers()

    @Query("SELECT * FROM clubs ORDER BY position")
    fun observeClubs(): Flow<List<ClubEntity>>

    @Query("SELECT * FROM clubs ORDER BY position")
    suspend fun getClubs(): List<ClubEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClubs(clubs: List<ClubEntity>)

    @Query("UPDATE clubs SET name = :name WHERE id = :id")
    suspend fun renameClub(id: Int, name: String)

    @Query("UPDATE stickers SET clubId = :clubId WHERE number IN (:numbers)")
    suspend fun assignClub(numbers: List<String>, clubId: Int?)

    @Query("DELETE FROM clubs")
    suspend fun deleteAllClubs()

    @Transaction
    suspend fun replaceAll(clubs: List<ClubEntity>, stickers: List<StickerEntity>) {
        deleteAllStickers()
        deleteAllClubs()
        insertClubs(clubs)
        insertStickers(stickers)
    }

    /** Erhöht/verringert atomar und liefert die neue Anzahl (oder null, wenn die Nummer unbekannt ist). */
    @Transaction
    suspend fun changeCount(number: String, delta: Int): Int? {
        if (addToCount(number, delta) == 0) return null
        return getSticker(number)?.count
    }
}
