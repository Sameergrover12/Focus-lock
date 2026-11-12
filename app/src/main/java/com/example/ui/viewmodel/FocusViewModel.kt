package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.FocusApplication
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.DailyScreenTime
import com.example.data.local.entity.DailyUsageLog
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.data.model.AppInfo
import com.example.data.model.DayBarData
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FocusRepository
import com.example.service.FocusForegroundService
import com.example.util.AppListLoader
import com.example.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

import androidx.compose.runtime.Immutable
import com.example.ui.theme.BaseThemeMode
import com.example.ui.theme.SecondaryThemePalette

@Immutable
data class GroupWithAppDetails(
    val group: AppGroup,
    val memberPackages: List<String>,
    val memberApps: List<AppInfo>
)

class FocusViewModel(
    application: Application,
    private val repository: FocusRepository,
    private val preferencesRepository: UserPreferencesRepository,
    val backupManager: com.example.data.backup.BackupManager = (application as FocusApplication).backupManager
) : AndroidViewModel(application) {

    // Cloud Backup & Account State
    val connectedGmailAccount: StateFlow<String?> = preferencesRepository.connectedGmailAccount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val lastCloudBackupTime: StateFlow<Long> = preferencesRepository.lastCloudBackupTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    fun triggerAutoBackup(isExplicitUserRuleChange: Boolean = false) {
        backupManager.uploadToCloudImmediate(isExplicitUserRuleChange = isExplicitUserRuleChange)
    }

    // Theme & Onboarding & Master State
    val baseThemeMode: StateFlow<BaseThemeMode> = preferencesRepository.baseThemeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = preferencesRepository.getInitialBaseThemeMode()
    )

    val secondaryPalette: StateFlow<SecondaryThemePalette> = preferencesRepository.secondaryPalette.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SecondaryThemePalette.FOCUS_EMERALD
    )

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val isOnboardingComplete: StateFlow<Boolean> = preferencesRepository.isOnboardingComplete.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isMasterEnabled: StateFlow<Boolean> = preferencesRepository.isMasterEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isCheatProtectionEnabled: StateFlow<Boolean> = preferencesRepository.isCheatProtectionEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isInvincibleModeEnabled: StateFlow<Boolean> = preferencesRepository.isInvincibleModeEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isClockTamperProtectionEnabled: StateFlow<Boolean> = preferencesRepository.isClockTamperProtectionEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isClockTampered: StateFlow<Boolean> = com.example.util.TimeIntegrityManager.isClockTamperedFlow

    fun setClockTamperProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setClockTamperProtectionEnabled(enabled)
            triggerAutoBackup()
        }
    }

    val reclaimedCommitment: StateFlow<String> = preferencesRepository.reclaimedCommitment.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "My Focus"
    )

    val cognitivePassphrase: StateFlow<String> = preferencesRepository.cognitivePassphrase.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE
    )

    val isEmergencyBreakActive: StateFlow<Boolean> = com.example.util.EmergencyBreakManager.isBreakActiveFlow

    val emergencyBreaksRemaining: StateFlow<Int> = com.example.util.EmergencyBreakManager.breaksRemainingFlow

    val activeEmergencyBreakUntil: StateFlow<Long> = preferencesRepository.activeEmergencyBreakUntil.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    // Killswitch State Flows
    val isKillswitchActive: StateFlow<Boolean> = com.example.util.KillswitchManager.isKillswitchActiveFlow
    val isKillswitchEmergencyBreakActive: StateFlow<Boolean> = com.example.util.KillswitchManager.isEmergencyBreakActiveFlow
    val killswitchEndTime: StateFlow<Long> = com.example.util.KillswitchManager.killswitchEndTimeFlow
    val killswitchBreakEndTime: StateFlow<Long> = com.example.util.KillswitchManager.breakEndTimeFlow
    val isKillswitchBreakUsed: StateFlow<Boolean> = com.example.util.KillswitchManager.breakUsedFlow

    fun setReclaimedCommitment(commitment: String) {
        viewModelScope.launch {
            preferencesRepository.setReclaimedCommitment(commitment)
            triggerAutoBackup()
        }
    }

    fun setCognitivePassphrase(phrase: String) {
        viewModelScope.launch {
            preferencesRepository.setCognitivePassphrase(phrase)
            triggerAutoBackup()
        }
    }

    fun startEmergencyBreak(context: android.content.Context): Boolean {
        val started = com.example.util.EmergencyBreakManager.startTenMinuteBreak(context)
        if (started) {
            triggerAutoBackup()
        }
        return started
    }

    suspend fun triggerEmergencyBreak(typedPhrase: String): Boolean {
        val expectedPhrase = preferencesRepository.getCognitivePassphrase()
        if (typedPhrase.trim() != expectedPhrase.trim()) {
            return false
        }
        val started = com.example.util.EmergencyBreakManager.startTenMinuteBreak(getApplication())
        if (started) {
            triggerAutoBackup()
        }
        return started
    }

    fun takeEmergencyBreak() {
        startEmergencyBreak(getApplication())
    }

    fun endEmergencyBreakEarly() {
        com.example.util.EmergencyBreakManager.onBreakExpired(getApplication())
        triggerAutoBackup()
    }

    suspend fun isEmergencyBreakActive(): Boolean {
        return com.example.util.EmergencyBreakManager.isBreakActive(getApplication())
    }

    // Killswitch Operations
    fun startKillswitch(minutes: Int) {
        com.example.util.KillswitchManager.startKillswitch(getApplication(), minutes)
    }

    fun startKillswitchEmergencyBreak(): Boolean {
        return com.example.util.KillswitchManager.startEmergencyBreak(getApplication())
    }

    fun endKillswitchEmergencyBreakEarly() {
        com.example.util.KillswitchManager.endEmergencyBreakEarly(getApplication())
    }

    fun setInvincibleModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setInvincibleModeEnabled(enabled)
            triggerAutoBackup()
        }
    }

    suspend fun verifyCheatPassphrase(input: String): Boolean {
        return preferencesRepository.verifyCheatPassphrase(input)
    }

    suspend fun enableCheatProtection(passphrase: String): Boolean {
        val success = preferencesRepository.enableCheatProtection(passphrase)
        if (success) triggerAutoBackup()
        return success
    }

    fun setCheatProtection(passphrase: String) {
        viewModelScope.launch {
            val success = preferencesRepository.enableCheatProtection(passphrase)
            if (success) triggerAutoBackup()
        }
    }

    fun disableCheatProtection() {
        viewModelScope.launch {
            preferencesRepository.disableCheatProtection()
            triggerAutoBackup()
        }
    }

    // Installed apps cache
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // DB Entities
    val blockedApps: StateFlow<List<BlockedApp>> = repository.allBlockedApps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val screenTimeLimits: StateFlow<List<ScreenTimeLimit>> = repository.allScreenTimeLimits.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groups: StateFlow<List<AppGroup>> = repository.allGroups.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupApps: StateFlow<List<GroupApp>> = repository.allGroupApps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val blockedWebsites: StateFlow<List<BlockedWebsite>> = repository.allBlockedWebsites.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val blockedKeywords: StateFlow<List<BlockedKeyword>> = repository.allBlockedKeywords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalMinutesUsedToday: StateFlow<Int?> = repository.getTotalMinutesUsedToday().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val todayUsageLogs: StateFlow<List<DailyUsageLog>> = repository.getTodayUsageLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentDailyScreenTimes: StateFlow<List<DailyScreenTime>> = repository.recentDailyScreenTimes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _thirtyDayHistory = MutableStateFlow<List<DayBarData>>(emptyList())
    val thirtyDayHistory: StateFlow<List<DayBarData>> = _thirtyDayHistory.asStateFlow()

    val real30DayAverageMinutes: StateFlow<Int> = _thirtyDayHistory.map { list ->
        val activeDays = list.filter { it.minutes > 0 }
        if (activeDays.isNotEmpty()) {
            activeDays.map { it.minutes }.average().toInt()
        } else {
            val todayMins = list.lastOrNull()?.minutes ?: (totalMinutesUsedToday.value ?: 0)
            todayMins
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Intercept Tracking Flows
    val todayTotalIntercepts: StateFlow<Int> = com.example.util.InterceptTracker.todayTotalIntercepts
    val todayAppIntercepts: StateFlow<Map<String, Int>> = com.example.util.InterceptTracker.todayAppIntercepts
    val todayKeywordWebIntercepts: StateFlow<Map<String, Int>> = com.example.util.InterceptTracker.todayKeywordWebIntercepts
    val sevenDayTotalIntercepts: StateFlow<Int> = com.example.util.InterceptTracker.sevenDayTotalIntercepts

    val monthlyAverageScreenTimeMinutes: StateFlow<Double> = real30DayAverageMinutes.map { it.toDouble() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    fun refresh30DayHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.get30DayHistory(getApplication())
            _thirtyDayHistory.value = history
        }
    }

    val allRecentUsageLogs: StateFlow<List<DailyUsageLog>> = repository.allRecentUsageLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Combined Groups with App Details
    val groupsWithDetails: StateFlow<List<GroupWithAppDetails>> = combine(
        groups,
        groupApps,
        installedApps
    ) { grps, grpApps, instApps ->
        val appMap = instApps.associateBy { it.packageName }
        grps.map { grp ->
            val pkgs = grpApps.filter { it.groupId == grp.id }.map { it.packageName }
            val apps = pkgs.map { pkg ->
                appMap[pkg] ?: AppInfo(packageName = pkg, appName = pkg)
            }
            GroupWithAppDetails(group = grp, memberPackages = pkgs, memberApps = apps)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scheduleAndDailyGroups: StateFlow<List<GroupWithAppDetails>> = groupsWithDetails.map { list ->
        list.filter { !it.group.sessionLimitEnabled }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val sessionGroups: StateFlow<List<GroupWithAppDetails>> = groupsWithDetails.map { list ->
        list.filter { it.group.sessionLimitEnabled }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadInstalledApps()
        refreshDailyUsageStats()
        refresh30DayHistory()

        // React immediately to Cloud or JSON restore
        viewModelScope.launch {
            backupManager.restoreVersion.collect { version ->
                if (version > 0) {
                    loadInstalledApps()
                    refreshDailyUsageStats()
                    refresh30DayHistory()
                }
            }
        }

        // Keep Today's bar item synchronized live when today's dwell updates
        viewModelScope.launch {
            totalMinutesUsedToday.collect { todayMins ->
                val currentList = _thirtyDayHistory.value
                if (currentList.isNotEmpty() && todayMins != null) {
                    val updated = currentList.toMutableList()
                    val lastIdx = updated.lastIndex
                    val todayItem = updated[lastIdx]
                    if (todayItem.isToday && todayItem.minutes != todayMins) {
                        updated[lastIdx] = todayItem.copy(minutes = maxOf(todayItem.minutes, todayMins))
                        _thirtyDayHistory.value = updated
                    }
                }
            }
        }
    }

    fun refreshDailyUsageStats() {
        viewModelScope.launch {
            repository.resetDailyLimitsIfNeeded()
            repository.syncUsageStatsFromSystem(getApplication())
            repository.pruneHistoricalData()
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = AppListLoader.getInstalledLaunchableApps(getApplication())
            _installedApps.value = apps
        }
    }

    // Settings actions
    fun setBaseThemeMode(mode: BaseThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setBaseThemeMode(mode)
            triggerAutoBackup()
        }
    }

    fun setSecondaryPalette(palette: SecondaryThemePalette) {
        viewModelScope.launch {
            preferencesRepository.setSecondaryPalette(palette)
            triggerAutoBackup()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            triggerAutoBackup()
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMasterEnabled(enabled)
            if (enabled) {
                FocusForegroundService.startService(getApplication())
            }
            triggerAutoBackup()
        }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingComplete(complete)
            triggerAutoBackup()
        }
    }

    // App Blocking actions
    fun toggleAppBlock(app: AppInfo, shouldBlock: Boolean) {
        viewModelScope.launch {
            if (shouldBlock) {
                repository.blockApp(app.packageName, app.appName)
            } else {
                repository.unblockApp(app.packageName)
            }
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun blockApp(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.blockApp(packageName, appName)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun unblockApp(packageName: String) {
        viewModelScope.launch {
            repository.unblockApp(packageName)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    // Screen Time actions
    fun setAppScreenLimit(
        packageName: String,
        limitMinutes: Int,
        sessionLimitSeconds: Int = 0,
        cooldownSeconds: Int = 0
    ) {
        viewModelScope.launch {
            repository.setScreenTimeLimit(packageName, limitMinutes, sessionLimitSeconds, cooldownSeconds)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun setDailyLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            repository.setDailyLimit(packageName, limitMinutes)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun removeDailyLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeDailyLimit(packageName)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun setSessionLimit(packageName: String, sessionLimitSeconds: Int, cooldownSeconds: Int) {
        viewModelScope.launch {
            repository.setSessionLimit(packageName, sessionLimitSeconds, cooldownSeconds)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun removeSessionLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeSessionLimit(packageName)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun removeAppScreenLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeScreenTimeLimit(packageName)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    // Group actions
    fun saveGroup(group: AppGroup, memberPackages: List<String>) {
        viewModelScope.launch {
            repository.saveGroup(group, memberPackages)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    sealed class AddRuleResult {
        data class Added(val count: Int) : AddRuleResult()
        object AllDuplicates : AddRuleResult()
        object EmptyInput : AddRuleResult()
    }

    // Website actions
    fun addBlockedWebsites(input: String, onResult: (AddRuleResult) -> Unit = {}) {
        val rawItems = input.split(",").map { com.example.util.WebRuleHelper.normalizeDomain(it) }.filter { it.isNotBlank() }
        if (rawItems.isEmpty()) {
            onResult(AddRuleResult.EmptyInput)
            return
        }
        val uniqueInBatch = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()
        for (item in rawItems) {
            if (seenLower.add(item.lowercase())) {
                uniqueInBatch.add(item)
            }
        }
        val existing = blockedWebsites.value
        val existingLower = existing.map { com.example.util.WebRuleHelper.normalizeDomain(it.domainOrUrl).lowercase() }.toSet()
        val toAdd = uniqueInBatch.filter { it.lowercase() !in existingLower }
        if (toAdd.isEmpty()) {
            onResult(AddRuleResult.AllDuplicates)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (domain in toAdd) {
                repository.addBlockedWebsite(domain)
            }
            triggerAutoBackup(isExplicitUserRuleChange = true)
            withContext(Dispatchers.Main) {
                onResult(AddRuleResult.Added(toAdd.size))
            }
        }
    }

    fun addBlockedWebsite(url: String) {
        addBlockedWebsites(url)
    }

    fun removeBlockedWebsite(id: Long) {
        viewModelScope.launch {
            repository.removeBlockedWebsite(id)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    // Keyword actions
    fun addBlockedKeywords(input: String, caseSensitive: Boolean = false, onResult: (AddRuleResult) -> Unit = {}) {
        val rawItems = input.split(",").map { com.example.util.WebRuleHelper.normalizeKeyword(it) }.filter { it.isNotBlank() }
        if (rawItems.isEmpty()) {
            onResult(AddRuleResult.EmptyInput)
            return
        }
        val uniqueInBatch = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()
        for (item in rawItems) {
            if (seenLower.add(item.lowercase())) {
                uniqueInBatch.add(item)
            }
        }
        val existing = blockedKeywords.value
        val existingLower = existing.map { com.example.util.WebRuleHelper.normalizeKeyword(it.keyword).lowercase() }.toSet()
        val toAdd = uniqueInBatch.filter { it.lowercase() !in existingLower }
        if (toAdd.isEmpty()) {
            onResult(AddRuleResult.AllDuplicates)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (kw in toAdd) {
                repository.addBlockedKeyword(kw, caseSensitive)
            }
            triggerAutoBackup(isExplicitUserRuleChange = true)
            withContext(Dispatchers.Main) {
                onResult(AddRuleResult.Added(toAdd.size))
            }
        }
    }

    fun addBlockedKeyword(keyword: String, caseSensitive: Boolean = false) {
        addBlockedKeywords(keyword, caseSensitive)
    }

    fun removeBlockedKeyword(id: Long) {
        viewModelScope.launch {
            repository.removeBlockedKeyword(id)
            triggerAutoBackup(isExplicitUserRuleChange = true)
        }
    }

    // --- Gmail Cloud Backup & Offline Backup Actions ---

    fun onGoogleSignInSuccess(email: String, idToken: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            val normalizedEmail = email.trim().lowercase()

            if (com.example.util.FirebaseInitializer.ensureInitialized(getApplication())) {
                val restoreResult = backupManager.connectGmailAccount(normalizedEmail, idToken)
                if (restoreResult.isSuccess) {
                    if (restoreResult.getOrNull() == true) {
                        _syncStatusMessage.value = "Settings restored from Google Cloud"
                    } else {
                        _syncStatusMessage.value = "Settings backed up to Google Cloud"
                    }
                } else {
                    _syncStatusMessage.value = "Cloud sync notice: ${restoreResult.exceptionOrNull()?.message}"
                }
            } else {
                preferencesRepository.setConnectedGmailAccount(normalizedEmail)
                _syncStatusMessage.value = "Connected to $normalizedEmail"
            }
            _isSyncing.value = false
            onComplete()
        }
    }

    fun syncNow(onResult: (Boolean, String) -> Unit) {
        val email = connectedGmailAccount.value ?: preferencesRepository.getConnectedGmailAccountSync()
        if (email.isNullOrEmpty()) {
            onResult(false, "No Google account connected.")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            com.example.util.FirebaseInitializer.ensureInitialized(getApplication())
            val syncResult = backupManager.syncToCloudDirect(email, isManual = true)
            _isSyncing.value = false
            if (syncResult.isSuccess) {
                onResult(true, "Cloud backup completed successfully.")
            } else {
                onResult(false, "Cloud backup failed: ${syncResult.exceptionOrNull()?.message}")
            }
        }
    }

    fun restoreBackupNow(onResult: (Boolean, String) -> Unit) {
        val email = connectedGmailAccount.value ?: preferencesRepository.getConnectedGmailAccountSync()
        if (email.isNullOrEmpty()) {
            onResult(false, "No Google account connected.")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            com.example.util.FirebaseInitializer.ensureInitialized(getApplication())
            val restoreResult = backupManager.restoreFromCloud(email, isInitialStartup = false)
            _isSyncing.value = false
            if (restoreResult.isSuccess) {
                if (restoreResult.getOrNull() == true) {
                    onResult(true, "Cloud backup restored successfully!")
                } else {
                    onResult(true, "Cloud connected & synchronized.")
                }
            } else {
                onResult(false, "Restore failed: ${restoreResult.exceptionOrNull()?.message}")
            }
        }
    }

    fun signOutGmail() {
        viewModelScope.launch {
            _isSyncing.value = true
            backupManager.handleAccountSignOut()
            _syncStatusMessage.value = "Disconnected from Google Cloud"
            _isSyncing.value = false
        }
    }

    fun exportOfflineBackup(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.exportToUri(uri)
            if (result.isSuccess) {
                onResult(true, "Backup exported successfully.")
            } else {
                onResult(false, "Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun importOfflineBackup(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.importFromUri(uri)
            if (result.isSuccess && result.getOrNull() == true) {
                triggerAutoBackup()
                onResult(true, "Backup imported and applied successfully.")
            } else {
                onResult(false, "Import failed: ${result.exceptionOrNull()?.message ?: "Invalid file"}")
            }
        }
    }
}

class FocusViewModelFactory(
    private val application: Application,
    private val repository: FocusRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val backupManager: com.example.data.backup.BackupManager = (application as FocusApplication).backupManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            return FocusViewModel(application, repository, preferencesRepository, backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
