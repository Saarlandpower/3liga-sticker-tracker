package de.dritteliga.stickertracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.dritteliga.stickertracker.data.ThemeMode

object StatusColors {
    val Missing = Color(0xFF5F6368)
    val MissingDark = Color(0xFF3A3D42)
    val Owned = Color(0xFF2E7D32)
    val Duplicate = Color(0xFFEF6C00)
    val OnStatus = Color.White
}

private val Dark = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF00330A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3E2400),
    secondaryContainer = Color(0xFF5D3A00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF121417),
    surface = Color(0xFF121417),
    surfaceContainer = Color(0xFF1C1F23),
    surfaceContainerHigh = Color(0xFF24282D),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0A3D0E),
    secondary = Color(0xFFEF6C00),
    secondaryContainer = Color(0xFFFFE0B2),
)

@Composable
fun StickerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DUNKEL -> true
        ThemeMode.HELL -> false
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
