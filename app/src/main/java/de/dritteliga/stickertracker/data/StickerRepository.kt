package de.dritteliga.stickertracker.data

import android.content.Context
import de.dritteliga.stickertracker.BuildConfig
import de.dritteliga.stickertracker.data.db.ClubEntity
import de.dritteliga.stickertracker.data.db.StickerDao
import de.dritteliga.stickertracker.data.db.StickerEntity
import de.dritteliga.stickertracker.domain.ChecklistMerge
import de.dritteliga.stickertracker.domain.ChecklistParser
import de.dritteliga.stickertracker.domain.Club
import de.dritteliga.stickertracker.domain.ParsedChecklist
import de.dritteliga.stickertracker.domain.Sticker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: StickerDao,
    private val settings: SettingsStore,
) {
    private val seedMutex = Mutex()

    val stickers: Flow<List<Sticker>> = dao.observeStickers().map { list -> list.map { it.toDomain() } }
    val clubs: Flow<List<Club>> = dao.observeClubs().map { list -> list.map { it.toDomain() } }

    /** Lädt beim allerersten Start die Checkliste aus assets/checklist.json. */
    suspend fun ensureSeeded() = seedMutex.withLock {
        withContext(Dispatchers.IO) {
            if (settings.seeded && dao.stickerCount() > 0) return@withContext
            if (dao.stickerCount() == 0) {
                val parsed = loadBundledChecklist()
                val merge = ChecklistMerge.merge(emptyList(), emptyList(), parsed)
                dao.replaceAll(merge.clubs.map(ClubEntity::from), merge.stickers.map(StickerEntity::from))
                settings.update { it.copy(checklistName = parsed.name.orEmpty()) }
            }
            settings.seeded = true
        }
    }

    fun loadBundledChecklist(): ParsedChecklist {
        val text = context.assets.open("checklist.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return ChecklistParser.parse(text, "checklist.json", BuildConfig.PLACEHOLDER_COUNT)
    }

    suspend fun previewImport(parsed: ParsedChecklist): ChecklistMerge.Result = withContext(Dispatchers.IO) {
        ChecklistMerge.merge(
            dao.getStickers().map { it.toDomain() },
            dao.getClubs().map { it.toDomain() },
            parsed,
        )
    }

    suspend fun applyImport(result: ChecklistMerge.Result, name: String?) = withContext(Dispatchers.IO) {
        dao.replaceAll(result.clubs.map(ClubEntity::from), result.stickers.map(StickerEntity::from))
        if (name != null) settings.update { it.copy(checklistName = name) }
    }

    /** Ersetzt Bestand + Liste komplett (Backup-Import). */
    suspend fun replaceEverything(clubs: List<Club>, stickers: List<Sticker>) = withContext(Dispatchers.IO) {
        dao.replaceAll(clubs.map(ClubEntity::from), stickers.map(StickerEntity::from))
    }

    suspend fun snapshot(): Pair<List<Club>, List<Sticker>> = withContext(Dispatchers.IO) {
        dao.getClubs().map { it.toDomain() } to dao.getStickers().map { it.toDomain() }
    }

    /** @return neue Anzahl, oder null wenn die Nummer nicht in der Checkliste ist. */
    suspend fun changeCount(number: String, delta: Int): Int? = withContext(Dispatchers.IO) {
        dao.changeCount(number, delta)
    }

    suspend fun setCount(number: String, count: Int) = withContext(Dispatchers.IO) { dao.setCount(number, count) }

    suspend fun updateSticker(sticker: Sticker) = withContext(Dispatchers.IO) {
        dao.updateSticker(StickerEntity.from(sticker))
    }

    suspend fun renameClub(id: Int, name: String) = withContext(Dispatchers.IO) { dao.renameClub(id, name.trim()) }

    suspend fun assignClub(numbers: List<String>, clubId: Int?) = withContext(Dispatchers.IO) {
        numbers.chunked(500).forEach { dao.assignClub(it, clubId) }
    }

    suspend fun resetCounts() = withContext(Dispatchers.IO) { dao.resetAllCounts() }
}
