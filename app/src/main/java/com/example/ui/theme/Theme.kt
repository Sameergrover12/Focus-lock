package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.data.preferences.ThemeMode

// Default legacy fallbacks
val LightFocusColorScheme: ColorScheme = getFocusColorScheme(
    BaseThemeMode.LIGHT,
    SecondaryThemePalette.FOCUS_EMERALD
)

val DarkFocusColorScheme: ColorScheme = getFocusColorScheme(
    BaseThemeMode.AMOLED,
    SecondaryThemePalette.FOCUS_EMERALD
)

/**
 * FocusLock 2-Layer Theme Provider:
 * Layer 1: Base Mode (Light, Dark, AMOLED, or System Default)
 * Layer 2: Secondary Color Palette (12 palettes)
 */
@Composable
fun FocusLockTheme(
    baseThemeMode: BaseThemeMode? = null,
    secondaryPalette: SecondaryThemePalette = SecondaryThemePalette.FOCUS_EMERALD,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()

    val resolvedBase = (baseThemeMode ?: when (themeMode) {
        ThemeMode.LIGHT -> BaseThemeMode.LIGHT
        ThemeMode.DARK -> BaseThemeMode.DARK
        ThemeMode.SYSTEM -> BaseThemeMode.SYSTEM_DEFAULT
    }).resolve(isSystemDark)

    val colorScheme = getFocusColorScheme(resolvedBase, secondaryPalette)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
