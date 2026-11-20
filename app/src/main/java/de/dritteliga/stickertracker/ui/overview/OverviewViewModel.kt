package de.dritteliga.stickertracker.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.SettingsStore
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.Category
import de.dritteliga.stickertracker.domain.CountLogic
import de.dritteliga.stickertracker.ui.components.countSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupProgress(val id: Int?, val label: String, val summary: CountLogic.Summary)

data class OverviewState(
    val loaded: Boolean = false,
    val checklistName: String = "",
    val summary: CountLogic.Summary = CountLogic.Summary(0, 0, 0, 0),
    val clubs: List<GroupProgress> = emptyList(),
    val categories: List<GroupProgress> = emptyList(),
    val knownNumbers: Set<String> = emptySet(),
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repository: StickerRepository,
    settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<OverviewState> = combine(repository.collection, settings.settings) { data, s ->
        val byClub = data.stickers.groupBy { it.clubId }
        val clubRows = data.clubs.mapNotNull { c ->
            byClub[c.id]?.let { list -> GroupProgress(c.id, c.name, CountLogic.summarize(list.map { it.count })) }
        } + listOfNotNull(
            byClub[null]?.takeIf { byClub.size > 1 }?.let { list ->
                GroupProgress(null, "Ohne Verein", CountLogic.summarize(list.map { it.count }))
            }
        )
        val byCat = data.stickers.groupBy { it.category }
        OverviewState(
            loaded = true,
            checklistName = s.checklistName,
            summary = CountLogic.summarize(data.stickers.map { it.count }),
            clubs = clubRows,
            categories = Category.entries.mapNotNull { cat ->
                byCat[cat]?.let { GroupProgress(cat.ordinal, cat.label, CountLogic.summarize(it.map { st -> st.count })) }
            },
            knownNumbers = data.numbers,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverviewState())

    val message = MutableStateFlow<String?>(null)

    fun addNumbers(numbers: List<String>) {
        viewModelScope.launch {
            message.value = countSummary(repository.addNumbers(numbers))
        }
    }
}
