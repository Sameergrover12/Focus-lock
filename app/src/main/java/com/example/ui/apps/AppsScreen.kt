package com.example.ui.apps

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppInfo
import com.example.data.repository.FocusRepository
import com.example.ui.common.AppIconImage
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.PendingLooseningAction
import com.example.ui.viewmodel.FocusViewModel

@Composable
fun AppsScreen(
    viewModel: FocusViewModel
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Hard Block, 1: Screen Time Limits

    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val screenLimits by viewModel.screenTimeLimits.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()
    val todayUsageLogs by viewModel.todayUsageLogs.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var appToSetLimit by remember { mutableStateOf<AppInfo?>(null) }
    var pendingCheatAction by remember { mutableStateOf<PendingLooseningAction?>(null) }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Memoize lookup structures to avoid O(N) linear scans during LazyColumn item rendering
    val blockedSet = remember(blockedApps) {
        blockedApps.map { it.packageName }.toSet()
    }
    val limitsMap = remember(screenLimits) {
        screenLimits.associateBy { it.packageName }
    }
    val usageLogsMap = remember(todayUsageLogs) {
        todayUsageLogs.associateBy { it.packageName }
    }
    val todayDateStr = remember { FocusRepository.getTodayDateString() }

    pendingCheatAction?.let { action ->
        CheatProtectionAuthDialog(
            action = action,
            onVerify = { viewModel.verifyCheatPassphrase(it) },
            onDismiss = { pendingCheatAction = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("apps_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Hard Block (${blockedApps.size})") },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Screen Time (${screenLimits.size})") },
                icon = { Icon(Icons.Default.HourglassBottom, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Search Bar
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_search_field"),
                placeholder = {
                    Text(if (selectedSubTab == 0) "Search apps to block..." else "Search apps for time limits...")
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Content List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (installedApps.isEmpty()) "Loading installed applications..." else "No matching applications found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                if (selectedSubTab == 0) {
                    // Feature 1: Hard Block Toggle
                    val isBlocked = blockedSet.contains(app.packageName)
                    AppBlockRow(
                        app = app,
                        isBlocked = isBlocked,
                        onToggle = { shouldBlock ->
                            if (!shouldBlock && isCheatProtectionEnabled) {
                                pendingCheatAction = PendingLooseningAction(
                                    title = "Unblock ${app.appName}",
                                    description = "Removing the block will restore unrestricted access to ${app.appName}.",
                                    onAuthorized = { viewModel.toggleAppBlock(app, false) }
                                )
                            } else {
                                viewModel.toggleAppBlock(app, shouldBlock)
                            }
                        }
                    )
                } else {
                    // Feature 2: Screen Time Limit Row
                    val limitEntity = limitsMap[app.packageName]
                    val usageLog = usageLogsMap[app.packageName]
                    val actualUsedMinutes = usageLog?.minutesUsed
                        ?: (if (limitEntity?.lastResetDate == todayDateStr) limitEntity.usedMinutesToday else 0)
                    AppScreenLimitRow(
                        app = app,
                        limitMinutes = limitEntity?.dailyLimitMinutes ?: 0,
                        usedMinutes = actualUsedMinutes,
                        onConfigure = {
                            appToSetLimit = app
                        }
                    )
                }
            }
        }
    }

    // Time Limit Configuration Dialog
    if (appToSetLimit != null) {
        val targetApp = appToSetLimit!!
        val currentLimit = screenLimits.firstOrNull { it.packageName == targetApp.packageName }
        val targetUsageLog = todayUsageLogs.firstOrNull { it.packageName == targetApp.packageName }
        val usedToday = targetUsageLog?.minutesUsed
            ?: (if (currentLimit?.lastResetDate == FocusRepository.getTodayDateString()) currentLimit.usedMinutesToday else 0)

        TimeLimitDialog(
            app = targetApp,
            currentMinutes = currentLimit?.dailyLimitMinutes ?: 0,
            alreadyUsedTodayMinutes = usedToday,
            onDismiss = { appToSetLimit = null },
            onSave = { minutes ->
                val prevMinutes = currentLimit?.dailyLimitMinutes
                if (prevMinutes != null && minutes > prevMinutes && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Increase Limit for ${targetApp.appName}",
                        description = "Increasing daily limit from ${prevMinutes}m to ${minutes}m grants more daily screen time.",
                        onAuthorized = { viewModel.setAppScreenLimit(targetApp.packageName, minutes) }
                    )
                } else {
                    viewModel.setAppScreenLimit(targetApp.packageName, minutes)
                }
                appToSetLimit = null
            },
            onRemove = {
                if (isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Remove Limit for ${targetApp.appName}",
                        description = "Removing daily screen time limit allows unlimited usage of ${targetApp.appName}.",
                        onAuthorized = { viewModel.removeAppScreenLimit(targetApp.packageName) }
                    )
                } else {
                    viewModel.removeAppScreenLimit(targetApp.packageName)
                }
                appToSetLimit = null
            }
        )
    }
}

