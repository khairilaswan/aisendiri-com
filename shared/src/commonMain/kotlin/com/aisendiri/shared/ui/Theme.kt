package com.aisendiri.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aisendiri.shared.settings.ThemeMode

/** Neo-brutalist palette from the Jalur Gemilang: navy, red, yellow and white, with black ink. */
object Malaysia {
    val Navy = Color(0xFF0B1F6B)
    val Red = Color(0xFFD7141A)
    val Yellow = Color(0xFFFFC72C)
    val Cream = Color(0xFFFFF6E0)
    val Ink = Color(0xFF111111)
    val SkyTint = Color(0xFFCFDAFF)
    val RedTint = Color(0xFFFFD4CF)
    val YellowTint = Color(0xFFFFE9A8)
    val Green = Color(0xFF1E9E6A)
}

private val LightColors = lightColorScheme(
    primary = Malaysia.Navy,
    onPrimary = Color.White,
    primaryContainer = Malaysia.SkyTint,
    onPrimaryContainer = Malaysia.Ink,
    secondary = Malaysia.Red,
    onSecondary = Color.White,
    secondaryContainer = Malaysia.RedTint,
    onSecondaryContainer = Malaysia.Ink,
    tertiary = Malaysia.Yellow,
    onTertiary = Malaysia.Ink,
    tertiaryContainer = Malaysia.YellowTint,
    onTertiaryContainer = Malaysia.Ink,
    background = Malaysia.Cream,
    onBackground = Malaysia.Ink,
    surface = Color.White,
    onSurface = Malaysia.Ink,
    surfaceVariant = Color(0xFFF3EAD3),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Malaysia.Ink,
    outlineVariant = Malaysia.Ink,
    error = Malaysia.Red,
)

private val DarkColors = darkColorScheme(
    primary = Malaysia.Yellow,
    onPrimary = Malaysia.Ink,
    primaryContainer = Color(0xFF26346E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF5A52),
    onSecondary = Malaysia.Ink,
    secondaryContainer = Color(0xFF5C1A1A),
    onSecondaryContainer = Color.White,
    tertiary = Malaysia.Yellow,
    onTertiary = Malaysia.Ink,
    tertiaryContainer = Color(0xFF5A4A12),
    onTertiaryContainer = Color.White,
    background = Color(0xFF0A1033),
    onBackground = Color(0xFFF4F1E8),
    surface = Color(0xFF151C4A),
    onSurface = Color(0xFFF4F1E8),
    surfaceVariant = Color(0xFF1F275A),
    onSurfaceVariant = Color(0xFFC9CCE0),
    outline = Color(0xFFF4F1E8),
    outlineVariant = Color(0xFFF4F1E8),
    error = Color(0xFFFF5A52),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// Chunky, heavy headings are part of the neo-brutalist look; body text stays large and readable.
private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 33.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Black),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Bold),
    )
}

/** Outline and hard-shadow colour: black on light, off-white on dark. */
val ink: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

@Composable
fun AiSendiriTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
