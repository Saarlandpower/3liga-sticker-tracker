package de.dritteliga.stickertracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.OcrBox
import de.dritteliga.stickertracker.domain.OcrNumberParser
import de.dritteliga.stickertracker.domain.OcrToken
import de.dritteliga.stickertracker.domain.ScanGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class ScanEntry(
    val id: Long,
    val number: String,
    val newCount: Int,
    val manual: Boolean,
) {
    val isNew: Boolean get() = newCount == 1
    val label: String get() = if (isNew) "#$number – NEU" else "#$number – doppelt (jetzt ${newCount}x)"
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val feedback: ScanFeedback,
) : ViewModel() {
    private val gate = ScanGate(requiredHits = 2, stabilityWindowMs = 1000, cooldownMs = 2000)
    private val ids = AtomicLong()

    val knownNumbers: StateFlow<Set<String>> = repository.stickers
        .map { list -> list.mapTo(HashSet()) { it.number } as Set<String> }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _session = MutableStateFlow<List<ScanEntry>>(emptyList())
    val session: StateFlow<List<ScanEntry>> = _session.asStateFlow()

    private val _overlay = MutableStateFlow<ScanEntry?>(null)
    val overlay: StateFlow<ScanEntry?> = _overlay.asStateFlow()
    private var overlayJob: Job? = null

    /** Zuletzt im Rahmen gesehene (noch nicht unbedingt gezählte) Nummer – als Live-Hinweis. */
    private val _live = MutableStateFlow<String?>(null)
    val live: StateFlow<String?> = _live.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    @Volatile var frame: OcrBox? = null

    fun togglePause() {
        _paused.update { !it }
        gate.reset()
    }

    /** Wird vom Kamera-Analyzer (Hintergrund-Thread) für jedes ausgewertete Bild aufgerufen. */
    fun onTextFrame(tokens: List<OcrToken>, nowMs: Long) {
        if (_paused.value) return
        val match = OcrNumberParser.pickBest(tokens, frame, knownNumbers.value)
        _live.value = match?.number
        val toCount = gate.onFrame(match?.number, nowMs) ?: return
        viewModelScope.launch { count(toCount, manual = false) }
    }

    fun addManual(numbers: List<String>) {
        viewModelScope.launch { numbers.forEach { count(it, manual = true) } }
    }

    private suspend fun count(number: String, manual: Boolean) {
        val newCount = repository.changeCount(number, +1) ?: return
        val entry = ScanEntry(ids.incrementAndGet(), number, newCount, manual)
        _session.update { listOf(entry) + it }
        feedback.play(entry.isNew)
        showOverlay(entry)
    }

    private fun showOverlay(entry: ScanEntry) {
        _overlay.value = entry
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(1800)
            _overlay.value = null
        }
    }

    fun undo(entry: ScanEntry) {
        viewModelScope.launch {
            repository.changeCount(entry.number, -1)
            _session.update { list -> list.filterNot { it.id == entry.id } }
            if (_overlay.value?.id == entry.id) _overlay.value = null
        }
    }

    fun clearSession() {
        _session.value = emptyList()
        gate.reset()
    }

    override fun onCleared() {
        feedback.release()
    }
}
