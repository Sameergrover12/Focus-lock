package com.example.ui.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.AppGroup
import com.example.service.GroupRuleEvaluator
import com.example.ui.common.AppIconImage
import com.example.ui.common.AppPickerDialog
import com.example.ui.common.CheatProtectionAuthDialog
import com.example.ui.common.PendingLooseningAction
import com.example.ui.viewmodel.FocusViewModel
import com.example.ui.viewmodel.GroupWithAppDetails
import com.example.util.SessionLimitHelper
import com.example.util.TimeIntegrityManager

@Composable
fun GroupsScreen(
    viewModel: FocusViewModel
) {
    // 2 Top Tabs: 0: Schedules & Daily Budget, 1: Session Groups
    var selectedTab by remember { mutableIntStateOf(0) }

    val scheduleGroups by viewModel.scheduleAndDailyGroups.collectAsStateWithLifecycle()
    val sessionGroups by viewModel.sessionGroups.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()

    var groupToEditSchedule by remember { mutableStateOf<GroupWithAppDetails?>(null) }
    var groupToEditSession by remember { mutableStateOf<GroupWithAppDetails?>(null) }
    var isCreatingScheduleGroup by remember { mutableStateOf(false) }
    var isCreatingSessionGroup by remember { mutableStateOf(false) }
    var pendingCheatAction by remember { mutableStateOf<PendingLooseningAction?>(null) }

    pendingCheatAction?.let { action ->
        CheatProtectionAuthDialog(
            action = action,
            onVerify = { viewModel.verifyCheatPassphrase(it) },
            onDismiss = { pendingCheatAction = null }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("groups_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        isCreatingScheduleGroup = true
                    } else {
                        isCreatingSessionGroup = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_group_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Group")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("tab_schedules_daily"),
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
                            text = "Schedules & Daily (${scheduleGroups.size})",
                            maxLines = 1,
                            softWrap = false,
                            fontSize = 13.sp,
                            letterSpacing = (-0.2).sp,
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("tab_session_groups"),
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
                            text = "Session Groups (${sessionGroups.size})",
                            maxLines = 1,
                            softWrap = false,
                            fontSize = 13.sp,
                            letterSpacing = (-0.2).sp,
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    // TAB 1: Schedules & Daily Budget Groups
                    item {
                        Text(
                            text = "Schedules & Daily Budget Groups",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Group multiple apps together under scheduled focus windows or a shared daily allowance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (scheduleGroups.isEmpty()) {
                        item {
                            EmptyGroupsCard(
                                title = "No Schedule or Budget Groups",
                                description = "Create a group like 'Social Media' or 'Entertainment' to enforce shared daily budgets or scheduled blackout windows.",
                                buttonLabel = "Create Schedule Group",
                                onClick = { isCreatingScheduleGroup = true }
                            )
                        }
                    }

                    items(scheduleGroups, key = { it.group.id }) { groupDetail ->
                        ScheduleGroupCard(
                            groupDetail = groupDetail,
                            onEdit = { groupToEditSchedule = groupDetail },
                            onDelete = {
                                if (isCheatProtectionEnabled) {
                                    pendingCheatAction = PendingLooseningAction(
                                        title = "Delete Group '${groupDetail.group.name}'",
                                        description = "Deleting this group removes all schedules and time budgets for its member apps.",
                                        onAuthorized = { viewModel.deleteGroup(groupDetail.group.id) }
                                    )
                                } else {
                                    viewModel.deleteGroup(groupDetail.group.id)
                                }
                            }
                        )
                    }
                } else {
                    // TAB 2: Dedicated Session Groups
                    item {
                        Text(
                            text = "Session Groups",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Multiple apps share a continuous session timer. When the session limit is reached, all apps in the group are locked into cooldown together.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (sessionGroups.isEmpty()) {
                        item {
                            EmptyGroupsCard(
                                title = "No Session Groups Configured",
                                description = "Create a session group to enforce a combined session timer (e.g. 15m session, 45m break) across multiple distracting apps.",
                                buttonLabel = "Create Session Group",
                                onClick = { isCreatingSessionGroup = true }
                            )
                        }
                    }

                    items(sessionGroups, key = { it.group.id }) { groupDetail ->
                        SessionGroupCard(
                            groupDetail = groupDetail,
                            onEdit = { groupToEditSession = groupDetail },
                            onDelete = {
                                if (isCheatProtectionEnabled) {
                                    pendingCheatAction = PendingLooseningAction(
                                        title = "Delete Session Group '${groupDetail.group.name}'",
                                        description = "Deleting this session group disables shared session tracking and cooldown protection across its member apps.",
                                        onAuthorized = { viewModel.deleteGroup(groupDetail.group.id) }
                                    )
                                } else {
                                    viewModel.deleteGroup(groupDetail.group.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Schedule & Budget Dialog
    if (isCreatingScheduleGroup || groupToEditSchedule != null) {
        val initialGroup = groupToEditSchedule?.group ?: AppGroup(name = "", sessionLimitEnabled = false)
        val initialPkgs = groupToEditSchedule?.memberPackages?.toSet() ?: emptySet()
        val currentEdit = groupToEditSchedule

        EditScheduleGroupDialog(
            initialGroup = initialGroup,
            initialPackages = initialPkgs,
            installedApps = installedApps,
            onDismiss = {
                isCreatingScheduleGroup = false
                groupToEditSchedule = null
            },
            onSave = { updatedGroup, selectedPkgs ->
                val isLoosening = currentEdit != null && (
                        (currentEdit.group.scheduleEnabled && !updatedGroup.scheduleEnabled) ||
                                (currentEdit.group.budgetEnabled && !updatedGroup.budgetEnabled) ||
                                (updatedGroup.dailyBudgetMinutes > currentEdit.group.dailyBudgetMinutes) ||
                                currentEdit.memberPackages.any { it !in selectedPkgs }
                        )

                if (isLoosening && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Modify Group '${updatedGroup.name}'",
                        description = "Changes will loosen restrictions, increase budget, disable the group, or remove protected member apps.",
                        onAuthorized = {
                            viewModel.saveGroup(updatedGroup, selectedPkgs.toList())
                        }
                    )
                } else {
                    viewModel.saveGroup(updatedGroup, selectedPkgs.toList())
                }
                isCreatingScheduleGroup = false
                groupToEditSchedule = null
            }
        )
    }

    // Dedicated Session Group Dialog
    if (isCreatingSessionGroup || groupToEditSession != null) {
        val initialGroup = groupToEditSession?.group ?: AppGroup(
            name = "",
            scheduleEnabled = false,
            budgetEnabled = false,
            sessionLimitEnabled = true,
            sessionLimitSeconds = 600,
            cooldownSeconds = 3600
        )
        val initialPkgs = groupToEditSession?.memberPackages?.toSet() ?: emptySet()
        val currentEdit = groupToEditSession

        EditSessionGroupDialog(
            initialGroup = initialGroup,
            initialPackages = initialPkgs,
            installedApps = installedApps,
            onDismiss = {
                isCreatingSessionGroup = false
                groupToEditSession = null
            },
            onSave = { updatedGroup, selectedPkgs ->
                val prevSession = currentEdit?.group?.sessionLimitSeconds ?: 0
                val prevCooldown = currentEdit?.group?.cooldownSeconds ?: 0

                val isLoosening = currentEdit != null && (
                        (prevSession > 0 && updatedGroup.sessionLimitSeconds > prevSession) ||
                                (prevCooldown > 0 && updatedGroup.cooldownSeconds < prevCooldown) ||
                                currentEdit.memberPackages.any { it !in selectedPkgs }
                        )

                if (isLoosening && isCheatProtectionEnabled) {
                    pendingCheatAction = PendingLooseningAction(
                        title = "Loosen Session Group '${updatedGroup.name}'",
                        description = "Increasing session time, reducing cooldown, or removing member apps requires passphrase verification.",
                        onAuthorized = {
                            viewModel.saveGroup(updatedGroup, selectedPkgs.toList())
                        }
                    )
                } else {
                    viewModel.saveGroup(updatedGroup, selectedPkgs.toList())
                }
                isCreatingSessionGroup = false
                groupToEditSession = null
            }
        )
    }
}

@Composable
private fun EmptyGroupsCard(
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun ScheduleGroupCard(
    groupDetail: GroupWithAppDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val group = groupDetail.group
    val isScheduleActive = remember(group.scheduleEnabled, group.scheduleStart, group.scheduleEnd, group.daysOfWeek) {
        GroupRuleEvaluator.isScheduleActive(group)
    }
    val isBudgetExhausted = remember(group.budgetEnabled, group.dailyBudgetMinutes, group.usedMinutesToday) {
        GroupRuleEvaluator.isBudgetExhausted(group)
    }
    val isCurrentlyBlocked = isScheduleActive || isBudgetExhausted

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group_card_${group.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyBlocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrentlyBlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "LOCKED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit group")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete group", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Member app icons preview
            MemberAppIconsRow(groupDetail.memberApps)

            Spacer(modifier = Modifier.height(12.dp))

            // Rule 1: Schedule
            if (group.scheduleEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Schedule: ${group.scheduleStart} – ${group.scheduleEnd} (${formatDays(group.daysOfWeek)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isScheduleActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isScheduleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Rule 2: Budget
            if (group.budgetEnabled) {
                val budget = group.dailyBudgetMinutes
                val used = group.usedMinutesToday
                val progress = remember(used, budget) {
                    if (budget > 0) {
                        (used.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                }

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shared Budget: ${used}m / ${budget}m",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isBudgetExhausted) FontWeight.Bold else FontWeight.Normal,
                                color = if (isBudgetExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isBudgetExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionGroupCard(
    groupDetail: GroupWithAppDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val group = groupDetail.group
    val isCooldownActive = remember(group.cooldownEndTimestamp) {
        group.cooldownSeconds > 0 && group.cooldownEndTimestamp > TimeIntegrityManager.getTrueTimeMillis()
    }
    val remainingCooldownMs = remember(group.cooldownEndTimestamp) {
        SessionLimitHelper.getCooldownRemainingMs(group.cooldownEndTimestamp)
    }

    val sessionProgress = remember(group.sessionLimitSeconds, group.currentSessionUsedMs) {
        if (group.sessionLimitSeconds > 0) {
            (group.currentSessionUsedMs.toFloat() / (group.sessionLimitSeconds * 1000f)).coerceIn(0f, 1f)
        } else 0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_group_card_${group.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCooldownActive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCooldownActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = if (isCooldownActive) "ON COOLDOWN" else if (group.currentSessionUsedMs > 0L) "IN SESSION" else "SESSION GROUP",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCooldownActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit session group")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete session group", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Member app icons preview
            MemberAppIconsRow(groupDetail.memberApps)

            Spacer(modifier = Modifier.height(14.dp))

            // Session & Cooldown Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = if (isCooldownActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCooldownActive) {
                        "Break active: ${SessionLimitHelper.formatRemaining(remainingCooldownMs)} remaining"
                    } else if (group.currentSessionUsedMs > 0L) {
                        "Session active: ${SessionLimitHelper.formatHms((group.currentSessionUsedMs / 1000).toInt())} used / ${SessionLimitHelper.formatHms(group.sessionLimitSeconds)} allowed"
                    } else {
                        "Session: ${SessionLimitHelper.formatHms(group.sessionLimitSeconds)} • Cooldown: ${SessionLimitHelper.formatHms(group.cooldownSeconds)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCooldownActive || group.currentSessionUsedMs > 0L) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCooldownActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }

            if (group.currentSessionUsedMs > 0L && !isCooldownActive) {
                Spacer(modifier = Modifier.height(8.dp))
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
private fun MemberAppIconsRow(memberApps: List<com.example.data.model.AppInfo>) {
    if (memberApps.isEmpty()) return
    Spacer(modifier = Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val maxVisibleIcons = 5
        val displayApps = memberApps.take(maxVisibleIcons)
        val remainingCount = memberApps.size - displayApps.size
        val iconOverlapStep = 22.dp
        val iconSize = 32.dp
        val totalStackWidth = iconSize + (iconOverlapStep * (displayApps.size - 1))

        Box(
            modifier = Modifier
                .width(totalStackWidth)
                .height(iconSize)
        ) {
            displayApps.forEachIndexed { index, app ->
                Box(
                    modifier = Modifier
                        .padding(start = (index * 22).dp)
                        .size(iconSize)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    AppIconImage(icon = app.icon, packageName = app.packageName, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (remainingCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.height(24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "+$remainingCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatDays(days: Set<Int>): String {
    val dayNames = mapOf(
        2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun"
    )
    if (days.size == 7) return "Every day"
    if (days == setOf(2, 3, 4, 5, 6)) return "Mon–Fri"
    if (days == setOf(1, 7)) return "Weekends"
    return days.sorted().mapNotNull { dayNames[it] }.joinToString(", ")
}

@Composable
private fun EditScheduleGroupDialog(
    initialGroup: AppGroup,
    initialPackages: Set<String>,
    installedApps: List<com.example.data.model.AppInfo>,
    onDismiss: () -> Unit,
    onSave: (AppGroup, Set<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialGroup.name) }
    var selectedPackages by remember { mutableStateOf(initialPackages) }
    var showAppPicker by remember { mutableStateOf(false) }

    var scheduleEnabled by remember { mutableStateOf(initialGroup.scheduleEnabled) }
    var scheduleStart by remember { mutableStateOf(if (initialGroup.scheduleStart.isNotBlank()) initialGroup.scheduleStart else "09:00") }
    var scheduleEnd by remember { mutableStateOf(if (initialGroup.scheduleEnd.isNotBlank()) initialGroup.scheduleEnd else "17:00") }
    var daysOfWeek by remember { mutableStateOf(if (initialGroup.daysOfWeek.isNotEmpty()) initialGroup.daysOfWeek else setOf(2, 3, 4, 5, 6)) }

    var budgetEnabled by remember { mutableStateOf(initialGroup.budgetEnabled) }
    var dailyBudgetMinutes by remember { mutableIntStateOf(if (initialGroup.dailyBudgetMinutes > 0) initialGroup.dailyBudgetMinutes else 60) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .testTag("edit_schedule_group_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (initialGroup.id == 0L) "Create Schedule / Budget Group" else "Edit Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Group Name (e.g. Social Media)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Included Apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${selectedPackages.size} apps selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { showAppPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("pick_group_apps_button")
                        ) {
                            Text("Select Apps")
                        }
                    }
                }

                // Schedule Rule
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Schedule Rule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Block apps inside a specific time window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = scheduleEnabled,
                                    onCheckedChange = { scheduleEnabled = it; errorMessage = null },
                                    modifier = Modifier.testTag("schedule_rule_switch")
                                )
                            }

                            AnimatedVisibility(visible = scheduleEnabled) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = scheduleStart,
                                            onValueChange = { scheduleStart = it },
                                            label = { Text("Start (HH:mm)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = scheduleEnd,
                                            onValueChange = { scheduleEnd = it },
                                            label = { Text("End (HH:mm)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Days of week:", style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val days = listOf(2 to "M", 3 to "T", 4 to "W", 5 to "T", 6 to "F", 7 to "S", 1 to "S")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        days.forEach { (dayInt, label) ->
                                            val isSel = daysOfWeek.contains(dayInt)
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    daysOfWeek = if (isSel) daysOfWeek - dayInt else daysOfWeek + dayInt
                                                },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Budget Rule
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Time Budget Rule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Shared daily limit for all apps in group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = budgetEnabled,
                                    onCheckedChange = { budgetEnabled = it; errorMessage = null },
                                    modifier = Modifier.testTag("budget_rule_switch")
                                )
                            }

                            AnimatedVisibility(visible = budgetEnabled) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    val h = dailyBudgetMinutes / 60
                                    val m = dailyBudgetMinutes % 60
                                    val budgetStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                                    Text(
                                        text = "Daily Allowance: $budgetStr",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(30, 45, 60, 90, 120).forEach { mins ->
                                            FilterChip(
                                                selected = dailyBudgetMinutes == mins,
                                                onClick = { dailyBudgetMinutes = mins },
                                                label = { Text(if (mins >= 60) "${mins / 60}h" else "${mins}m") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Please enter a group name"
                                return@Button
                            }
                            if (!scheduleEnabled && !budgetEnabled) {
                                errorMessage = "Enable at least one rule (Schedule or Time Budget)"
                                return@Button
                            }
                            if (selectedPackages.isEmpty()) {
                                errorMessage = "Please select at least one app for this group"
                                return@Button
                            }

                            val updated = initialGroup.copy(
                                name = name.trim(),
                                scheduleEnabled = scheduleEnabled,
                                scheduleStart = scheduleStart.trim(),
                                scheduleEnd = scheduleEnd.trim(),
                                daysOfWeek = daysOfWeek,
                                budgetEnabled = budgetEnabled,
                                dailyBudgetMinutes = dailyBudgetMinutes,
                                sessionLimitEnabled = false
                            )
                            onSave(updated, selectedPackages)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_group_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Group")
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            title = "Select Group Apps",
            availableApps = installedApps,
            initialSelectedPackages = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { pkgs ->
                selectedPackages = pkgs
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun EditSessionGroupDialog(
    initialGroup: AppGroup,
    initialPackages: Set<String>,
    installedApps: List<com.example.data.model.AppInfo>,
    onDismiss: () -> Unit,
    onSave: (AppGroup, Set<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialGroup.name) }
    var selectedPackages by remember { mutableStateOf(initialPackages) }
    var showAppPicker by remember { mutableStateOf(false) }

    var sessionHours by remember { mutableIntStateOf(initialGroup.sessionLimitSeconds / 3600) }
    var sessionMins by remember { mutableIntStateOf((initialGroup.sessionLimitSeconds % 3600) / 60) }
    var sessionSecs by remember { mutableIntStateOf(initialGroup.sessionLimitSeconds % 60) }

    var cooldownHours by remember { mutableIntStateOf(initialGroup.cooldownSeconds / 3600) }
    var cooldownMins by remember { mutableIntStateOf((initialGroup.cooldownSeconds % 3600) / 60) }
    var cooldownSecs by remember { mutableIntStateOf(initialGroup.cooldownSeconds % 60) }

    if (initialGroup.sessionLimitSeconds == 0 && sessionHours == 0 && sessionMins == 0 && sessionSecs == 0) {
        sessionMins = 10
    }
    if (initialGroup.cooldownSeconds == 0 && cooldownHours == 0 && cooldownMins == 0 && cooldownSecs == 0) {
        cooldownHours = 1
    }

    val totalSessionSeconds = SessionLimitHelper.toSeconds(sessionHours, sessionMins, sessionSecs)
    val totalCooldownSeconds = SessionLimitHelper.toSeconds(cooldownHours, cooldownMins, cooldownSecs)
    val isValid = totalSessionSeconds > 0 && totalCooldownSeconds > 0 && name.isNotBlank() && selectedPackages.isNotEmpty()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .testTag("edit_session_group_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (initialGroup.id == 0L) "Create Session Group" else "Edit Session Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Shared session limit and break cooldown across multiple apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Group Name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Group Name (e.g. Social Apps)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_group_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Member Apps
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Included Apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${selectedPackages.size} apps selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { showAppPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("pick_session_group_apps_button")
                        ) {
                            Text("Select Apps")
                        }
                    }
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
                                text = "Session Duration (Max time across all member apps):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Using any app in this group consumes this shared session timer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GroupHmsInputRow(
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
                                text = "Cooldown Break Duration (Lockout period for all member apps):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All member apps are locked until this break period elapses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GroupHmsInputRow(
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

                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Please enter a group name"
                                return@Button
                            }
                            if (selectedPackages.isEmpty()) {
                                errorMessage = "Please select at least one app for this session group"
                                return@Button
                            }
                            if (totalSessionSeconds <= 0 || totalCooldownSeconds <= 0) {
                                errorMessage = "Both session and cooldown must be greater than 0 seconds"
                                return@Button
                            }

                            val updated = initialGroup.copy(
                                name = name.trim(),
                                scheduleEnabled = false,
                                budgetEnabled = false,
                                sessionLimitEnabled = true,
                                sessionLimitSeconds = totalSessionSeconds,
                                cooldownSeconds = totalCooldownSeconds
                            )
                            onSave(updated, selectedPackages)
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_session_group_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Session Group")
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            title = "Select Session Group Apps",
            availableApps = installedApps,
            initialSelectedPackages = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { pkgs ->
                selectedPackages = pkgs
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun GroupHmsInputRow(
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
