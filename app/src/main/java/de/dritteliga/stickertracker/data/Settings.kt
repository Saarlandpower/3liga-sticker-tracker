package de.dritteliga.stickertracker.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("System"), DUNKEL("Dunkel"), HELL("Hell") }

enum class ScanMode(val label: String, val description: String) {
    MANUELL("Auslöser", "Zählt nur, wenn du auf den Auslöser oder ins Bild tippst"),
    AUTOMATISCH("Automatisch", "Zählt jede stabil erkannte Nummer selbstständig (Serienmodus)"),
}

data class AppSettings(
    val checklistName: String = "",
    val themeMode: ThemeMode = ThemeMode.DUNKEL,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val scanMode: ScanMode = ScanMode.MANUELL,
)

/** Kleine App-Einstellungen in SharedPreferences (werden per Auto Backup mitgesichert). */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        checklistName = prefs.getString(KEY_NAME, "").orEmpty(),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.DUNKEL),
        sound = prefs.getBoolean(KEY_SOUND, true),
        vibration = prefs.getBoolean(KEY_VIBRATION, true),
        scanMode = runCatching { ScanMode.valueOf(prefs.getString(KEY_SCAN_MODE, null) ?: "") }.getOrDefault(ScanMode.MANUELL),
    )

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(v) { prefs.edit().putBoolean(KEY_SEEDED, v).apply() }

    fun update(transform: (AppSettings) -> AppSettings) {
        val new = transform(_settings.value)
        prefs.edit()
            .putString(KEY_NAME, new.checklistName)
            .putString(KEY_THEME, new.themeMode.name)
            .putBoolean(KEY_SOUND, new.sound)
            .putBoolean(KEY_VIBRATION, new.vibration)
            .putString(KEY_SCAN_MODE, new.scanMode.name)
            .apply()
        _settings.value = new
    }

    private companion object {
        const val KEY_NAME = "checklist_name"
        const val KEY_THEME = "theme"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATION = "vibration"
        const val KEY_SEEDED = "seeded"
        const val KEY_SCAN_MODE = "scan_mode"
    }
}
