package de.dritteliga.stickertracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.ScanMode
import de.dritteliga.stickertracker.data.SettingsStore
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.OcrBox
import de.dritteliga.stickertracker.domain.OcrNumberParser
import de.dritteliga.stickertracker.domain.OcrToken
import de.dritteliga.stickertracker.domain.ScanGate
import de.dritteliga.stickertracker.domain.ScanVote
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

/** Hinweis nach einem Auslösen, das nicht gezählt hat. */
data class ScanHint(val id: Long, val text: String)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: StickerRepository,
    private val feedback: ScanFeedback,
    private val settings: SettingsStore,
) : ViewModel() {
    // Automatik: deutlich vorsichtiger als zuvor (3 Treffer, 3 s Sperre)
    private val gate = ScanGate(requiredHits = 3, stabilityWindowMs = 1500, cooldownMs = 3000)
    private val ids = AtomicLong()
    private val lock = Any()

    val mode: StateFlow<ScanMode> = settings.settings.map { it.scanMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value.scanMode)

    val knownNumbers: StateFlow<Set<String>> = repository.stickers
        .map { list -> list.mapTo(HashSet()) { it.number } as Set<String> }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _session = MutableStateFlow<List<ScanEntry>>(emptyList())
    val session: StateFlow<List<ScanEntry>> = _session.asStateFlow()

    private val _overlay = MutableStateFlow<ScanEntry?>(null)
    val overlay: StateFlow<ScanEntry?> = _overlay.asStateFlow()
    private var overlayJob: Job? = null

    private val _hint = MutableStateFlow<ScanHint?>(null)
    val hint: StateFlow<ScanHint?> = _hint.asStateFlow()
    private var hintJob: Job? = null

    /** Stabil erkannte Nummer im Rahmen (Mehrheit der letzten Bilder) – Live-Vorschau. */
    private val _live = MutableStateFlow<String?>(null)
    val live: StateFlow<String?> = _live.asStateFlow()
    private val recent = ArrayDeque<String?>()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** true, während nach dem Auslösen Bilder gesammelt werden. */
    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()
    private var captureSamples: MutableList<String?>? = null
    private var captureMinEnd = 0L
    private var captureJob: Job? = null

    @Volatile var frame: OcrBox? = null

    fun setMode(mode: ScanMode) {
        settings.update { it.copy(scanMode = mode) }
        gate.reset()
    }

    fun togglePause() {
        _paused.update { !it }
        gate.reset()
    }

    /** Wird vom Kamera-Analyzer (Hintergrund-Thread) für jedes ausgewertete Bild aufgerufen. */
    fun onTextFrame(tokens: List<OcrToken>, nowMs: Long) {
        if (_paused.value) return
        val number = OcrNumberParser.pickBest(tokens, frame, knownNumbers.value)?.number

        var finishCapture = false
        synchronized(lock) {
            recent.addLast(number)
            while (recent.size > LIVE_FRAMES) recent.removeFirst()
            _live.value = ScanVote.stable(recent.toList())

            captureSamples?.let { samples ->
                samples += number
                if (nowMs >= captureMinEnd && samples.size >= CAPTURE_MIN_FRAMES) finishCapture = true
            }
        }
        if (finishCapture) finishCapture()

        if (mode.value == ScanMode.AUTOMATISCH) {
            val toCount = gate.onFrame(number, nowMs) ?: return
            viewModelScope.launch { count(toCount, manual = false) }
        }
    }

    /** Manuelles Auslösen: sammelt kurz Bilder und zählt nur bei eindeutiger Mehrheit. */
    fun trigger() {
        if (_paused.value) return
        synchronized(lock) {
            if (captureSamples != null) return // läuft bereits – Doppeltippen ignorieren
            captureSamples = mutableListOf()
            captureMinEnd = System.currentTimeMillis() + CAPTURE_MIN_MS
        }
        _capturing.value = true
        captureJob = viewModelScope.launch {
            delay(CAPTURE_MAX_MS) // Fallback, falls zu wenige Bilder ankommen
            finishCapture()
        }
    }

    private fun finishCapture() {
        val samples = synchronized(lock) {
            captureSamples.also { captureSamples = null }
        } ?: return
        captureJob?.cancel()
        _capturing.value = false
        when (val r = ScanVote.decide(samples)) {
            is ScanVote.Result.Sure -> viewModelScope.launch { count(r.number, manual = false) }
            is ScanVote.Result.Unsure -> showHint(
                if (r.best != null) "Nicht eindeutig (#${r.best}?) – Sticker ruhig halten und nochmal auslösen"
                else "Nicht eindeutig – nochmal auslösen"
            )
            ScanVote.Result.Nothing -> showHint("Keine Nummer erkannt – Rückseite mit Nummer in den Rahmen halten")
        }
    }

    fun addManual(numbers: List<String>) {
        viewModelScope.launch { numbers.forEach { count(it, manual = true) } }
    }

    private suspend fun count(number: String, manual: Boolean) {
        val newCount = repository.changeCount(number, +1) ?: return
        val entry = ScanEntry(ids.incrementAndGet(), number, newCount, manual)
        _session.update { listOf(entry) + it }
        feedback.play(entry.isNew)
        _hint.value = null
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

    private fun showHint(text: String) {
        val h = ScanHint(ids.incrementAndGet(), text)
        _hint.value = h
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(2500)
            if (_hint.value?.id == h.id) _hint.value = null
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

    private companion object {
        const val LIVE_FRAMES = 6
        const val CAPTURE_MIN_MS = 700L
        const val CAPTURE_MIN_FRAMES = 3
        const val CAPTURE_MAX_MS = 2000L
    }
}
