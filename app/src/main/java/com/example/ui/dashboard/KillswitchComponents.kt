package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.FocusHaptics
import kotlinx.coroutines.delay

const val EXACT_KILLSWITCH_PLEDGE =
    "This break is only for an actual emergency. I will handle my work and close the phone immediately after, because I am committed to investing my time into building my future."

@Composable
fun KillswitchCard(
    viewModel: FocusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    val isKillswitchActive by viewModel.isKillswitchActive.collectAsStateWithLifecycle()
    val isBreakActive by viewModel.isKillswitchEmergencyBreakActive.collectAsStateWithLifecycle()
    val killswitchEndTime by viewModel.killswitchEndTime.collectAsStateWithLifecycle()
    val breakEndTime by viewModel.killswitchBreakEndTime.collectAsStateWithLifecycle()
    val isBreakUsed by viewModel.isKillswitchBreakUsed.collectAsStateWithLifecycle()

    var showSetupDialog by remember { mutableStateOf(false) }
    var showPledgeDialog by remember { mutableStateOf(false) }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isKillswitchActive, isBreakActive) {
        while (isKillswitchActive) {
            currentTimeMs = System.currentTimeMillis()
            delay(500L)
        }
    }

    if (showSetupDialog) {
        KillswitchSetupDialog(
            onDismiss = { showSetupDialog = false },
            onEngage = { durationMinutes ->
                FocusHaptics.confirm(view)
                viewModel.startKillswitch(durationMinutes)
                showSetupDialog = false
            }
        )
    }

    if (showPledgeDialog) {
        KillswitchPledgeDialog(
            onDismiss = { showPledgeDialog = false },
            onUnlock = {
                FocusHaptics.confirm(view)
                val success = viewModel.startKillswitchEmergencyBreak()
                if (success) {
                    showPledgeDialog = false
                }
            }
        )
    }

    if (!isKillswitchActive) {
        // Inactive State Card
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("killswitch_card_inactive"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Killswitch Icon",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Killswitch",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Emergency full-phone lockdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        FocusHaptics.tick(context)
                        showSetupDialog = true
                    },
                    modifier = Modifier.testTag("killswitch_engage_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Engage",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // Active Vault Card
        val isBreakNow = isBreakActive && breakEndTime > currentTimeMs
        val remainingMs = if (isBreakNow) {
            maxOf(0L, breakEndTime - currentTimeMs)
        } else {
            maxOf(0L, killswitchEndTime - currentTimeMs)
        }

        val totalSecs = remainingMs / 1000L
        val hours = totalSecs / 3600L
        val minutes = (totalSecs % 3600L) / 60L
        val seconds = totalSecs % 60L
        val countdownDisplay = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("killswitch_card_active"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isBreakNow) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            border = BorderStroke(
                1.5.dp,
                if (isBreakNow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isBreakNow) Icons.Default.HourglassBottom else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isBreakNow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBreakNow) "EMERGENCY BREAK ACTIVE" else "KILLSWITCH ENGAGED • TOTAL LOCKDOWN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isBreakNow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Countdown Display
                Text(
                    text = countdownDisplay,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("killswitch_countdown_text")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isBreakNow) {
                        "Focus lockdown resumes abruptly when timer reaches 00:00"
                    } else {
                        "All non-essential apps locked • Only phone calls permitted"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Area
                if (isBreakNow) {
                    Button(
                        onClick = {
                            FocusHaptics.confirm(view)
                            viewModel.endKillswitchEmergencyBreakEarly()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("killswitch_end_break_early_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Work Done • Re-Lock Phone Now",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    if (!isBreakUsed) {
                        OutlinedButton(
                            onClick = {
                                FocusHaptics.tick(context)
                                showPledgeDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("killswitch_take_break_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Take 10m Emergency Break (1 of 1 remaining)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Emergency Break Used (0 of 1 remaining)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KillswitchSetupDialog(
    onDismiss: () -> Unit,
    onEngage: (minutes: Int) -> Unit
) {
    val presets = listOf(15, 30, 60, 120, 180)
    val presetLabels = mapOf(
        15 to "15m",
        30 to "30m",
        60 to "1h",
        120 to "2h",
        180 to "3h"
    )

    var selectedMinutes by remember { mutableIntStateOf(60) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Engage Killswitch",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("killswitch_setup_dialog")
            ) {
                Text(
                    text = "Select lockdown duration:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Preset Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { mins ->
                        val isSelected = selectedMinutes == mins && customText.isEmpty()
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedMinutes = mins
                                customText = ""
                            },
                            label = {
                                Text(
                                    text = presetLabels[mins] ?: "${mins}m",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Minutes Input
                OutlinedTextField(
                    value = customText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(4)
                        customText = filtered
                        val parsed = filtered.toIntOrNull()
                        if (parsed != null && parsed > 0) {
                            selectedMinutes = parsed.coerceIn(1, 1440)
                        }
                    },
                    label = { Text("Or enter custom minutes (1–1440)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("killswitch_custom_minutes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Warning Paraphrase Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Warning: Engaging Killswitch locks all non-essential apps for the entire duration. Phone & Emergency calls remain active. Only 1 single 10-minute break is permitted, unlocked exclusively by typing an exact commitment pledge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEngage(selectedMinutes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("killswitch_confirm_button")
            ) {
                Text(
                    text = "Engage ($selectedMinutes min)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun KillswitchPledgeDialog(
    onDismiss: () -> Unit,
    onUnlock: () -> Unit
) {
    val targetPledge = EXACT_KILLSWITCH_PLEDGE
    var typedPledge by remember { mutableStateOf("") }

    val isExactMatch = typedPledge.trim() == targetPledge.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Emergency Break Pledge",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("killswitch_pledge_dialog")
            ) {
                Text(
                    text = "Type the exact pledge below to unlock a single 10-minute break. Pasting is strictly blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Box 1: Target Read-Only Pledge Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("killswitch_pledge_target_box")
                ) {
                    Text(
                        text = targetPledge,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Box 2: Manual Typing Input (Paste Blocked)
                OutlinedTextField(
                    value = typedPledge,
                    onValueChange = { newValue ->
                        // Strictly block paste operations: reject change if > 2 characters inserted at once
                        if (newValue.length - typedPledge.length <= 2) {
                            typedPledge = newValue
                        }
                    },
                    label = { Text("Type pledge word-for-word here") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("killswitch_pledge_input_field"),
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Indicator / Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExactMatch) "Pledge matched!" else "Characters: ${typedPledge.length} / ${targetPledge.length}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isExactMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isExactMatch) FontWeight.Bold else FontWeight.Normal
                    )

                    if (isExactMatch) {
                        Text(
                            text = "Ready to Unlock",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUnlock,
                enabled = isExactMatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("killswitch_unlock_break_button")
            ) {
                Text(
                    text = "Unlock 10-Minute Break",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
