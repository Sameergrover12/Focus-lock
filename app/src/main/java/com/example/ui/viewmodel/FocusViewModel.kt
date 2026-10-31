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
import kotlinx.coroutines.launch

import kotlinx.coroutines.tasks.await

import com.example.ui.theme.BaseThemeMode
import com.example.ui.theme.SecondaryThemePalette

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

    fun triggerAutoBackup() {
        val email = connectedGmailAccount.value ?: return
        viewModelScope.launch {
            if (com.example.data.backup.BackupManager.isFirebaseAvailable(getApplication())) {
                try {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    val uid = auth.currentUser?.uid ?: return@launch
                    backupManager.syncToCloud(uid, email)
                } catch (e: Exception) {
                    // Suppress background auto-sync exceptions
                }
            }
        }
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

    val emergencyBreaksRemaining: StateFlow<Int> = preferencesRepository.emergencyBreaksRemaining.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferencesRepository.MAX_WEEKLY_EMERGENCY_BREAKS
    )

    val activeEmergencyBreakUntil: StateFlow<Long> = preferencesRepository.activeEmergencyBreakUntil.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    fun setReclaimedCommitment(commitment: String) {
        viewModelScope.launch {
            preferencesRepository.setReclaimedCommitment(commitment)
        }
    }

    fun setCognitivePassphrase(phrase: String) {
        viewModelScope.launch {
            preferencesRepository.setCognitivePassphrase(phrase)
        }
    }

    suspend fun triggerEmergencyBreak(typedPhrase: String): Boolean {
        return preferencesRepository.triggerEmergencyBreak(typedPhrase)
    }

    fun takeEmergencyBreak() {
        viewModelScope.launch {
            preferencesRepository.takeEmergencyBreak()
        }
    }

    fun endEmergencyBreakEarly() {
        viewModelScope.launch {
            preferencesRepository.endEmergencyBreakEarly()
        }
    }

    suspend fun isEmergencyBreakActive(): Boolean {
        return preferencesRepository.isEmergencyBreakActive()
    }

    fun setInvincibleModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setInvincibleModeEnabled(enabled)
        }
    }

    suspend fun verifyCheatPassphrase(input: String): Boolean {
        return preferencesRepository.verifyCheatPassphrase(input)
    }

    suspend fun enableCheatProtection(passphrase: String): Boolean {
        return preferencesRepository.enableCheatProtection(passphrase)
    }

    fun setCheatProtection(passphrase: String) {
        viewModelScope.launch {
            preferencesRepository.enableCheatProtection(passphrase)
        }
    }

    fun disableCheatProtection() {
        viewModelScope.launch {
            preferencesRepository.disableCheatProtection()
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

    val monthlyAverageScreenTimeMinutes: StateFlow<Double> = repository.recentDailyScreenTimes.map { list ->
        if (list.isEmpty()) 0.0 else list.map { it.screenOnMinutes }.average()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

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
        }
    }

    fun setSecondaryPalette(palette: SecondaryThemePalette) {
        viewModelScope.launch {
            preferencesRepository.setSecondaryPalette(palette)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMasterEnabled(enabled)
            if (enabled) {
                FocusForegroundService.startService(getApplication())
            }
        }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingComplete(complete)
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
        }
    }

    fun blockApp(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.blockApp(packageName, appName)
        }
    }

    fun unblockApp(packageName: String) {
        viewModelScope.launch {
            repository.unblockApp(packageName)
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
        }
    }

    fun setDailyLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            repository.setDailyLimit(packageName, limitMinutes)
        }
    }

    fun removeDailyLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeDailyLimit(packageName)
        }
    }

    fun setSessionLimit(packageName: String, sessionLimitSeconds: Int, cooldownSeconds: Int) {
        viewModelScope.launch {
            repository.setSessionLimit(packageName, sessionLimitSeconds, cooldownSeconds)
        }
    }

    fun removeSessionLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeSessionLimit(packageName)
        }
    }

    fun removeAppScreenLimit(packageName: String) {
        viewModelScope.launch {
            repository.removeScreenTimeLimit(packageName)
        }
    }

    // Group actions
    fun saveGroup(group: AppGroup, memberPackages: List<String>) {
        viewModelScope.launch {
            repository.saveGroup(group, memberPackages)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
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
        viewModelScope.launch {
            for (domain in toAdd) {
                repository.addBlockedWebsite(domain)
            }
            triggerAutoBackup()
            onResult(AddRuleResult.Added(toAdd.size))
        }
    }

    fun addBlockedWebsite(url: String) {
        addBlockedWebsites(url)
    }

    fun removeBlockedWebsite(id: Long) {
        viewModelScope.launch {
            repository.removeBlockedWebsite(id)
            triggerAutoBackup()
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
        viewModelScope.launch {
            for (kw in toAdd) {
                repository.addBlockedKeyword(kw, caseSensitive)
            }
            triggerAutoBackup()
            onResult(AddRuleResult.Added(toAdd.size))
        }
    }

    fun addBlockedKeyword(keyword: String, caseSensitive: Boolean = false) {
        addBlockedKeywords(keyword, caseSensitive)
    }

    fun removeBlockedKeyword(id: Long) {
        viewModelScope.launch {
            repository.removeBlockedKeyword(id)
            triggerAutoBackup()
        }
    }

    // --- Gmail Cloud Backup & Offline Backup Actions ---

    fun onGoogleSignInSuccess(email: String, idToken: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            preferencesRepository.setConnectedGmailAccount(email)

            if (com.example.data.backup.BackupManager.isFirebaseAvailable(getApplication())) {
                try {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    if (!idToken.isNullOrEmpty()) {
                        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                        auth.signInWithCredential(credential).await()
                    }
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        val restoreResult = backupManager.restoreFromCloud(uid)
                        if (restoreResult.isSuccess && restoreResult.getOrNull() == true) {
                            _syncStatusMessage.value = "Settings restored from Google Cloud"
                        } else {
                            backupManager.syncToCloud(uid, email)
                            _syncStatusMessage.value = "Settings backed up to Google Cloud"
                        }
                    }
                } catch (e: Exception) {
                    _syncStatusMessage.value = "Account connected (Cloud sync notice: ${e.message})"
                }
            } else {
                _syncStatusMessage.value = "Connected to $email"
            }
            _isSyncing.value = false
            onComplete()
        }
    }

    fun syncNow(onResult: (Boolean, String) -> Unit) {
        val email = connectedGmailAccount.value
        if (email.isNullOrEmpty()) {
            onResult(false, "No Google account connected.")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            if (!com.example.data.backup.BackupManager.isFirebaseAvailable(getApplication())) {
                _isSyncing.value = false
                onResult(false, "Firebase backend not initialized. Add google-services.json to enable cloud sync.")
                return@launch
            }
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _isSyncing.value = false
                    onResult(false, "Authentication session expired. Please sign in again.")
                    return@launch
                }
                val syncResult = backupManager.syncToCloud(uid, email)
                _isSyncing.value = false
                if (syncResult.isSuccess) {
                    onResult(true, "Cloud backup completed successfully.")
                } else {
                    onResult(false, "Cloud sync failed: ${syncResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    fun signOutGmail() {
        viewModelScope.launch {
            if (com.example.data.backup.BackupManager.isFirebaseAvailable(getApplication())) {
                try {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                } catch (e: Exception) {
                    // Suppress
                }
            }
            preferencesRepository.setConnectedGmailAccount(null)
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
