package com.playground.siply.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Clay500,
    onPrimary = Mist100,
    primaryContainer = Sand100,
    onPrimaryContainer = Clay700,
    secondary = Moss500,
    onSecondary = Mist100,
    secondaryContainer = Forest100,
    onSecondaryContainer = Color(0xFF20311A),
    tertiary = NightBlue,
    onTertiary = Mist100,
    background = Cream50,
    onBackground = Ink900,
    surface = Color(0xFFFFFBF6),
    onSurface = Ink900,
    surfaceContainer = Color(0xFFF3E9DB),
    surfaceContainerHigh = Color(0xFFEBDDCB),
    onSurfaceVariant = Color(0xFF61554B),
    outline = Mist300,
    outlineVariant = Color(0xFFE5D9CA),
)

private val DarkColors = darkColorScheme(
    primary = Amber300,
    onPrimary = Ink900,
    primaryContainer = Color(0xFF251B0C),
    onPrimaryContainer = Color(0xFFE2BC68),
    secondary = Color(0xFFA7C88A),
    onSecondary = Color(0xFF0F180A),
    secondaryContainer = Color(0xFF14200E),
    onSecondaryContainer = Color(0xFFC4D9B4),
    tertiary = Color(0xFF9AC3FF),
    onTertiary = Color(0xFF11253C),
    // OLED dark theme: pure black backgrounds keep the display pixels off.
    background = Color.Black,
    onBackground = Color(0xFFD4D0C8),
    surface = Color(0xFF070707),
    onSurface = Color(0xFFD4D0C8),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFA8A39B),
    outline = Color(0xFF484542),
    outlineVariant = Color(0xFF292826),
)

@Composable
fun BrziKonobarTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BrziTypography,
        content = content,
    )
}

