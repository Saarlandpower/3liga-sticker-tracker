package de.dritteliga.stickertracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.dritteliga.stickertracker.data.ColorTheme
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

/** Farben der Fortschrittsbalken; null = Standardwert der jeweiligen Stelle. */
data class ProgressColors(
    val bar: Color,
    val barComplete: Color,
    /** Spur im großen Fortschritts-Kasten */
    val cardTrack: Color?,
    /** Spur der Balken pro Verein/Kategorie */
    val listTrack: Color?,
)

val LocalProgressColors = staticCompositionLocalOf {
    ProgressColors(bar = StatusColors.Owned, barComplete = StatusColors.Duplicate, cardTrack = null, listTrack = null)
}

/** Vereinsfarben des 1. FC Saarbrücken: Blau, Schwarz, Gelb. */
object FcsColors {
    val Blue = Color(0xFF0057B8)
    val BlueLight = Color(0xFF5C9DFF)
    val BlueDeep = Color(0xFF00326B)
    val Black = Color(0xFF000000)
    val Yellow = Color(0xFFFFD400)
}

private val FcsDark = darkColorScheme(
    primary = FcsColors.BlueLight,
    onPrimary = Color(0xFF001A3D),
    primaryContainer = FcsColors.BlueDeep,
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = FcsColors.Yellow,
    onSecondary = FcsColors.Black,
    secondaryContainer = Color(0xFF4D4000),
    onSecondaryContainer = Color(0xFFFFEFA3),
    tertiary = FcsColors.Yellow,
    onTertiary = FcsColors.Black,
    background = Color(0xFF07090D),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF07090D),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1E2633),
    onSurfaceVariant = Color(0xFFB9C3D4),
    surfaceContainerLowest = Color(0xFF040507),
    surfaceContainerLow = Color(0xFF0D1118),
    surfaceContainer = Color(0xFF111722),
    surfaceContainerHigh = Color(0xFF18202D),
    surfaceContainerHighest = Color(0xFF202A39),
    outline = Color(0xFF4A5568),
)

private val FcsLight = lightColorScheme(
    primary = FcsColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E5FF),
    onPrimaryContainer = FcsColors.BlueDeep,
    secondary = Color(0xFF6B5A00),
    onSecondary = Color.White,
    secondaryContainer = FcsColors.Yellow,
    onSecondaryContainer = FcsColors.Black,
    tertiary = FcsColors.Black,
    onTertiary = FcsColors.Yellow,
    background = Color(0xFFF7F9FD),
    surface = Color(0xFFF7F9FD),
    surfaceContainer = Color(0xFFE9EEF7),
    surfaceContainerHigh = Color(0xFFE1E8F4),
)

@Composable
fun StickerTheme(mode: ThemeMode, colorTheme: ColorTheme = ColorTheme.STANDARD, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DUNKEL -> true
        ThemeMode.HELL -> false
    }
    val scheme = when (colorTheme) {
        ColorTheme.STANDARD -> if (dark) Dark else Light
        ColorTheme.FCS -> if (dark) FcsDark else FcsLight
    }
    val progress = when (colorTheme) {
        ColorTheme.STANDARD -> ProgressColors(StatusColors.Owned, StatusColors.Duplicate, null, null)
        // FCS: blaue Spur, gelber Fortschritt
        ColorTheme.FCS -> ProgressColors(
            bar = FcsColors.Yellow,
            barComplete = FcsColors.Yellow,
            cardTrack = FcsColors.Blue,
            listTrack = if (dark) FcsColors.Blue else Color(0xFFB9CFF2),
        )
    }
    CompositionLocalProvider(LocalProgressColors provides progress) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
