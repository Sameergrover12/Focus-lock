package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Layer 1: 3 Primary Base Modes
 * - LIGHT: Soft daylight surfaces with dark text
 * - DARK: Normal Dark charcoal/slate surfaces (#14171A range)
 * - AMOLED: True pitch-black (#000000) for OLED with dark tinted surfaces (#0D0F11 range)
 * - SYSTEM_DEFAULT: Follows the device system setting on first launch or by default
 */
enum class BaseThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark (System Default)"),
    AMOLED("AMOLED Black"),
    SYSTEM_DEFAULT("System Default");

    fun resolve(isSystemInDark: Boolean): BaseThemeMode = when (this) {
        SYSTEM_DEFAULT -> if (isSystemInDark) DARK else LIGHT
        else -> this
    }
}

/**
 * Layer 2: 12 Harmonious Secondary Color Palettes
 */
enum class SecondaryThemePalette(
    val displayName: String,
    val subtitle: String
) {
    FOCUS_EMERALD("Focus Emerald", "Default Emerald Focus"),
    ROSE_QUARTZ("Rose Quartz & Mist", "Soft Dusty Rose"),
    SAGE_MATCHA("Sage & Matcha", "Calming Earthy Matcha"),
    WARM_LATTE("Warm Latte", "Cozy Amber Cream"),
    NORDIC_FROST("Nordic Frost & Slate", "Arctic Cool Ice"),
    ROYAL_OBSIDIAN("Royal Obsidian & Gold", "Imperial Amber Gold"),
    LAVENDER_DUSK("Lavender Dusk", "Dreamy Twilight Violet"),
    TERRACOTTA_SAND("Terracotta & Sand", "Earthy Warm Clay"),
    KYOTO_SAKURA("Kyoto Sakura & Ink", "Delicate Spring Blossom"),
    OCEANIC_ABYSS("Oceanic Abyss", "Deep Maritime Cyan"),
    VELVET_BORDEAUX("Velvet Bordeaux", "Rich Crimson Wine"),
    MONOCHROME_MINIMAL("Monochrome Minimal", "High-Contrast Slate");

    /**
     * Returns 3 preview colors for display in settings and theme picker cards:
     * [0] Accent / Primary
     * [1] Container / Contrast
     * [2] Surface / Card background in the current base mode
     */
    fun getPreviewDots(baseMode: BaseThemeMode): List<Color> = when (this) {
        FOCUS_EMERALD -> listOf(
            Color(0xFF10B981),
            Color(0xFF064E3B),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF1A201C)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF0D0F0E)
            }
        )
        ROSE_QUARTZ -> listOf(
            Color(0xFFF472B6),
            Color(0xFF831843),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF201A1D)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF110D0F)
            }
        )
        SAGE_MATCHA -> listOf(
            Color(0xFF84CC16),
            Color(0xFF365314),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF1B2117)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF0E110C)
            }
        )
        WARM_LATTE -> listOf(
            Color(0xFFD97706),
            Color(0xFF78350F),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF211C17)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF110E0B)
            }
        )
        NORDIC_FROST -> listOf(
            Color(0xFF38BDF8),
            Color(0xFF075985),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF182025)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF0B1013)
            }
        )
        ROYAL_OBSIDIAN -> listOf(
            Color(0xFFFBBF24),
            Color(0xFF78350F),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF201E17)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF110F0B)
            }
        )
        LAVENDER_DUSK -> listOf(
            Color(0xFFA78BFA),
            Color(0xFF4C1D95),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF1E1825)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF0F0C13)
            }
        )
        TERRACOTTA_SAND -> listOf(
            Color(0xFFEA580C),
            Color(0xFF7C2D12),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF211A16)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF110D0B)
            }
        )
        KYOTO_SAKURA -> listOf(
            Color(0xFFFDA4AF),
            Color(0xFF881337),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF20171A)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF100C0E)
            }
        )
        OCEANIC_ABYSS -> listOf(
            Color(0xFF06B6D4),
            Color(0xFF155E75),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF162121)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF0B1010)
            }
        )
        VELVET_BORDEAUX -> listOf(
            Color(0xFFE11D48),
            Color(0xFF881337),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF201619)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF100A0C)
            }
        )
        MONOCHROME_MINIMAL -> listOf(
            Color(0xFFE2E8F0),
            Color(0xFF475569),
            when (baseMode) {
                BaseThemeMode.LIGHT -> Color(0xFFFFFFFF)
                BaseThemeMode.DARK -> Color(0xFF191C22)
                BaseThemeMode.AMOLED, BaseThemeMode.SYSTEM_DEFAULT -> Color(0xFF0D0E10)
            }
        )
    }
}
