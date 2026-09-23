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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
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

@Composable
fun GroupsScreen(
    viewModel: FocusViewModel
) {
    val groupsWithDetails by viewModel.groupsWithDetails.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isCheatProtectionEnabled by viewModel.isCheatProtectionEnabled.collectAsStateWithLifecycle()

    var groupToEdit by remember { mutableStateOf<GroupWithAppDetails?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreatingNew = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_group_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Group")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "App Groups",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Combine multiple apps under scheduled focus windows or shared daily time budgets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (groupsWithDetails.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
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
                                text = "No Groups Configured",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create a group like 'Social Media' or 'Games' to enforce shared limits or schedules across multiple apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { isCreatingNew = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Create First Group")
                            }
                        }
                    }
                }
            }

            items(groupsWithDetails, key = { it.group.id }) { groupDetail ->
                GroupCard(
                    groupDetail = groupDetail,
                    onEdit = { groupToEdit = groupDetail },
                    onDelete = {
                        if (isCheatProtectionEnabled) {
                            pendingCheatAction = PendingLooseningAction(
                                title = "Delete Group '${groupDetail.group.name}'",
                                description = "Deleting this group removes all schedules, time budgets, and protections for its member apps.",
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

    if (isCreatingNew || groupToEdit != null) {
        val initialGroup = groupToEdit?.group ?: AppGroup(name = "")
        val initialPkgs = groupToEdit?.memberPackages?.toSet() ?: emptySet()
        val currentEdit = groupToEdit

        EditGroupDialog(
            initialGroup = initialGroup,
            initialPackages = initialPkgs,
            installedApps = installedApps,
            onDismiss = {
                isCreatingNew = false
                groupToEdit = null
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
                isCreatingNew = false
                groupToEdit = null
            }
        )
    }
}

@Composable
private fun GroupCard(
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
            // Title and action buttons
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

            // Member app icons preview (avatar-stack style)
            if (groupDetail.memberApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val maxVisibleIcons = 4
                    val displayApps = groupDetail.memberApps.take(maxVisibleIcons)
                    val remainingCount = groupDetail.memberApps.size - displayApps.size
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
                                AppIconImage(icon = app.icon, modifier = Modifier.fillMaxSize())
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
private fun EditGroupDialog(
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
    var scheduleStart by remember { mutableStateOf(initialGroup.scheduleStart) }
    var scheduleEnd by remember { mutableStateOf(initialGroup.scheduleEnd) }
    var daysOfWeek by remember { mutableStateOf(initialGroup.daysOfWeek) }

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
                .testTag("edit_group_dialog"),
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
                        text = if (initialGroup.id == 0L) "Create App Group" else "Edit Group",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Name
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

                // App Selection
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

                // Rule 1: Schedule Rule
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

                // Rule 2: Budget Rule
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
                                dailyBudgetMinutes = dailyBudgetMinutes
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
