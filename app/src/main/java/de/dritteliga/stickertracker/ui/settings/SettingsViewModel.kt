package de.dritteliga.stickertracker.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.AppSettings
import de.dritteliga.stickertracker.data.SettingsStore
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.ChecklistMerge
import de.dritteliga.stickertracker.domain.ChecklistParser
import de.dritteliga.stickertracker.domain.ParsedChecklist
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Vorschau eines Checklisten-Imports, die der Nutzer bestätigen muss. */
data class ImportPreview(
    val title: String,
    val parsed: ParsedChecklist,
    val result: ChecklistMerge.Result,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StickerRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val stickerCount: StateFlow<Int> = repository.stickers.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun loadChecklistFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) { readText(uri) }
                val parsed = withContext(Dispatchers.Default) { ChecklistParser.parse(text, name) }
                showPreview(parsed.name ?: name?.substringBeforeLast('.') ?: "Importierte Checkliste", parsed)
            } catch (e: Exception) {
                _message.value = "Import fehlgeschlagen: ${e.message}"
            }
        }
    }

    fun loadBundled() {
        viewModelScope.launch {
            runCatching { repository.loadBundledChecklist() }
                .onSuccess { showPreview(it.name ?: "Mitgelieferte Checkliste", it) }
                .onFailure { _message.value = "Fehler: ${it.message}" }
        }
    }

    fun generatePlaceholders(count: Int) {
        if (count !in 1..5000) { _message.value = "Bitte eine Anzahl zwischen 1 und 5000 angeben."; return }
        val parsed = ParsedChecklist("Platzhalter 1–$count", emptyList(), ChecklistParser.placeholders(count))
        viewModelScope.launch { showPreview(parsed.name!!, parsed) }
    }

    private suspend fun showPreview(title: String, parsed: ParsedChecklist) {
        _preview.value = ImportPreview(title, parsed, repository.previewImport(parsed))
    }

    fun cancelImport() { _preview.value = null }

    fun confirmImport() {
        val p = _preview.value ?: return
        _preview.value = null
        viewModelScope.launch {
            repository.applyImport(p.result, p.title)
            _message.value = "Checkliste übernommen: ${p.result.stickers.size} Sticker, ${p.result.keptCounts} Bestände erhalten."
        }
    }

    fun resetCounts() {
        viewModelScope.launch {
            repository.resetCounts()
            _message.value = "Alle Anzahlen wurden auf 0 gesetzt."
        }
    }

    private fun readText(uri: Uri): Pair<String?, String> {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Datei konnte nicht gelesen werden.")
        return name to text
    }
}
