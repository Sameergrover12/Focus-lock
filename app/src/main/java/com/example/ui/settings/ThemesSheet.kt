package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BaseThemeMode
import com.example.ui.theme.SecondaryThemePalette
import com.example.ui.theme.getFocusColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesBottomSheet(
    currentBaseMode: BaseThemeMode,
    currentPalette: SecondaryThemePalette,
    onSelectBaseMode: (BaseThemeMode) -> Unit,
    onSelectPalette: (SecondaryThemePalette) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag("themes_bottom_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Appearance & Themes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Customize base display contrast and accent color palette",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("themes_sheet_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Layer 1: Base Display Mode
            Text(
                text = "LAYER 1: BASE DISPLAY CONTRAST",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3-way segmented pill selector
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_mode_segmented_pill_container"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BaseModePillItem(
                        title = "Light",
                        icon = Icons.Default.LightMode,
                        selected = currentBaseMode == BaseThemeMode.LIGHT,
                        onClick = { onSelectBaseMode(BaseThemeMode.LIGHT) },
                        testTag = "base_mode_light",
                        modifier = Modifier.weight(1f)
                    )
                    BaseModePillItem(
                        title = "Dark\n(System)",
                        icon = Icons.Default.DarkMode,
                        selected = currentBaseMode == BaseThemeMode.DARK || currentBaseMode == BaseThemeMode.SYSTEM_DEFAULT,
                        onClick = { onSelectBaseMode(BaseThemeMode.DARK) },
                        testTag = "base_mode_dark",
                        modifier = Modifier.weight(1f)
                    )
                    BaseModePillItem(
                        title = "AMOLED\nBlack",
                        icon = Icons.Default.NightlightRound,
                        selected = currentBaseMode == BaseThemeMode.AMOLED,
                        onClick = { onSelectBaseMode(BaseThemeMode.AMOLED) },
                        testTag = "base_mode_amoled",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Layer 2: 12 Harmonious Secondary Color Themes
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LAYER 2: COLOR PALETTES (12)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Adapts to Base Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2-column grid of all 12 palettes
            val palettes = remember { SecondaryThemePalette.entries.toList() }
            val rows = remember(palettes) { palettes.chunked(2) }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { palette ->
                            PaletteGridCard(
                                palette = palette,
                                baseMode = currentBaseMode,
                                isSelected = palette == currentPalette,
                                onSelect = { onSelectPalette(palette) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BaseModePillItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun PaletteGridCard(
    palette: SecondaryThemePalette,
    baseMode: BaseThemeMode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate color preview for this palette in the current base mode
    val previewScheme = remember(palette, baseMode) {
        val effectiveBase = if (baseMode == BaseThemeMode.SYSTEM_DEFAULT) BaseThemeMode.DARK else baseMode
        getFocusColorScheme(effectiveBase, palette)
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .testTag("theme_palette_${palette.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = previewScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) previewScheme.primary else previewScheme.outline.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Miniature UI preview card showing how app surface + primary accent look
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(previewScheme.background)
                    .border(1.dp, previewScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(6.dp)
            ) {
                // Mini header bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(previewScheme.primary)
                )

                // Mini preview button / chip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(width = 24.dp, height = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(previewScheme.primaryContainer)
                )

                // Mini text lines inside the surface
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(previewScheme.onSurface.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(previewScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Palette Name + Selection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = palette.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = previewScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = palette.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = previewScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = previewScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(start = 2.dp)
                    )
                } else {
                    // Small color accent dot indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(previewScheme.primary)
                    )
                }
            }
        }
    }
}
