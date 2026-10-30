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
    val todayDateStr = FocusRepository.getTodayDateString()

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
                        limitEntity = limitEntity,
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
            currentSessionLimitSeconds = currentLimit?.sessionLimitSeconds ?: 0,
            currentCooldownSeconds = currentLimit?.cooldownSeconds ?: 0,
            alreadyUsedTodayMinutes = usedToday,
            onDismiss = { appToSetLimit = null },
            onSave = { dailyMinutes, sessionSeconds, cooldownSeconds ->
                val prevDaily = currentLimit?.dailyLimitMinutes ?: 0
                val prevSession = currentLimit?.sessionLimitSeconds ?: 0
                val prevCooldown = currentLimit?.cooldownSeconds ?: 0

                val isLoosening = (prevDaily > 0 && dailyMinutes > prevDaily) ||
                    (prevSession > 0 && sessionSeconds == 0) ||
                    (prevSession > 0 && sessionSeconds > prevSession) ||
                    (prevCooldown > 0 && cooldownSeconds < prevCooldown)

                if (isLoosening && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Loosen Limit for ${targetApp.appName}",
                        description = "Increasing session time, daily allowance, or reducing break cooldown requires passphrase verification.",
                        onAuthorized = { viewModel.setAppScreenLimit(targetApp.packageName, dailyMinutes, sessionSeconds, cooldownSeconds) }
                    )
                } else {
                    viewModel.setAppScreenLimit(targetApp.packageName, dailyMinutes, sessionSeconds, cooldownSeconds)
                }
                appToSetLimit = null
            },
            onRemove = {
                val prevDaily = currentLimit?.dailyLimitMinutes ?: 0
                val prevSession = currentLimit?.sessionLimitSeconds ?: 0
                if ((prevDaily > 0 || prevSession > 0) && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Remove Limits for ${targetApp.appName}",
                        description = "Removing time limits allows unrestricted access to ${targetApp.appName}.",
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
        shape = RoundedCornerShape(16.dp),
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
                    text = if (isBlocked) "Locked — Opening blocked" else "Unrestricted",
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
    limitEntity: com.example.data.local.entity.ScreenTimeLimit?,
    usedMinutes: Int,
    onConfigure: () -> Unit
) {
    val limitMinutes = limitEntity?.dailyLimitMinutes ?: 0
    val sessionLimitSeconds = limitEntity?.sessionLimitSeconds ?: 0
    val cooldownSeconds = limitEntity?.cooldownSeconds ?: 0
    val cooldownEndTimestamp = limitEntity?.cooldownEndTimestamp ?: 0L

    val isCooldownActive = remember(cooldownEndTimestamp) {
        com.example.util.SessionLimitHelper.isCooldownActive(cooldownEndTimestamp)
    }
    val remainingCooldownMs = remember(cooldownEndTimestamp) {
        com.example.util.SessionLimitHelper.getCooldownRemainingMs(cooldownEndTimestamp)
    }

    val hasDailyLimit = limitMinutes > 0
    val hasSessionLimit = sessionLimitSeconds > 0
    val hasAnyLimit = hasDailyLimit || hasSessionLimit

    val progress = remember(limitMinutes, usedMinutes) {
        if (hasDailyLimit) {
            (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    val subtitleText = when {
        isCooldownActive -> "On Cooldown (${com.example.util.SessionLimitHelper.formatRemaining(remainingCooldownMs)} remaining)"
        hasDailyLimit && hasSessionLimit -> {
            val dailyStr = if (limitMinutes >= 60) "${limitMinutes / 60}h ${limitMinutes % 60}m" else "${limitMinutes}m"
            val sessionStr = com.example.util.SessionLimitHelper.formatHms(sessionLimitSeconds)
            val cooldownStr = com.example.util.SessionLimitHelper.formatHms(cooldownSeconds)
            "Daily: $dailyStr • Session: $sessionStr (Cooldown: $cooldownStr)"
        }
        hasDailyLimit -> {
            val h = limitMinutes / 60
            val m = limitMinutes % 60
            val lStr = if (h > 0) "${h}h ${m}m" else "${m}m"
            val uH = usedMinutes / 60
            val uM = usedMinutes % 60
            val uStr = if (uH > 0) "${uH}h ${uM}m" else "${uM}m"
            "$uStr / $lStr used today"
        }
        hasSessionLimit -> {
            val sessionStr = com.example.util.SessionLimitHelper.formatHms(sessionLimitSeconds)
            val cooldownStr = com.example.util.SessionLimitHelper.formatHms(cooldownSeconds)
            "Session: $sessionStr (Cooldown: $cooldownStr)"
        }
        else -> "Tap to set daily or session limits"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
            .testTag("app_limit_row_${app.packageName}"),
        shape = RoundedCornerShape(16.dp),
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
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isCooldownActive -> MaterialTheme.colorScheme.error
                            hasDailyLimit && usedMinutes >= limitMinutes -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(if (hasAnyLimit) "Edit" else "Set Limit", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (hasDailyLimit) {
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
private fun HmsInputRow(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = if (hours > 0) hours.toString() else "",
            onValueChange = { str ->
                val v = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                onHoursChange(v.coerceIn(0, 23))
            },
            label = { Text("Hours") },
            placeholder = { Text("0") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(10.dp)
        )
        OutlinedTextField(
            value = if (minutes > 0) minutes.toString() else "",
            onValueChange = { str ->
                val v = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                onMinutesChange(v.coerceIn(0, 59))
            },
            label = { Text("Mins") },
            placeholder = { Text("0") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(10.dp)
        )
        OutlinedTextField(
            value = if (seconds > 0) seconds.toString() else "",
            onValueChange = { str ->
                val v = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                onSecondsChange(v.coerceIn(0, 59))
            },
            label = { Text("Secs") },
            placeholder = { Text("0") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(10.dp)
        )
    }
}

@Composable
private fun TimeLimitDialog(
    app: AppInfo,
    currentMinutes: Int,
    currentSessionLimitSeconds: Int = 0,
    currentCooldownSeconds: Int = 0,
    alreadyUsedTodayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (dailyMinutes: Int, sessionSeconds: Int, cooldownSeconds: Int) -> Unit,
    onRemove: () -> Unit
) {
    var dailyLimitEnabled by remember {
        mutableStateOf(currentMinutes > 0)
    }
    var inputMinutesText by remember {
        mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "60")
    }

    var sessionLimitEnabled by remember {
        mutableStateOf(currentSessionLimitSeconds > 0)
    }

    // Session duration H/M/S
    var sessionHours by remember { mutableIntStateOf(currentSessionLimitSeconds / 3600) }
    var sessionMins by remember { mutableIntStateOf((currentSessionLimitSeconds % 3600) / 60) }
    var sessionSecs by remember { mutableIntStateOf(currentSessionLimitSeconds % 60) }

    // Cooldown duration H/M/S
    var cooldownHours by remember { mutableIntStateOf(currentCooldownSeconds / 3600) }
    var cooldownMins by remember { mutableIntStateOf((currentCooldownSeconds % 3600) / 60) }
    var cooldownSecs by remember { mutableIntStateOf(currentCooldownSeconds % 60) }

    // Default reasonable values if toggled on from 0
    if (sessionLimitEnabled && sessionHours == 0 && sessionMins == 0 && sessionSecs == 0) {
        sessionMins = 10
    }
    if (sessionLimitEnabled && cooldownHours == 0 && cooldownMins == 0 && cooldownSecs == 0) {
        cooldownHours = 1
    }

    val totalSessionSeconds = com.example.util.SessionLimitHelper.toSeconds(sessionHours, sessionMins, sessionSecs)
    val totalCooldownSeconds = com.example.util.SessionLimitHelper.toSeconds(cooldownHours, cooldownMins, cooldownSecs)

    val parsedDailyMinutes = if (dailyLimitEnabled) (inputMinutesText.toIntOrNull() ?: 0) else 0
    val isDailyValid = !dailyLimitEnabled || parsedDailyMinutes >= 1
    val isSessionValid = !sessionLimitEnabled || (totalSessionSeconds > 0 && totalCooldownSeconds > 0)
    val hasAtLeastOneRule = (dailyLimitEnabled && parsedDailyMinutes >= 1) || (sessionLimitEnabled && totalSessionSeconds > 0 && totalCooldownSeconds > 0)
    val isValid = isDailyValid && isSessionValid && hasAtLeastOneRule

    val liveDailyReadout = remember(parsedDailyMinutes) {
        if (parsedDailyMinutes >= 1) {
            val h = parsedDailyMinutes / 60
            val m = parsedDailyMinutes % 60
            when {
                h > 0 && m > 0 -> "= ${h}h ${m}m"
                h > 0 -> "= ${h}h"
                else -> "= ${m}m"
            }
        } else {
            ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIconImage(icon = app.icon, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Screen Time & Session Limits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 1. Daily Limit Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Daily Screen Time Limit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Total usage allowed per 24-hour day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = dailyLimitEnabled,
                                    onCheckedChange = { dailyLimitEnabled = it }
                                )
                            }

                            AnimatedVisibility(visible = dailyLimitEnabled) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    OutlinedTextField(
                                        value = inputMinutesText,
                                        onValueChange = { newText ->
                                            if (newText.isEmpty() || (newText.all { it.isDigit() } && newText.length <= 5)) {
                                                inputMinutesText = newText
                                            }
                                        },
                                        label = { Text("Daily Allowance (Minutes)") },
                                        placeholder = { Text("e.g. 45 or 120") },
                                        suffix = {
                                            if (liveDailyReadout.isNotEmpty()) {
                                                Text(liveDailyReadout, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            } else {
                                                Text("min")
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_minute_input"),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Session Limit & Cooldown Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Session Limit & Cooldown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Locks app after continuous use until break ends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = sessionLimitEnabled,
                                    onCheckedChange = { sessionLimitEnabled = it },
                                    modifier = Modifier.testTag("session_limit_switch")
                                )
                            }

                            AnimatedVisibility(visible = sessionLimitEnabled) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Text(
                                        text = "Session Duration (Max time per session):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HmsInputRow(
                                        hours = sessionHours,
                                        minutes = sessionMins,
                                        seconds = sessionSecs,
                                        onHoursChange = { sessionHours = it },
                                        onMinutesChange = { sessionMins = it },
                                        onSecondsChange = { sessionSecs = it }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Cooldown Break Duration (Lockout period):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HmsInputRow(
                                        hours = cooldownHours,
                                        minutes = cooldownMins,
                                        seconds = cooldownSecs,
                                        onHoursChange = { cooldownHours = it },
                                        onMinutesChange = { cooldownMins = it },
                                        onSecondsChange = { cooldownSecs = it }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "Allowed: ${com.example.util.SessionLimitHelper.formatHms(totalSessionSeconds)} • Break: ${com.example.util.SessionLimitHelper.formatHms(totalCooldownSeconds)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            val dMin = if (dailyLimitEnabled) parsedDailyMinutes else 0
                            val sSec = if (sessionLimitEnabled) totalSessionSeconds else 0
                            val cSec = if (sessionLimitEnabled) totalCooldownSeconds else 0
                            onSave(dMin, sSec, cSec)
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_limit_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Limits")
                    }
                }

                if (currentMinutes > 0 || currentSessionLimitSeconds > 0) {
                    item {
                        TextButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("remove_limit_button")
                        ) {
                            Text("Remove Limits", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
