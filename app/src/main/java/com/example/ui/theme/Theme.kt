package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.preferences.ThemeMode

val LightFocusColorScheme: ColorScheme = lightColorScheme(
    primary = SagePrimaryLight,
    onPrimary = SageOnPrimaryLight,
    primaryContainer = SagePrimaryContainerLight,
    onPrimaryContainer = SageOnPrimaryContainerLight,
    secondary = SageSecondaryLight,
    onSecondary = SageOnSecondaryLight,
    background = SageBackgroundLight,
    onBackground = SageOnSurfaceLight,
    surface = SageSurfaceLight,
    onSurface = SageOnSurfaceLight,
    surfaceVariant = SageSurfaceVariantLight,
    onSurfaceVariant = SageOnSurfaceVariantLight,
    outline = SageOutlineLight
)

val DarkFocusColorScheme: ColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = AmoledBlack,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = EmeraldLight,
    onSecondary = AmoledBlack,
    secondaryContainer = Color(0xFF0A2E22),
    onSecondaryContainer = EmeraldLight,
    background = AmoledBlack, // Pure black #000000
    onBackground = TextHighContrast,
    surface = AmoledSurfacePitch, // Deep pitch-slate #0D0E11
    onSurface = TextHighContrast,
    surfaceVariant = AmoledSurfaceSlate, // #141519
    onSurfaceVariant = TextLowContrast,
    outline = AmoledCardBorder, // #22262F
    outlineVariant = Color(0xFF1A1D24),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF450A0A),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFCA5A5)
)

@Composable
fun FocusLockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkFocusColorScheme else LightFocusColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
