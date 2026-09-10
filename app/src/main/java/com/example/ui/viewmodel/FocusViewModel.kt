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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupWithAppDetails(
    val group: AppGroup,
    val memberPackages: List<String>,
    val memberApps: List<AppInfo>
)

class FocusViewModel(
    application: Application,
    private val repository: FocusRepository,
    private val preferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    // Theme & Onboarding & Master State
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

    val todayUsageLogs: StateFlow<List<DailyUsageLog>> = repository.getUsageLogsForDate(FocusRepository.getTodayDateString()).stateIn(
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

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = AppListLoader.getInstalledLaunchableApps(getApplication())
            _installedApps.value = apps
        }
    }

    // Settings actions
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

    // Screen Time actions
    fun setAppScreenLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            repository.setScreenTimeLimit(packageName, limitMinutes)
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

    // Website actions
    fun addBlockedWebsite(url: String) {
        viewModelScope.launch {
            repository.addBlockedWebsite(url)
        }
    }

    fun removeBlockedWebsite(id: Long) {
        viewModelScope.launch {
            repository.removeBlockedWebsite(id)
        }
    }

    // Keyword actions
    fun addBlockedKeyword(keyword: String, caseSensitive: Boolean = false) {
        viewModelScope.launch {
            repository.addBlockedKeyword(keyword, caseSensitive)
        }
    }

    fun removeBlockedKeyword(id: Long) {
        viewModelScope.launch {
            repository.removeBlockedKeyword(id)
        }
    }
}

class FocusViewModelFactory(
    private val application: Application,
    private val repository: FocusRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            return FocusViewModel(application, repository, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
