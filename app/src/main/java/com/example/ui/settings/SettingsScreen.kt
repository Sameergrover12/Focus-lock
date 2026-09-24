package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.example.receiver.FocusDeviceAdminReceiver
import com.example.data.preferences.ThemeMode
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Psychology
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.PendingLooseningAction
import com.example.ui.common.StrictModeActivationDialog
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.PermissionHelper

@Composable
fun SettingsScreen(
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()
    val isInvincibleModeEnabled by viewModel.isInvincibleModeEnabled.collectAsStateWithLifecycle()
    val cognitivePassphrase by viewModel.cognitivePassphrase.collectAsStateWithLifecycle()
    val emergencyBreaksRemaining by viewModel.emergencyBreaksRemaining.collectAsStateWithLifecycle()
    val activeEmergencyBreakUntil by viewModel.activeEmergencyBreakUntil.collectAsStateWithLifecycle()

    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var hasUsageStats by remember { mutableStateOf(PermissionHelper.isUsageStatsPermissionGranted(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var hasBattery by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }

    var showStrictModeDialog by remember { mutableStateOf(false) }
    var showInvincibleConfirmDialog by remember { mutableStateOf(false) }
    var showEmergencyBreakConfirmDialog by remember { mutableStateOf(false) }
    var pendingCheatAction by remember { mutableStateOf<PendingLooseningAction?>(null) }

    val isBreakActive = activeEmergencyBreakUntil > System.currentTimeMillis()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
                hasUsageStats = PermissionHelper.isUsageStatsPermissionGranted(context)
                hasOverlay = PermissionHelper.canDrawOverlays(context)
                hasBattery = PermissionHelper.isBatteryOptimizationIgnored(context)
                
                // If returning from Device Admin screen and user wanted to enable invincible
                val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent) && !isInvincibleModeEnabled && showInvincibleConfirmDialog) {
                    // Do nothing, dialog will show
                } else if (!dpm.isAdminActive(adminComponent)) {
                    showInvincibleConfirmDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showStrictModeDialog) {
        StrictModeActivationDialog(
            initialPassphrase = cognitivePassphrase,
            onConfirm = { definedPassphrase ->
                viewModel.setCognitivePassphrase(definedPassphrase)
                viewModel.setCheatProtection(definedPassphrase)
                showStrictModeDialog = false
            },
            onDismiss = { showStrictModeDialog = false }
        )
    }

    if (showInvincibleConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showInvincibleConfirmDialog = false },
            title = { Text("Point of No Return") },
            text = { Text("This will make the app completely invincible. You will not be able to uninstall the app, remove its permissions, or disable its services while a focus session is active. Do you accept this strict commitment?") },
            confirmButton = {
                Button(
                    onClick = {
                        showInvincibleConfirmDialog = false
                        viewModel.setInvincibleModeEnabled(true)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Make Invincible")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInvincibleConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmergencyBreakConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyBreakConfirmDialog = false },
            title = { Text("Take Emergency Break (10m)") },
            text = { Text("This will consume 1 of your 3 weekly emergency breaks for an immediate 10-minute window. All app and website blocking will be paused for exactly 10 minutes without countdowns or warnings. Do you wish to proceed?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyBreakConfirmDialog = false
                        viewModel.takeEmergencyBreak()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                ) {
                    Text("Start Break (10m)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmergencyBreakConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingCheatAction?.let { action ->
        CheatProtectionAuthDialog(
            action = action,
            onVerify = { viewModel.verifyCheatPassphrase(it) },
            onDismiss = { pendingCheatAction = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Section: Appearance / Theme Mode
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ThemeOptionRow(
                        title = "Follow System",
                        description = "Matches device dark/light theme setting",
                        icon = Icons.Default.SettingsBrightness,
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        testTag = "theme_system_option"
                    )
                    ThemeOptionRow(
                        title = "Light Theme",
                        description = "Clean, high-contrast off-white palette",
                        icon = Icons.Default.LightMode,
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        testTag = "theme_light_option"
                    )
                    ThemeOptionRow(
                        title = "Dark Theme",
                        description = "Deep charcoal, comfortable for night focus",
                        icon = Icons.Default.DarkMode,
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        testTag = "theme_dark_option"
                    )
                }
            }
        }

        // Section: Emergency Failsafe System
        item {
            Text(
                text = "Emergency Failsafe System",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emergency_failsafe_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E11)),
                border = BorderStroke(1.dp, Color(0xFF22262F)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "The Emergency Failsafe System",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "$emergencyBreaksRemaining of 3 breaks remaining this week",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (emergencyBreaksRemaining > 0) Color(0xFF10B981) else Color(0xFFEF5350),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• 10-Minute Hard Cap: 3 breaks per 7-day cycle.\n• The Phantom Cutoff: Zero visual countdown timers.\n• The Abrupt Snapback: Immediate lock restoration at 10:00:00.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color(0xFF22262F))
                    Spacer(modifier = Modifier.height(14.dp))

                    if (isBreakActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Emergency Break Active (10m)",
                                        color = Color(0xFF10B981),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "All app & website blocking is paused.",
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = { viewModel.endEmergencyBreakEarly() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE53935),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("end_emergency_break_button")
                                ) {
                                    Text("End Early", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (emergencyBreaksRemaining > 0) {
                        Button(
                            onClick = { showEmergencyBreakConfirmDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("take_emergency_break_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Take Emergency Break (10m)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFF1E2024),
                                disabledContentColor = Color(0xFF666666)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("take_emergency_break_button")
                        ) {
                            Text(
                                text = "No Breaks Remaining",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Cheat Protection Lock (Change 4)
        item {
            Text(
                text = "Cheating Protection & Strict Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cheat_protection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0D0E11)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isCheatProtectionEnabled) Color(0xFF10B981) else Color(0xFF22262F)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCheatProtectionEnabled) Color(0xFF064E3B) else Color(0xFF141519)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isCheatProtectionEnabled) Color(0xFF10B981) else Color(0xFFB0B0B0),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Strict Mode / Anti-Cheat Lock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isCheatProtectionEnabled) "Active • Verbatim passphrase required to loosen" else "Disabled • Rules can be changed freely",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCheatProtectionEnabled) Color(0xFF10B981) else Color(0xFF9E9E9E)
                                )
                            }
                        }

                        Switch(
                            checked = isCheatProtectionEnabled,
                            onCheckedChange = { willEnable ->
                                if (willEnable) {
                                    showStrictModeDialog = true
                                } else {
                                    pendingCheatAction = PendingLooseningAction(
                                        title = "Disable Strict Mode",
                                        description = "Disabling Strict Mode allows all rules and limits to be modified freely. Enter your reflective passphrase verbatim to authorize.",
                                        onAuthorized = { viewModel.disableCheatProtection() }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("cheat_protection_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF064E3B),
                                uncheckedThumbColor = Color(0xFF888888),
                                uncheckedTrackColor = Color(0xFF1E2024)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "When active, any action that deletes a rule, increases/removes a screen-time limit, or pauses protection strictly requires typing a 600–1000 character passphrase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isCheatProtectionEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Strict Mode (Device Administrator)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isInvincibleModeEnabled) "Active • Device Admin active (anti-uninstall barrier)" else "Disabled • Device Admin privileges inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isInvincibleModeEnabled,
                                onCheckedChange = { willEnable ->
                                    if (willEnable) {
                                        val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                        val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
                                        if (dpm.isAdminActive(adminComponent)) {
                                            showInvincibleConfirmDialog = true
                                        } else {
                                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Invincible Mode to prevent uninstallation.")
                                            }
                                            context.startActivity(intent)
                                            showInvincibleConfirmDialog = true
                                        }
                                    } else {
                                        pendingCheatAction = PendingLooseningAction(
                                            title = "Disable Invincible Mode",
                                            description = "Disabling Invincible Mode will allow uninstalling the app and changing system settings.",
                                            onAuthorized = { viewModel.setInvincibleModeEnabled(false) }
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onError,
                                    checkedTrackColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }
        }

        // Section: System Permissions Status
        item {
            Text(
                text = "System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    PermissionStatusRow(
                        title = "Accessibility Service",
                        isGranted = hasAccessibility,
                        onClick = { PermissionHelper.openAccessibilitySettings(context) }
                    )
                    PermissionStatusRow(
                        title = "Usage Access",
                        isGranted = hasUsageStats,
                        onClick = { PermissionHelper.openUsageAccessSettings(context) }
                    )
                    PermissionStatusRow(
                        title = "Display Over Other Apps",
                        isGranted = hasOverlay,
                        onClick = { PermissionHelper.openOverlaySettings(context) }
                    )
                    PermissionStatusRow(
                        title = "Battery Optimization Exemption",
                        isGranted = hasBattery,
                        onClick = { PermissionHelper.openBatteryOptimizationSettings(context) }
                    )
                }
            }
        }

        // Section: OEM Tips if applicable
        if (PermissionHelper.isAggressiveOem()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${PermissionHelper.getOemName()} Device Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Some phone manufacturers automatically kill background services. Ensure Focus Lock has 'Autostart' enabled in phone settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section: About
        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Focus Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0 • 100% Local & Private",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No analytics, no ads, and no external servers. All rules, logs, and database records remain exclusively on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isGranted) "Granted" else "Fix in Settings",
            style = MaterialTheme.typography.labelMedium,
            color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
