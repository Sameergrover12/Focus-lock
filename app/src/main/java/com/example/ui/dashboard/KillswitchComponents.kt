package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.FocusHaptics
import kotlinx.coroutines.delay

private val KillswitchRedAccent = Color(0xFFEF4444)

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
            onStart = { durationMinutes ->
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
        // Inactive State Card: Dynamically inherits theme surface/tones with a crisp red boundary border
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("killswitch_card_inactive"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, KillswitchRedAccent)
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
                                color = KillswitchRedAccent.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .border(
                                1.dp,
                                KillswitchRedAccent.copy(alpha = 0.4f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Killswitch Icon",
                            tint = KillswitchRedAccent,
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
                            text = "Full phone lockdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        FocusHaptics.tick(context)
                        showSetupDialog = true
                    },
                    modifier = Modifier.testTag("killswitch_start_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = BorderStroke(1.dp, KillswitchRedAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Start",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // Active Vault Card: Hard lockdown state with red boundary border and dynamic theme surfaces
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
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.5.dp, KillswitchRedAccent)
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
                        tint = KillswitchRedAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBreakNow) "BREAK ACTIVE • 10M WINDOW" else "KILLSWITCH ENGAGED • TOTAL LOCKDOWN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = KillswitchRedAccent,
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
                        "All non-essential apps locked • Only essential calls permitted"
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
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = BorderStroke(1.dp, KillswitchRedAccent),
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
                            border = BorderStroke(1.dp, KillswitchRedAccent.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = KillswitchRedAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Take 10m Break (1 of 1 remaining)",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Break Used (0 of 1 remaining)",
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
    onStart: (minutes: Int) -> Unit
) {
    // 5 Duration Presets: 15m, 30m, 1hr, 2hr, 3hr
    val presets = listOf(15, 30, 60, 120, 180)
    val presetLabels = mapOf(
        15 to "15m",
        30 to "30m",
        60 to "1hr",
        120 to "2hr",
        180 to "3hr"
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
                    tint = KillswitchRedAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Killswitch",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Equal-Sized Duration Preset Chips (15m, 30m, 1hr, 2hr, 3hr)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { mins ->
                        val isSelected = selectedMinutes == mins && customText.isEmpty()
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clickable {
                                    selectedMinutes = mins
                                    customText = ""
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = presetLabels[mins] ?: "${mins}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                    label = { Text("Enter the time manually") },
                    placeholder = { Text("Minutes (1–1440)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("killswitch_custom_minutes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Warning Box Copy (cleaned up copy without the word "emergency")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KillswitchRedAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, KillswitchRedAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = KillswitchRedAccent,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Warning: Locking your phone restricts all non-essential apps for the entire duration. Phone & essential calls remain active. Only 1 single 10-minute break is permitted, unlocked exclusively by typing an exact commitment pledge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(selectedMinutes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = BorderStroke(1.dp, KillswitchRedAccent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("killswitch_confirm_button")
            ) {
                Text(
                    text = "Start ($selectedMinutes min)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    tint = KillswitchRedAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Break Verification",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, KillswitchRedAccent),
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
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
