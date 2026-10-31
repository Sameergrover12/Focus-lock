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
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppInfo
import com.example.data.repository.FocusRepository
import com.example.ui.common.AppIconImage
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.PendingLooseningAction
import com.example.ui.viewmodel.FocusViewModel
import com.example.util.SessionLimitHelper

@Composable
fun AppsScreen(
    viewModel: FocusViewModel
) {
    // 3 Tabs: 0: Hard Block, 1: Daily Screen Time, 2: Session Limits
    var selectedSubTab by remember { mutableIntStateOf(0) }

    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val screenLimits by viewModel.screenTimeLimits.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()
    val todayUsageLogs by viewModel.todayUsageLogs.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var appToSetDailyLimit by remember { mutableStateOf<AppInfo?>(null) }
    var appToSetSessionLimit by remember { mutableStateOf<AppInfo?>(null) }
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

    val dailyLimitsCount = remember(screenLimits) {
        screenLimits.count { it.dailyLimitMinutes > 0 }
    }
    val sessionLimitsCount = remember(screenLimits) {
        screenLimits.count { it.sessionLimitSeconds > 0 }
    }

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
                modifier = Modifier.testTag("tab_hard_block"),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hard Block (${blockedApps.size})",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (selectedSubTab == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                modifier = Modifier.testTag("tab_screen_time"),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassBottom,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Screen Time ($dailyLimitsCount)",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (selectedSubTab == 1) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                modifier = Modifier.testTag("tab_session_limits"),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Session ($sessionLimitsCount)",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (selectedSubTab == 2) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
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
                    Text(
                        when (selectedSubTab) {
                            0 -> "Search apps to block..."
                            1 -> "Search apps for daily limits..."
                            else -> "Search apps for session limits..."
                        }
                    )
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
                when (selectedSubTab) {
                    0 -> {
                        // Tab 1: Hard Block Toggle
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
                    }
                    1 -> {
                        // Tab 2: Daily Screen Time Limit Row
                        val limitEntity = limitsMap[app.packageName]
                        val usageLog = usageLogsMap[app.packageName]
                        val actualUsedMinutes = usageLog?.minutesUsed
                            ?: (if (limitEntity?.lastResetDate == todayDateStr) limitEntity.usedMinutesToday else 0)
                        AppScreenLimitRow(
                            app = app,
                            limitEntity = limitEntity,
                            usedMinutes = actualUsedMinutes,
                            onConfigure = {
                                appToSetDailyLimit = app
                            }
                        )
                    }
                    2 -> {
                        // Tab 3: Dedicated Session Limit & Cooldown Row
                        val limitEntity = limitsMap[app.packageName]
                        AppSessionLimitRow(
                            app = app,
                            limitEntity = limitEntity,
                            onConfigure = {
                                appToSetSessionLimit = app
                            }
                        )
                    }
                }
            }
        }
    }

    // Daily Limit Dialog (Tab 2)
    if (appToSetDailyLimit != null) {
        val targetApp = appToSetDailyLimit!!
        val currentLimit = screenLimits.firstOrNull { it.packageName == targetApp.packageName }
        val targetUsageLog = todayUsageLogs.firstOrNull { it.packageName == targetApp.packageName }
        val usedToday = targetUsageLog?.minutesUsed
            ?: (if (currentLimit?.lastResetDate == FocusRepository.getTodayDateString()) currentLimit.usedMinutesToday else 0)

        DailyLimitDialog(
            app = targetApp,
            currentMinutes = currentLimit?.dailyLimitMinutes ?: 0,
            alreadyUsedTodayMinutes = usedToday,
            onDismiss = { appToSetDailyLimit = null },
            onSave = { newDailyMinutes ->
                val prevDaily = currentLimit?.dailyLimitMinutes ?: 0
                val isLoosening = prevDaily > 0 && newDailyMinutes > prevDaily

                if (isLoosening && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Increase Daily Limit for ${targetApp.appName}",
                        description = "Increasing daily allowance from ${prevDaily}m to ${newDailyMinutes}m requires passphrase verification.",
                        onAuthorized = { viewModel.setDailyLimit(targetApp.packageName, newDailyMinutes) }
                    )
                } else {
                    viewModel.setDailyLimit(targetApp.packageName, newDailyMinutes)
                }
                appToSetDailyLimit = null
            },
            onRemove = {
                val prevDaily = currentLimit?.dailyLimitMinutes ?: 0
                if (prevDaily > 0 && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Remove Daily Limit for ${targetApp.appName}",
                        description = "Removing daily limit allows unrestricted daily usage for ${targetApp.appName}.",
                        onAuthorized = { viewModel.removeDailyLimit(targetApp.packageName) }
                    )
                } else {
                    viewModel.removeDailyLimit(targetApp.packageName)
                }
                appToSetDailyLimit = null
            }
        )
    }

    // Dedicated Session Limit & Cooldown Dialog (Tab 3)
    if (appToSetSessionLimit != null) {
        val targetApp = appToSetSessionLimit!!
        val currentLimit = screenLimits.firstOrNull { it.packageName == targetApp.packageName }

        SessionLimitDialog(
            app = targetApp,
            currentSessionLimitSeconds = currentLimit?.sessionLimitSeconds ?: 0,
            currentCooldownSeconds = currentLimit?.cooldownSeconds ?: 0,
            onDismiss = { appToSetSessionLimit = null },
            onSave = { sessionSeconds, cooldownSeconds ->
                val prevSession = currentLimit?.sessionLimitSeconds ?: 0
                val prevCooldown = currentLimit?.cooldownSeconds ?: 0

                val isLoosening = (prevSession > 0 && sessionSeconds > prevSession) ||
                        (prevCooldown > 0 && cooldownSeconds < prevCooldown)

                if (isLoosening && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Loosen Session Limit for ${targetApp.appName}",
                        description = "Increasing session time or reducing break cooldown requires passphrase verification.",
                        onAuthorized = { viewModel.setSessionLimit(targetApp.packageName, sessionSeconds, cooldownSeconds) }
                    )
                } else {
                    viewModel.setSessionLimit(targetApp.packageName, sessionSeconds, cooldownSeconds)
                }
                appToSetSessionLimit = null
            },
            onRemove = {
                val prevSession = currentLimit?.sessionLimitSeconds ?: 0
                if (prevSession > 0 && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Remove Session Limit for ${targetApp.appName}",
                        description = "Removing session limit allows unrestricted continuous usage without cooldown breaks.",
                        onAuthorized = { viewModel.removeSessionLimit(targetApp.packageName) }
                    )
                } else {
                    viewModel.removeSessionLimit(targetApp.packageName)
                }
                appToSetSessionLimit = null
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
    val hasDailyLimit = limitMinutes > 0

    val progress = remember(limitMinutes, usedMinutes) {
        if (hasDailyLimit) {
            (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    val subtitleText = if (hasDailyLimit) {
        val h = limitMinutes / 60
        val m = limitMinutes % 60
        val lStr = if (h > 0) "${h}h ${m}m" else "${m}m"
        val uH = usedMinutes / 60
        val uM = usedMinutes % 60
        val uStr = if (uH > 0) "${uH}h ${uM}m" else "${uM}m"
        "$uStr / $lStr used today"
    } else {
        "Tap to set daily limit"
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
                        color = if (hasDailyLimit && usedMinutes >= limitMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("set_daily_limit_button_${app.packageName}")
                ) {
                    Text(if (hasDailyLimit) "Edit" else "Set Limit", style = MaterialTheme.typography.labelMedium)
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
private fun AppSessionLimitRow(
    app: AppInfo,
    limitEntity: com.example.data.local.entity.ScreenTimeLimit?,
    onConfigure: () -> Unit
) {
    val sessionLimitSeconds = limitEntity?.sessionLimitSeconds ?: 0
    val cooldownSeconds = limitEntity?.cooldownSeconds ?: 0
    val cooldownEndTimestamp = limitEntity?.cooldownEndTimestamp ?: 0L
    val currentSessionUsedMs = limitEntity?.currentSessionUsedMs ?: 0L

    val hasSessionLimit = sessionLimitSeconds > 0
    val isCooldownActive = remember(cooldownEndTimestamp) {
        SessionLimitHelper.isCooldownActive(cooldownEndTimestamp)
    }
    val remainingCooldownMs = remember(cooldownEndTimestamp) {
        SessionLimitHelper.getCooldownRemainingMs(cooldownEndTimestamp)
    }

    val sessionProgress = remember(sessionLimitSeconds, currentSessionUsedMs) {
        if (sessionLimitSeconds > 0) {
            (currentSessionUsedMs.toFloat() / (sessionLimitSeconds * 1000f)).coerceIn(0f, 1f)
        } else 0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigure() }
            .testTag("app_session_row_${app.packageName}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCooldownActive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
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
                        text = when {
                            !hasSessionLimit -> "No session limit configured"
                            isCooldownActive -> "On Cooldown (${SessionLimitHelper.formatRemaining(remainingCooldownMs)} left)"
                            currentSessionUsedMs > 0L -> "Session in progress (${SessionLimitHelper.formatHms((currentSessionUsedMs / 1000).toInt())} used)"
                            else -> "Session: ${SessionLimitHelper.formatHms(sessionLimitSeconds)} • Break: ${SessionLimitHelper.formatHms(cooldownSeconds)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isCooldownActive -> MaterialTheme.colorScheme.error
                            currentSessionUsedMs > 0L -> MaterialTheme.colorScheme.primary
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
                    modifier = Modifier
                        .height(34.dp)
                        .testTag(if (hasSessionLimit) "edit_session_button_${app.packageName}" else "set_session_button_${app.packageName}")
                ) {
                    Text(if (hasSessionLimit) "Edit" else "Set Session", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (hasSessionLimit && currentSessionUsedMs > 0L && !isCooldownActive) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { sessionProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
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
private fun DailyLimitDialog(
    app: AppInfo,
    currentMinutes: Int,
    alreadyUsedTodayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (dailyMinutes: Int) -> Unit,
    onRemove: () -> Unit
) {
    var inputMinutesText by remember {
        mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "60")
    }

    val parsedDailyMinutes = inputMinutesText.toIntOrNull() ?: 0
    val isValid = parsedDailyMinutes >= 1

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppIconImage(icon = app.icon, modifier = Modifier.size(52.dp))
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

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15, 30, 45, 60, 120).forEach { mins ->
                        FilterChip(
                            selected = parsedDailyMinutes == mins,
                            onClick = { inputMinutesText = mins.toString() },
                            label = { Text(if (mins >= 60) "${mins / 60}h" else "${mins}m") }
                        )
                    }
                }

                Button(
                    onClick = { onSave(parsedDailyMinutes) },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_limit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Daily Limit")
                }

                if (currentMinutes > 0) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_limit_button")
                    ) {
                        Text("Remove Daily Limit", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionLimitDialog(
    app: AppInfo,
    currentSessionLimitSeconds: Int,
    currentCooldownSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (sessionSeconds: Int, cooldownSeconds: Int) -> Unit,
    onRemove: () -> Unit
) {
    var sessionHours by remember { mutableIntStateOf(currentSessionLimitSeconds / 3600) }
    var sessionMins by remember { mutableIntStateOf((currentSessionLimitSeconds % 3600) / 60) }
    var sessionSecs by remember { mutableIntStateOf(currentSessionLimitSeconds % 60) }

    var cooldownHours by remember { mutableIntStateOf(currentCooldownSeconds / 3600) }
    var cooldownMins by remember { mutableIntStateOf((currentCooldownSeconds % 3600) / 60) }
    var cooldownSecs by remember { mutableIntStateOf(currentCooldownSeconds % 60) }

    if (currentSessionLimitSeconds == 0 && sessionHours == 0 && sessionMins == 0 && sessionSecs == 0) {
        sessionMins = 10
    }
    if (currentCooldownSeconds == 0 && cooldownHours == 0 && cooldownMins == 0 && cooldownSecs == 0) {
        cooldownHours = 1
    }

    val totalSessionSeconds = SessionLimitHelper.toSeconds(sessionHours, sessionMins, sessionSecs)
    val totalCooldownSeconds = SessionLimitHelper.toSeconds(cooldownHours, cooldownMins, cooldownSecs)
    val isValid = totalSessionSeconds > 0 && totalCooldownSeconds > 0

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
                    AppIconImage(icon = app.icon, modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Session Time Limit & Cooldown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Session Duration
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Session Duration (Continuous use limit):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Locks app after this much use before requiring a break",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HmsInputRow(
                                hours = sessionHours,
                                minutes = sessionMins,
                                seconds = sessionSecs,
                                onHoursChange = { sessionHours = it },
                                onMinutesChange = { sessionMins = it },
                                onSecondsChange = { sessionSecs = it }
                            )
                        }
                    }
                }

                // Cooldown Duration
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Cooldown Break Duration (Lockout period):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "App remains blocked until this break period elapses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HmsInputRow(
                                hours = cooldownHours,
                                minutes = cooldownMins,
                                seconds = cooldownSecs,
                                onHoursChange = { cooldownHours = it },
                                onMinutesChange = { cooldownMins = it },
                                onSecondsChange = { cooldownSecs = it }
                            )
                        }
                    }
                }

                // Preview Summary
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Allowed: ${SessionLimitHelper.formatHms(totalSessionSeconds)} • Break: ${SessionLimitHelper.formatHms(totalCooldownSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                item {
                    Button(
                        onClick = { onSave(totalSessionSeconds, totalCooldownSeconds) },
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_session_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Session Rule")
                    }
                }

                if (currentSessionLimitSeconds > 0) {
                    item {
                        TextButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("remove_session_button")
                        ) {
                            Text("Remove Session Rule", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
