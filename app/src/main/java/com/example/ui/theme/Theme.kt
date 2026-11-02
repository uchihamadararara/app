package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ClassicPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = ClassicPrimaryLightBg,
    onPrimaryContainer = ClassicPrimaryDarkBlue,
    secondary = ClassicAccentIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = ClassicSuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = ClassicSuccessLightBg,
    error = ClassicDangerRed,
    onError = Color.White,
    errorContainer = ClassicDangerLightBg,
    onErrorContainer = ClassicDangerRed,
    background = NativeBackgroundLight,
    onBackground = NativeTextPrimary,
    surface = NativeSurfaceWhite,
    onSurface = NativeTextPrimary,
    surfaceVariant = NativeSurfaceSubtle,
    onSurfaceVariant = NativeTextSecondary,
    outline = NativeBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF6366F1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFF22C55E),
    error = Color(0xFFEF4444),
    background = NativeBackgroundDark,
    onBackground = NativeTextPrimaryDark,
    surface = NativeSurfaceDark,
    onSurface = NativeTextPrimaryDark,
    surfaceVariant = NativeSurfaceVariantDark,
    onSurfaceVariant = NativeTextSecondaryDark,
    outline = NativeBorderDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
