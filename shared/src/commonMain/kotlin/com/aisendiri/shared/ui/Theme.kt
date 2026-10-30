package com.aisendiri.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aisendiri.shared.settings.ThemeMode

// Brand: deep teal with a warm sunset accent.
private val Teal900 = Color(0xFF00423B)
private val Teal700 = Color(0xFF00695C)
private val Teal500 = Color(0xFF0E9F8C)
private val Teal100 = Color(0xFFCDEFEA)
private val Sunset = Color(0xFFFF7A59)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary = Color(0xFFE2603F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D9),
    onSecondaryContainer = Color(0xFF5A1A08),
    tertiary = Color(0xFF5B5BD6),
    tertiaryContainer = Color(0xFFE4E4FF),
    onTertiaryContainer = Color(0xFF1E1E6E),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF151D1C),
    surface = Color.White,
    onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFEAF0EE),
    onSurfaceVariant = Color(0xFF52605D),
    surfaceContainer = Color(0xFFEFF4F2),
    surfaceContainerHigh = Color(0xFFE6EDEB),
    outline = Color(0xFFC3CECB),
    outlineVariant = Color(0xFFDCE4E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD6C5),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0B4F46),
    onPrimaryContainer = Teal100,
    secondary = Color(0xFFFFB199),
    onSecondary = Color(0xFF5A1A08),
    secondaryContainer = Color(0xFF6E2A15),
    onSecondaryContainer = Color(0xFFFFE3D9),
    tertiary = Color(0xFFBDBDFF),
    tertiaryContainer = Color(0xFF34348A),
    onTertiaryContainer = Color(0xFFE4E4FF),
    background = Color(0xFF0D1312),
    onBackground = Color(0xFFE1E8E6),
    surface = Color(0xFF151C1B),
    onSurface = Color(0xFFE1E8E6),
    surfaceVariant = Color(0xFF1E2725),
    onSurfaceVariant = Color(0xFFAAB8B4),
    surfaceContainer = Color(0xFF19211F),
    surfaceContainerHigh = Color(0xFF222B29),
    outline = Color(0xFF3B4744),
    outlineVariant = Color(0xFF2B3533),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// Slightly larger than Material defaults: easier to read for everyone.
private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    )
}

/** The signature teal-to-green gradient used on hero areas and the main action. */
val brandGradient: Brush
    get() = Brush.linearGradient(listOf(Teal700, Teal500))

/** A warm gradient for highlights such as the AI avatar. */
val accentGradient: Brush
    get() = Brush.linearGradient(listOf(Teal500, Sunset))

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
