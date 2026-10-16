package com.ikhanai.timesheet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ikhanai.timesheet.data.ThemeMode

/**
 * Series colours. NTIME (blue) and P6 (orange) stay distinguishable for common colour-vision
 * deficiencies, and every use is paired with a text label.
 */
@Immutable
data class SeriesColors(
    val ntime: Color,
    val p6: Color,
    val overhead: Color,
    val overtime: Color,
    val track: Color,
    val missing: Color,
    val leave: Color,
    val complete: Color,
    val projectPalette: List<Color>,
)

private val LightSeries = SeriesColors(
    ntime = Color(0xFF1F6FEB),
    p6 = Color(0xFFD9730D),
    overhead = Color(0xFF8C959F),
    overtime = Color(0xFFCF222E),
    track = Color(0xFFE6E8EB),
    missing = Color(0xFFCF222E),
    leave = Color(0xFF8250DF),
    complete = Color(0xFF1A7F37),
    projectPalette = listOf(
        Color(0xFF1F6FEB), Color(0xFFD9730D), Color(0xFF1A7F37), Color(0xFF8250DF),
        Color(0xFFBF3989), Color(0xFF0E8A8A), Color(0xFF9A6700), Color(0xFF57606A),
    ),
)

private val DarkSeries = SeriesColors(
    ntime = Color(0xFF58A6FF),
    p6 = Color(0xFFF0A045),
    overhead = Color(0xFF8B949E),
    overtime = Color(0xFFFF7B72),
    track = Color(0xFF30363D),
    missing = Color(0xFFFF7B72),
    leave = Color(0xFFBC8CFF),
    complete = Color(0xFF3FB950),
    projectPalette = listOf(
        Color(0xFF58A6FF), Color(0xFFF0A045), Color(0xFF3FB950), Color(0xFFBC8CFF),
        Color(0xFFF778BA), Color(0xFF39C5CF), Color(0xFFD29922), Color(0xFF8B949E),
    ),
)

val LocalSeriesColors = staticCompositionLocalOf { LightSeries }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F4E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF555F71),
    secondaryContainer = Color(0xFFD9E3F8),
    background = Color(0xFFF9F9FC),
    surface = Color(0xFFF9F9FC),
    error = Color(0xFFBA1A1A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF1F4E8C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    secondaryContainer = Color(0xFF3E4759),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TimesheetTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalSeriesColors provides if (dark) DarkSeries else LightSeries) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
