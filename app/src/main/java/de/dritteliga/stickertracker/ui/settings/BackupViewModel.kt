package de.dritteliga.stickertracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.SettingsStore
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.BackupCodec
import de.dritteliga.stickertracker.domain.BackupFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PendingRestore(
    val file: BackupFile,
    val exportedAt: String,
    val stickerCount: Int,
    val ownedCount: Int,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StickerRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _pending = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pending.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun suggestedFileName(): String =
        "sticker-tracker-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date()) + ".json"

    fun export(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val (clubs, stickers) = repository.snapshot()
                val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
                val text = BackupCodec.encode(stamp, settings.settings.value.checklistName, clubs, stickers)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Datei konnte nicht geschrieben werden.")
                }
                stickers.size
            }.onSuccess { _message.value = "Bestand exportiert ($it Sticker)." }
                .onFailure { _message.value = "Export fehlgeschlagen: ${it.message}" }
        }
    }

    fun loadForRestore(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Datei konnte nicht gelesen werden.")
                }
                val file = BackupCodec.decode(text)
                PendingRestore(file, file.exportedAt, file.stickers.size, file.stickers.count { it.count > 0 })
            }.onSuccess { _pending.value = it }
                .onFailure { _message.value = "Import fehlgeschlagen: ${it.message}" }
        }
    }

    fun cancelRestore() { _pending.value = null }

    fun confirmRestore() {
        val p = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val (clubs, stickers) = BackupCodec.toDomain(p.file)
            repository.replaceEverything(clubs, stickers)
            settings.update { it.copy(checklistName = p.file.checklistName) }
            _message.value = "Bestand wiederhergestellt (${stickers.size} Sticker)."
        }
    }
}