@Composable
private fun AppBlockRow(
    app: AppInfo,
    isBlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_block_row_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(icon = app.icon, modifier = Modifier.size(44.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isBlocked) "Locked — Opening blocked" else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("block_toggle_${app.packageName}")
            )
        }
    }
}

@Composable
private fun AppScreenLimitRow(
    app: AppInfo,
    limitMinutes: Int,
    usedMinutes: Int,
    onConfigure: () -> Unit
) {
    val hasLimit = limitMinutes > 0
    val (progress, limitStr, usedStr) = remember(limitMinutes, usedMinutes) {
        val p = if (hasLimit) {
            (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val lStr = if (hasLimit) {
            val h = limitMinutes / 60
            val m = limitMinutes % 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        } else "No limit"

        val uStr = if (hasLimit) {
            val h = usedMinutes / 60
            val m = usedMinutes % 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        } else ""

        Triple(p, lStr, uStr)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
            .testTag("app_limit_row_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(icon = app.icon, modifier = Modifier.size(42.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasLimit) "$usedStr / $limitStr used today" else "Tap to set daily limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasLimit && usedMinutes >= limitMinutes) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(if (hasLimit) "Edit" else "Set Limit", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (hasLimit) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (usedMinutes >= limitMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimeLimitDialog(
    app: AppInfo,
    currentMinutes: Int,
    alreadyUsedTodayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var inputMinutesText by remember {
        mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "60")
    }

    val parsedMinutes = inputMinutesText.toIntOrNull() ?: 0
    val isValid = parsedMinutes >= 1

    val liveReadout = remember(parsedMinutes) {
        if (parsedMinutes >= 1) {
            val h = parsedMinutes / 60
            val m = parsedMinutes % 60
            when {
                h > 0 && m > 0 -> "= ${h}h ${m}m"
                h > 0 -> "= ${h}h"
                else -> "= ${m}m"
            }
        } else {
            ""
        }
    }

    val displayStr = remember(parsedMinutes) {
        if (parsedMinutes >= 1) {
            val h = parsedMinutes / 60
            val m = parsedMinutes % 60
            if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
        } else {
            "0m"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconImage(icon = app.icon, modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Daily Screen Time Limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Prior usage today breakdown
                if (alreadyUsedTodayMinutes > 0) {
                    val usedH = alreadyUsedTodayMinutes / 60
                    val usedM = alreadyUsedTodayMinutes % 60
                    val usedDisplay = if (usedH > 0 && usedM > 0) "${usedH}h ${usedM}m" else if (usedH > 0) "${usedH}h" else "${usedM}m"
                    val isExceeded = isValid && alreadyUsedTodayMinutes >= parsedMinutes

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExceeded) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "Already used today: $usedDisplay",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (isValid) {
                                val remaining = parsedMinutes - alreadyUsedTodayMinutes
                                if (remaining > 0) {
                                    val remH = remaining / 60
                                    val remM = remaining % 60
                                    val remDisplay = if (remH > 0 && remM > 0) "${remH}h ${remM}m" else if (remH > 0) "${remH}h" else "${remM}m"
                                    Text(
                                        text = "Remaining today: $remDisplay",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "Exceeds new limit! App will block immediately upon opening.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Numeric input field with live readout
                OutlinedTextField(
                    value = inputMinutesText,
                    onValueChange = { newText ->
                        if (newText.isEmpty() || (newText.all { it.isDigit() } && newText.length <= 5)) {
                            inputMinutesText = newText
                        }
                    },
                    label = { Text("Daily Limit (Minutes)") },
                    placeholder = { Text("e.g. 45 or 120") },
                    suffix = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("min", fontWeight = FontWeight.SemiBold)
                            if (liveReadout.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = liveReadout,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    isError = inputMinutesText.isNotEmpty() && !isValid,
                    supportingText = {
                        if (inputMinutesText.isNotEmpty() && !isValid) {
                            Text("Limit must be at least 1 minute", color = MaterialTheme.colorScheme.error)
                        } else if (inputMinutesText.isEmpty()) {
                            Text("Enter any whole number of minutes (minimum 1)")
                        } else if (liveReadout.isNotEmpty()) {
                            Text("$parsedMinutes minutes $liveReadout", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Enter duration in minutes")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_minute_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onSave(parsedMinutes) },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_limit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isValid) "Save Limit ($displayStr)" else "Save Limit")
                }

                if (currentMinutes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_limit_button")
                    ) {
                        Text("Remove Limit", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
