package de.dritteliga.stickertracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [StickerEntity::class, ClubEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stickerDao(): StickerDao
}
