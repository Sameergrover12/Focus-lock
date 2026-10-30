package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private fun createColorScheme(
    isAmoled: Boolean,
    isDark: Boolean,
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color,
    outline: Color,
    onBackground: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    error: Color = if (isDark || isAmoled) Color(0xFFEF4444) else Color(0xFFDC2626),
    onError: Color = Color.White
): ColorScheme {
    return if (isDark || isAmoled) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer.copy(alpha = 0.5f),
            onSecondaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.4f),
            error = error,
            onError = onError
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer.copy(alpha = 0.5f),
            onSecondaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outline.copy(alpha = 0.4f),
            error = error,
            onError = onError
        )
    }
}

/**
 * Maps a (BaseThemeMode, SecondaryThemePalette) combination to a full M3 ColorScheme.
 */
fun getFocusColorScheme(
    baseMode: BaseThemeMode,
    palette: SecondaryThemePalette
): ColorScheme {
    val isAmoled = baseMode == BaseThemeMode.AMOLED
    val isDark = baseMode == BaseThemeMode.DARK

    return when (palette) {
        SecondaryThemePalette.FOCUS_EMERALD -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFF10B981), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF064E3B), onPrimaryContainer = Color(0xFFA7F3D0),
                secondary = Color(0xFF34D399),
                background = Color(0xFF000000),
                surface = Color(0xFF0D0F0E),
                surfaceVariant = Color(0xFF141715),
                outline = Color(0xFF222B25),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFF9E9E9E)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFF10B981), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF064E3B), onPrimaryContainer = Color(0xFFA7F3D0),
                secondary = Color(0xFF34D399),
                background = Color(0xFF121513),
                surface = Color(0xFF1A201C),
                surfaceVariant = Color(0xFF222924),
                outline = Color(0xFF2E3A32),
                onBackground = Color(0xFFF1F5F2),
                onSurface = Color(0xFFF1F5F2),
                onSurfaceVariant = Color(0xFF9CA8A0)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFF10B981), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B),
                secondary = Color(0xFF059669),
                background = Color(0xFFFAFAF8),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEEF2EF),
                outline = Color(0xFFD4DBD6),
                onBackground = Color(0xFF191C1A),
                onSurface = Color(0xFF191C1A),
                onSurfaceVariant = Color(0xFF404944)
            )
        }

        SecondaryThemePalette.ROSE_QUARTZ -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFF472B6), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF701A45), onPrimaryContainer = Color(0xFFFBCFE8),
                secondary = Color(0xFFFB7185),
                background = Color(0xFF000000),
                surface = Color(0xFF110D0F),
                surfaceVariant = Color(0xFF181216),
                outline = Color(0xFF2B1F26),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFA898A0)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFF472B6), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF701A45), onPrimaryContainer = Color(0xFFFBCFE8),
                secondary = Color(0xFFFB7185),
                background = Color(0xFF161214),
                surface = Color(0xFF201A1D),
                surfaceVariant = Color(0xFF2B2227),
                outline = Color(0xFF3D2F37),
                onBackground = Color(0xFFFDF2F8),
                onSurface = Color(0xFFFDF2F8),
                onSurfaceVariant = Color(0xFFA898A0)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFFDB2777), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFCE7F3), onPrimaryContainer = Color(0xFF831843),
                secondary = Color(0xFFE11D48),
                background = Color(0xFFFAF7F8),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF5EBEF),
                outline = Color(0xFFE2D4DA),
                onBackground = Color(0xFF1C191A),
                onSurface = Color(0xFF1C191A),
                onSurfaceVariant = Color(0xFF4E4448)
            )
        }

        SecondaryThemePalette.SAGE_MATCHA -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFA3E635), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF365314), onPrimaryContainer = Color(0xFFD9F99D),
                secondary = Color(0xFFBEF264),
                background = Color(0xFF000000),
                surface = Color(0xFF0E110C),
                surfaceVariant = Color(0xFF151912),
                outline = Color(0xFF242C1F),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFA3ACA0)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFA3E635), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF365314), onPrimaryContainer = Color(0xFFD9F99D),
                secondary = Color(0xFFBEF264),
                background = Color(0xFF131611),
                surface = Color(0xFF1B2117),
                surfaceVariant = Color(0xFF242D20),
                outline = Color(0xFF34402E),
                onBackground = Color(0xFFF7FEE7),
                onSurface = Color(0xFFF7FEE7),
                onSurfaceVariant = Color(0xFFA3ACA0)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFF65A30D), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFECFCCB), onPrimaryContainer = Color(0xFF365314),
                secondary = Color(0xFF4D7C0F),
                background = Color(0xFFF8FAF6),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEDF2EA),
                outline = Color(0xFFD2DCD0),
                onBackground = Color(0xFF171B14),
                onSurface = Color(0xFF171B14),
                onSurfaceVariant = Color(0xFF434A3E)
            )
        }

        SecondaryThemePalette.WARM_LATTE -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFF59E0B), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF78350F), onPrimaryContainer = Color(0xFFFDE68A),
                secondary = Color(0xFFFBBF24),
                background = Color(0xFF000000),
                surface = Color(0xFF110E0B),
                surfaceVariant = Color(0xFF191410),
                outline = Color(0xFF2B221A),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFAAA097)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFF59E0B), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF78350F), onPrimaryContainer = Color(0xFFFDE68A),
                secondary = Color(0xFFFBBF24),
                background = Color(0xFF161310),
                surface = Color(0xFF211C17),
                surfaceVariant = Color(0xFF2C2620),
                outline = Color(0xFF3F352C),
                onBackground = Color(0xFFFFFBEB),
                onSurface = Color(0xFFFFFBEB),
                onSurfaceVariant = Color(0xFFAAA097)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFFB45309), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFEF3C7), onPrimaryContainer = Color(0xFF78350F),
                secondary = Color(0xFFD97706),
                background = Color(0xFFFAF8F5),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF5EFEB),
                outline = Color(0xFFE2D9D2),
                onBackground = Color(0xFF1C1814),
                onSurface = Color(0xFF1C1814),
                onSurfaceVariant = Color(0xFF4C433C)
            )
        }

        SecondaryThemePalette.NORDIC_FROST -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFF38BDF8), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF0C4A6E), onPrimaryContainer = Color(0xFFBAE6FD),
                secondary = Color(0xFF7DD3FC),
                background = Color(0xFF000000),
                surface = Color(0xFF0B1013),
                surfaceVariant = Color(0xFF12181C),
                outline = Color(0xFF1D282F),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFF94A7B3)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFF38BDF8), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF0C4A6E), onPrimaryContainer = Color(0xFFBAE6FD),
                secondary = Color(0xFF7DD3FC),
                background = Color(0xFF101518),
                surface = Color(0xFF182025),
                surfaceVariant = Color(0xFF202B32),
                outline = Color(0xFF2D3D47),
                onBackground = Color(0xFFF0F9FF),
                onSurface = Color(0xFFF0F9FF),
                onSurfaceVariant = Color(0xFF94A7B3)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFF0284C7), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE0F2FE), onPrimaryContainer = Color(0xFF075985),
                secondary = Color(0xFF0369A1),
                background = Color(0xFFF5F9FA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE8F1F5),
                outline = Color(0xFFCEE0E8),
                onBackground = Color(0xFF10171B),
                onSurface = Color(0xFF10171B),
                onSurfaceVariant = Color(0xFF3D4850)
            )
        }

        SecondaryThemePalette.ROYAL_OBSIDIAN -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFFBBF24), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF78350F), onPrimaryContainer = Color(0xFFFEF08A),
                secondary = Color(0xFFF59E0B),
                background = Color(0xFF000000),
                surface = Color(0xFF110F0B),
                surfaceVariant = Color(0xFF181610),
                outline = Color(0xFF2B261A),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFABA497)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFFBBF24), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF78350F), onPrimaryContainer = Color(0xFFFEF08A),
                secondary = Color(0xFFF59E0B),
                background = Color(0xFF151410),
                surface = Color(0xFF201E17),
                surfaceVariant = Color(0xFF2B2820),
                outline = Color(0xFF3E3A2E),
                onBackground = Color(0xFFFFFBEB),
                onSurface = Color(0xFFFFFBEB),
                onSurfaceVariant = Color(0xFFABA497)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFFD97706), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFEF3C7), onPrimaryContainer = Color(0xFF78350F),
                secondary = Color(0xFFB45309),
                background = Color(0xFFFAF9F5),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF5F1E8),
                outline = Color(0xFFE2DAD0),
                onBackground = Color(0xFF1C1A14),
                onSurface = Color(0xFF1C1A14),
                onSurfaceVariant = Color(0xFF4D473C)
            )
        }

        SecondaryThemePalette.LAVENDER_DUSK -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFA78BFA), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF4C1D95), onPrimaryContainer = Color(0xFFDDD6FE),
                secondary = Color(0xFFC084FC),
                background = Color(0xFF000000),
                surface = Color(0xFF0F0C13),
                surfaceVariant = Color(0xFF16111C),
                outline = Color(0xFF271E31),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFA39AA8)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFA78BFA), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF4C1D95), onPrimaryContainer = Color(0xFFDDD6FE),
                secondary = Color(0xFFC084FC),
                background = Color(0xFF141118),
                surface = Color(0xFF1E1825),
                surfaceVariant = Color(0xFF282032),
                outline = Color(0xFF3A2E48),
                onBackground = Color(0xFFF5F3FF),
                onSurface = Color(0xFFF5F3FF),
                onSurfaceVariant = Color(0xFFA39AA8)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFF7C3AED), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFEDE9FE), onPrimaryContainer = Color(0xFF4C1D95),
                secondary = Color(0xFF6D28D9),
                background = Color(0xFFF8F7FA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEFEBF5),
                outline = Color(0xFFD8CEE2),
                onBackground = Color(0xFF19151F),
                onSurface = Color(0xFF19151F),
                onSurfaceVariant = Color(0xFF483F52)
            )
        }

        SecondaryThemePalette.TERRACOTTA_SAND -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFFB923C), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF7C2D12), onPrimaryContainer = Color(0xFFFFEDD5),
                secondary = Color(0xFFF97316),
                background = Color(0xFF000000),
                surface = Color(0xFF110D0B),
                surfaceVariant = Color(0xFF18130F),
                outline = Color(0xFF2C201A),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFABA09A)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFFB923C), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF7C2D12), onPrimaryContainer = Color(0xFFFFEDD5),
                secondary = Color(0xFFF97316),
                background = Color(0xFF161210),
                surface = Color(0xFF211A16),
                surfaceVariant = Color(0xFF2D231E),
                outline = Color(0xFF40322B),
                onBackground = Color(0xFFFFF7ED),
                onSurface = Color(0xFFFFF7ED),
                onSurfaceVariant = Color(0xFFABA09A)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFFC2410C), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFEDD5), onPrimaryContainer = Color(0xFF7C2D12),
                secondary = Color(0xFFEA580C),
                background = Color(0xFFFAF7F5),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF5ECE8),
                outline = Color(0xFFE2D2CC),
                onBackground = Color(0xFF1C1613),
                onSurface = Color(0xFF1C1613),
                onSurfaceVariant = Color(0xFF4E423D)
            )
        }

        SecondaryThemePalette.KYOTO_SAKURA -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFFDA4AF), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF881337), onPrimaryContainer = Color(0xFFFECDD3),
                secondary = Color(0xFFFB7185),
                background = Color(0xFF000000),
                surface = Color(0xFF100C0E),
                surfaceVariant = Color(0xFF181114),
                outline = Color(0xFF2A1E23),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFA9989E)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFFDA4AF), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF881337), onPrimaryContainer = Color(0xFFFECDD3),
                secondary = Color(0xFFFB7185),
                background = Color(0xFF151012),
                surface = Color(0xFF20171A),
                surfaceVariant = Color(0xFF2B1F23),
                outline = Color(0xFF3E2C33),
                onBackground = Color(0xFFFFF1F2),
                onSurface = Color(0xFFFFF1F2),
                onSurfaceVariant = Color(0xFFA9989E)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFFE11D48), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFE4E6), onPrimaryContainer = Color(0xFF881337),
                secondary = Color(0xFFBE123C),
                background = Color(0xFFFAF6F7),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF5E9EC),
                outline = Color(0xFFE2CFD4),
                onBackground = Color(0xFF1A1416),
                onSurface = Color(0xFF1A1416),
                onSurfaceVariant = Color(0xFF4C3F43)
            )
        }

        SecondaryThemePalette.OCEANIC_ABYSS -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFF06B6D4), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF155E75), onPrimaryContainer = Color(0xFFA5F3FC),
                secondary = Color(0xFF14B8A6),
                background = Color(0xFF000000),
                surface = Color(0xFF0B1010),
                surfaceVariant = Color(0xFF111818),
                outline = Color(0xFF1C2929),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFF92A8A8)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFF06B6D4), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF155E75), onPrimaryContainer = Color(0xFFA5F3FC),
                secondary = Color(0xFF14B8A6),
                background = Color(0xFF0E1515),
                surface = Color(0xFF162121),
                surfaceVariant = Color(0xFF1E2C2C),
                outline = Color(0xFF2B3E3E),
                onBackground = Color(0xFFECFEFF),
                onSurface = Color(0xFFECFEFF),
                onSurfaceVariant = Color(0xFF92A8A8)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFF0891B2), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFCFFAFE), onPrimaryContainer = Color(0xFF155E75),
                secondary = Color(0xFF0E7490),
                background = Color(0xFFF5FAF9),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE8F4F3),
                outline = Color(0xFFCDE6E4),
                onBackground = Color(0xFF0F1818),
                onSurface = Color(0xFF0F1818),
                onSurfaceVariant = Color(0xFF3C4949)
            )
        }

        SecondaryThemePalette.VELVET_BORDEAUX -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFFB7185), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF881337), onPrimaryContainer = Color(0xFFFECDD3),
                secondary = Color(0xFFF43F5E),
                background = Color(0xFF000000),
                surface = Color(0xFF100A0C),
                surfaceVariant = Color(0xFF170F13),
                outline = Color(0xFF2A1B20),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFFAA979D)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFFB7185), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF881337), onPrimaryContainer = Color(0xFFFECDD3),
                secondary = Color(0xFFF43F5E),
                background = Color(0xFF150F11),
                surface = Color(0xFF201619),
                surfaceVariant = Color(0xFF2B1D22),
                outline = Color(0xFF3E2930),
                onBackground = Color(0xFFFFF1F2),
                onSurface = Color(0xFFFFF1F2),
                onSurfaceVariant = Color(0xFFAA979D)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFFBE123C), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFE4E6), onPrimaryContainer = Color(0xFF881337),
                secondary = Color(0xFF9F1239),
                background = Color(0xFFFAF6F7),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF5E8EB),
                outline = Color(0xFFE2CED2),
                onBackground = Color(0xFF1A1315),
                onSurface = Color(0xFF1A1315),
                onSurfaceVariant = Color(0xFF4C3D41)
            )
        }

        SecondaryThemePalette.MONOCHROME_MINIMAL -> when {
            isAmoled -> createColorScheme(
                isAmoled = true, isDark = false,
                primary = Color(0xFFE2E8F0), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF334155), onPrimaryContainer = Color(0xFFF8FAFC),
                secondary = Color(0xFF94A3B8),
                background = Color(0xFF000000),
                surface = Color(0xFF0D0E10),
                surfaceVariant = Color(0xFF141619),
                outline = Color(0xFF22262B),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
            isDark -> createColorScheme(
                isAmoled = false, isDark = true,
                primary = Color(0xFFE2E8F0), onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF334155), onPrimaryContainer = Color(0xFFF8FAFC),
                secondary = Color(0xFF94A3B8),
                background = Color(0xFF111317),
                surface = Color(0xFF191C22),
                surfaceVariant = Color(0xFF22262E),
                outline = Color(0xFF323844),
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC),
                onSurfaceVariant = Color(0xFF94A3B8)
            )
            else -> createColorScheme(
                isAmoled = false, isDark = false,
                primary = Color(0xFF475569), onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFE2E8F0), onPrimaryContainer = Color(0xFF1E293B),
                secondary = Color(0xFF334155),
                background = Color(0xFFF8FAFC),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF1F5F9),
                outline = Color(0xFFCBD5E1),
                onBackground = Color(0xFF0F172A),
                onSurface = Color(0xFF0F172A),
                onSurfaceVariant = Color(0xFF475569)
            )
        }
    }
}
