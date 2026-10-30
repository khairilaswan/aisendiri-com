package com.aisendiri.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aisendiri.shared.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF341100),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFFDDE5E3),
    onSurfaceVariant = Color(0xFF3F4947),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFFFB68E),
    onSecondary = Color(0xFF552000),
    secondaryContainer = Color(0xFF793000),
    onSecondaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF101413),
    surface = Color(0xFF101413),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
)

// Slightly larger than Material defaults: easier to read for everyone.
private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    )
}

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
        content = content,
    )
}
