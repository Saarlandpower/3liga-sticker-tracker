package de.dritteliga.stickertracker.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.CollectionData
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.Category
import de.dritteliga.stickertracker.domain.CountLogic
import de.dritteliga.stickertracker.domain.Sticker
import de.dritteliga.stickertracker.domain.StickerStatus
import de.dritteliga.stickertracker.ui.components.countSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tabs im Album: Alle | Fehlend | Doppelte | nach Verein | Kategorie */
enum class AlbumFilter(val label: String) {
    ALLE("Alle"), FEHLEND("Fehlend"), DOPPELT("Doppelte"), VEREIN("Nach Verein"), KATEGORIE("Kategorie")
}

/** Spezieller Vereinsfilter für Sticker ohne Verein. */
const val CLUB_NONE = -2

data class AlbumQuery(
    val filter: AlbumFilter = AlbumFilter.ALLE,
    val search: String = "",
    val clubId: Int? = null,
    val category: Category? = null,
)

data class AlbumSection(val key: String, val title: String?, val subtitle: String?, val stickers: List<Sticker>)

data class AlbumState(
    val query: AlbumQuery = AlbumQuery(),
    val sections: List<AlbumSection> = emptyList(),
    val resultCount: Int = 0,
    val data: CollectionData = CollectionData(emptyList(), emptyList()),
)

data class UndoEvent(val text: String, val number: String, val delta: Int)

@HiltViewModel
class AlbumViewModel @Inject constructor(private val repository: StickerRepository) : ViewModel() {
    private val query = MutableStateFlow(AlbumQuery())
    private var initialized = false

    val state: StateFlow<AlbumState> = combine(repository.collection, query) { data, q -> build(data, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumState())

    private val _undo = MutableStateFlow<UndoEvent?>(null)
    val undo: StateFlow<UndoEvent?> = _undo.asStateFlow()
    val message = MutableStateFlow<String?>(null)

    fun init(filter: String?, clubId: Int?) {
        if (initialized) return
        initialized = true
        val f = AlbumFilter.entries.firstOrNull { it.name == filter } ?: AlbumFilter.ALLE
        query.value = AlbumQuery(filter = f, clubId = clubId)
    }

    fun setFilter(f: AlbumFilter) = query.update { it.copy(filter = f) }
    fun setSearch(s: String) = query.update { it.copy(search = s) }
    fun setClub(id: Int?) = query.update { it.copy(clubId = id) }
    fun setCategory(c: Category?) = query.update { it.copy(category = c) }

    fun tap(sticker: Sticker) {
        viewModelScope.launch {
            val newCount = repository.changeCount(sticker.number, +1) ?: return@launch
            val text = if (newCount == 1) "#${sticker.number} – NEU" else "#${sticker.number} – doppelt (jetzt ${newCount}x)"
            _undo.value = UndoEvent(text, sticker.number, +1)
        }
    }

    fun change(sticker: Sticker, delta: Int) {
        viewModelScope.launch { repository.changeCount(sticker.number, delta) }
    }

    fun undo(event: UndoEvent) {
        viewModelScope.launch { repository.changeCount(event.number, -event.delta) }
    }

    fun consumeUndo() { _undo.value = null }

    fun save(sticker: Sticker) {
        viewModelScope.launch { repository.updateSticker(sticker) }
    }

    fun addNumbers(numbers: List<String>) {
        viewModelScope.launch { message.value = countSummary(repository.addNumbers(numbers)) }
    }

    private fun build(data: CollectionData, q: AlbumQuery): AlbumState {
        val search = q.search.trim().lowercase()
        val filtered = data.stickers.filter { s ->
            (q.clubId == null || (q.clubId == CLUB_NONE && s.clubId == null) || s.clubId == q.clubId) &&
                (q.category == null || s.category == q.category) &&
                when (q.filter) {
                    AlbumFilter.FEHLEND -> s.status == StickerStatus.FEHLT
                    AlbumFilter.DOPPELT -> s.status == StickerStatus.DOPPELT
                    else -> true
                } &&
                (search.isEmpty() || matches(s, search, data))
        }
        val sections = when (q.filter) {
            AlbumFilter.VEREIN -> {
                val byClub = filtered.groupBy { it.clubId }
                data.clubs.mapNotNull { c -> byClub[c.id]?.let { section("club-${c.id}", c.name, it) } } +
                    listOfNotNull(byClub[null]?.let { section("club-none", "Ohne Verein", it) })
            }
            AlbumFilter.KATEGORIE -> {
                val byCat = filtered.groupBy { it.category }
                Category.entries.mapNotNull { c -> byCat[c]?.let { section("cat-${c.name}", c.label, it) } }
            }
            else -> listOf(AlbumSection("all", null, null, filtered))
        }
        return AlbumState(q, sections, filtered.size, data)
    }

    private fun section(key: String, title: String, list: List<Sticker>): AlbumSection {
        val s = CountLogic.summarize(list.map { it.count })
        return AlbumSection(key, title, "${s.owned}/${s.total}", list)
    }

    private fun matches(s: Sticker, search: String, data: CollectionData): Boolean {
        val normalizedNumber = search.removePrefix("#").uppercase()
        return s.number == normalizedNumber ||
            s.number.startsWith(normalizedNumber) ||
            s.name.lowercase().contains(search) ||
            (s.clubId?.let { data.clubById[it]?.name?.lowercase()?.contains(search) } == true)
    }
}
