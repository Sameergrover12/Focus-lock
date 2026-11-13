package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.example.FocusApplication
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.data.repository.FocusRepository
import com.example.focusapp.util.MotivationLibrary
import com.example.ui.blocked.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

import java.util.concurrent.atomic.AtomicBoolean

import com.example.ui.overlay.OverlayManager
import com.example.util.AppForegroundTracker
import com.example.util.FocusHaptics
import com.example.util.PermissionHelper
import com.example.util.SettingsTamperDetector
import com.example.util.TimeIntegrityManager

class FocusAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var periodicScanJob: Job? = null
    private val ruleCollectionJobs = mutableListOf<Job>()
    private var timeChangeReceiver: BroadcastReceiver? = null

    private fun ensureActiveScope(): CoroutineScope {
        if (!serviceJob.isActive) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
        }
        return serviceScope
    }

    // Local cached in-memory rules for fast checks
    @Volatile private var cachedBlockedPackages: Set<String> = emptySet()
    @Volatile private var blockedAppsCache = listOf<BlockedApp>()
    @Volatile private var screenLimitsCache = listOf<ScreenTimeLimit>()
    @Volatile private var groupsCache = listOf<AppGroup>()
    @Volatile private var groupAppsCache = listOf<GroupApp>()
    @Volatile private var blockedWebsitesCache = listOf<BlockedWebsite>()
    @Volatile private var blockedKeywordsCache = listOf<BlockedKeyword>()
    @Volatile private var isMasterEnabled = true
    @Volatile private var isInvincibleModeEnabled = false
    @Volatile private var isCheatProtectionEnabled = false
    @Volatile private var isClockTamperProtectionEnabled = true
    @Volatile private var todayUsageCache = mapOf<String, Int>()
    @Volatile private var activeEmergencyBreakUntil = 0L
    @Volatile private var cachedReclaimedCommitment: String? = null

    // Pre-indexed O(1) structures updated on background thread (Dispatchers.Default)
    @Volatile private var blockedAppsByPackage = mapOf<String, BlockedApp>()
    @Volatile private var screenLimitsByPackage = mapOf<String, ScreenTimeLimit>()
    @Volatile private var groupsById = mapOf<Long, AppGroup>()
    @Volatile private var groupIdsByPackage = mapOf<String, List<Long>>()
    @Volatile private var compiledWebsites = listOf<Pair<BlockedWebsite, Regex>>()
    @Volatile private var compiledKeywords = listOf<Pair<BlockedKeyword, Regex>>()
    private val appNameCache = ConcurrentHashMap<String, String>()

    @Volatile private var lastBlockedTime = 0L
    @Volatile private var lastBlockedPackage: String? = null
    private var lastScanTime = 0L
    private var lastEvaluatedPackage: String? = null
    private var lastHideAppInterceptTime = 0L
    private var lastHideAppScanTime = 0L

    companion object {
        private const val TAG = "FocusAccessibility"
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set

        @Volatile
        var currentForegroundPackage: String? = null
            private set

        fun getActiveFocusedPackage(): String? {
            return instance?.getCurrentlyFocusedPackage() ?: currentForegroundPackage
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureActiveScope()
        observeDatabaseRules()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true
        currentForegroundPackage = null
        lastEvaluatedPackage = null
        Log.d(TAG, "FocusAccessibilityService connected")

        val scope = ensureActiveScope()
        configureServiceInfo()

        // Inside onServiceConnected(), launch a coroutine that collects the Flow<List<String>>
        // from the Room Database and updates cachedBlockedPackages in local memory
        val app = application as? FocusApplication
        if (app != null) {
            scope.launch {
                try {
                    val initial = app.repository.getAllBlockedPackageNamesSync()
                    cachedBlockedPackages = initial.toSet()
                    Log.d(TAG, "Initial cachedBlockedPackages size: ${cachedBlockedPackages.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed initial sync read of blocked packages", e)
                }
                app.repository.allBlockedPackageNames.collect { list ->
                    cachedBlockedPackages = list.toSet()
                    Log.d(TAG, "Updated cachedBlockedPackages: ${cachedBlockedPackages.size} packages")
                }
            }
        }

        // Start listening to database changes to maintain warm memory caches
        observeDatabaseRules()

        // Start lightweight periodic rescan backstop for feed-style apps and coalesced events
        startPeriodicContentScanner()

        // Register dynamic time/timezone tamper broadcast receiver for sub-second detection
        registerTimeChangeReceiver()

        // Also ensure foreground service is running
        FocusForegroundService.startService(applicationContext)
    }

    private fun registerTimeChangeReceiver() {
        if (timeChangeReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
                    val ctx = context ?: applicationContext
                    TimeIntegrityManager.onTimeChanged(ctx)
                    val activePkg = currentForegroundPackage
                    if (!activePkg.isNullOrEmpty() && activePkg != packageName && !isSystemOverlay(activePkg)) {
                        ensureActiveScope().launch(Dispatchers.Default) {
                            checkForegroundPackage(activePkg)
                        }
                    }
                }
            }
        }
        timeChangeReceiver = receiver
        try {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register timeChangeReceiver", e)
        }
    }

    private fun configureServiceInfo() {
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.notificationTimeout = 0
            serviceInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure serviceInfo", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
        currentForegroundPackage = null
        lastEvaluatedPackage = null
        FocusForegroundService.onForegroundPackageChanged(null)
        try {
            timeChangeReceiver?.let { unregisterReceiver(it) }
            timeChangeReceiver = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister timeChangeReceiver", e)
        }
        periodicScanJob?.cancel()
        synchronized(ruleCollectionJobs) {
            ruleCollectionJobs.forEach { it.cancel() }
            ruleCollectionJobs.clear()
        }
        serviceJob.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        OverlayManager.removeOverlay(this)
        Log.d(TAG, "FocusAccessibilityService destroyed")
    }

    private fun startPeriodicContentScanner() {
        periodicScanJob?.cancel()
        periodicScanJob = serviceScope.launch(Dispatchers.Default) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            while (isActive) {
                val fgPkg = currentForegroundPackage
                val isSettings = SettingsTamperDetector.isSettingsAppPackage(fgPkg)
                // 40ms rapid watchdog when in Settings to catch tamper pages instantly, 350ms otherwise
                delay(if (isSettings) 40L else 350L)

                // Pauses immediately when Focus Lock itself is in foreground
                if (AppForegroundTracker.isFocusLockInForeground) {
                    continue
                }

                // 1. Surgical Settings Anti-Tamper Check (runs 24/7, never paused by Emergency Break)
                val isProtectionActive = isMasterEnabled ||
                        com.example.util.KillswitchManager.isKillswitchActive(this@FocusAccessibilityService) ||
                        isCheatProtectionEnabled ||
                        isInvincibleModeEnabled

                if (isProtectionActive && isSettings && !isUnwindingSettings.get()) {
                    withContext(Dispatchers.Main) {
                        checkAndInterceptSettingsTamper(fgPkg)
                    }
                    continue
                }

                // 2. Killswitch Lockdown scan (runs regardless of master switch, bypassed during Killswitch emergency break)
                if (com.example.util.KillswitchManager.isKillswitchActive(this@FocusAccessibilityService) &&
                    !com.example.util.KillswitchManager.isEmergencyBreakActive(this@FocusAccessibilityService)
                ) {
                    val currentPkg = currentForegroundPackage ?: withContext(Dispatchers.Main) { getCurrentlyFocusedPackage() }
                    if (!currentPkg.isNullOrEmpty() && !com.example.util.KillswitchManager.isAllowedKillswitchPackage(this@FocusAccessibilityService, currentPkg)) {
                        com.example.util.InterceptTracker.recordAppIntercept(this@FocusAccessibilityService, currentPkg)
                        withContext(Dispatchers.Main) {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            launchMainActivityForKillswitch()
                        }
                        continue
                    }
                }

                if (!isMasterEnabled) continue

                // 3. Bypass distraction rules during active emergency break
                if (com.example.util.EmergencyBreakManager.isBreakActive(this@FocusAccessibilityService)) {
                    continue
                }

                // 4. HARD FLOATING WINDOW SCAN: inspect all visible interactive windows for any blocked app
                val blockedInWindow = withContext(Dispatchers.Main) {
                    findBlockedPackageInVisibleWindows(currentForegroundPackage)
                }
                if (blockedInWindow != null) {
                    withContext(Dispatchers.Main.immediate) {
                        triggerBlockScreen(blockedInWindow)
                    }
                    continue
                }

                val currentPkg = currentForegroundPackage
                // Pauses immediately when Focus Lock itself is foreground or system overlay
                if (currentPkg.isNullOrEmpty() || currentPkg == packageName || isSystemOverlay(currentPkg)) {
                    continue
                }

                // Run only when active website or keyword rules exist
                if (blockedWebsitesCache.isEmpty() && blockedKeywordsCache.isEmpty()) {
                    continue
                }

                scanVisibleWindowsForContent()
            }
        }
    }

    fun reloadAllRules() {
        observeDatabaseRules()
    }

    fun reloadRulesImmediately() {
        observeDatabaseRules()
        val scope = ensureActiveScope()
        scope.launch(Dispatchers.Main) {
            val currentPkg = currentForegroundPackage ?: getCurrentlyFocusedPackage()
            if (!currentPkg.isNullOrEmpty() && currentPkg != packageName && !isSystemOverlay(currentPkg)) {
                checkForegroundPackage(currentPkg)
            }
        }
    }

    private fun observeDatabaseRules() {
        val app = application as? FocusApplication ?: return
        val repo = app.repository
        val prefRepo = app.preferencesRepository
        val scope = ensureActiveScope()

        synchronized(ruleCollectionJobs) {
            ruleCollectionJobs.forEach { it.cancel() }
            ruleCollectionJobs.clear()

            // Immediate synchronous pre-load on IO to populate all caches instantly
            scope.launch {
                try {
                    val syncPackages = repo.getAllBlockedPackageNamesSync()
                    cachedBlockedPackages = syncPackages.toSet()
                    val syncApps = repo.getAllBlockedAppsSync()
                    blockedAppsCache = syncApps
                    blockedAppsByPackage = syncApps.associateBy { it.packageName }

                    val syncLimits = repo.getAllScreenTimeLimitsSync()
                    screenLimitsCache = syncLimits
                    screenLimitsByPackage = syncLimits.associateBy { it.packageName }

                    val syncGroups = repo.getAllGroupsSync()
                    groupsCache = syncGroups
                    groupsById = syncGroups.associateBy { it.id }

                    val syncGroupApps = repo.getAllGroupAppsSync()
                    groupAppsCache = syncGroupApps
                    val gMap = mutableMapOf<String, MutableList<Long>>()
                    for (item in syncGroupApps) {
                        gMap.getOrPut(item.packageName) { mutableListOf() }.add(item.groupId)
                    }
                    groupIdsByPackage = gMap

                    val syncWebsites = repo.getAllBlockedWebsitesSync()
                    blockedWebsitesCache = syncWebsites
                    compiledWebsites = syncWebsites.mapNotNull { rule ->
                        val norm = ContentScanner.normalizeUrlOrDomain(rule.domainOrUrl)
                        if (norm.isNotEmpty()) {
                            rule to ContentScanner.buildWordBoundaryRegex(norm, caseSensitive = false)
                        } else null
                    }

                    val syncKeywords = repo.getAllBlockedKeywordsSync()
                    blockedKeywordsCache = syncKeywords
                    compiledKeywords = syncKeywords.mapNotNull { rule ->
                        val pattern = rule.keyword.trim()
                        if (pattern.isNotEmpty()) {
                            rule to ContentScanner.buildWordBoundaryRegex(pattern, caseSensitive = rule.caseSensitive)
                        } else null
                    }

                    isMasterEnabled = if (prefRepo.syncPrefs.contains("master_enabled")) {
                        prefRepo.syncPrefs.getBoolean("master_enabled", true)
                    } else true
                    isInvincibleModeEnabled = if (prefRepo.syncPrefs.contains("invincible_mode_enabled")) {
                        prefRepo.syncPrefs.getBoolean("invincible_mode_enabled", false)
                    } else false
                    isCheatProtectionEnabled = if (prefRepo.syncPrefs.contains("cheat_protection_enabled")) {
                        prefRepo.syncPrefs.getBoolean("cheat_protection_enabled", false)
                    } else false
                    isClockTamperProtectionEnabled = true
                    cachedReclaimedCommitment = prefRepo.syncPrefs.getString("reclaimed_commitment", "My Focus")

                    Log.d(TAG, "Preloaded ${syncPackages.size} blocked packages, ${syncLimits.size} limits, ${syncGroups.size} groups into memory")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during synchronous pre-load of rules", e)
                }
            }

            // Stream 1: Dedicated Flow<List<String>> of blocked package names
            ruleCollectionJobs += scope.launch {
                repo.allBlockedPackageNames.collect { packageNames ->
                    val set = packageNames.toSet()
                    cachedBlockedPackages = set
                    Log.d(TAG, "Flow update allBlockedPackageNames: ${set.size} packages")
                }
            }

            // Stream 2: Full BlockedApp entities stream
            ruleCollectionJobs += scope.launch {
                repo.allBlockedApps.collect { list ->
                    blockedAppsCache = list
                    val map = list.associateBy { it.packageName }
                    val set = list.map { it.packageName }.toSet()
                    withContext(Dispatchers.Default) {
                        blockedAppsByPackage = map
                        cachedBlockedPackages = set
                    }
                    Log.d(TAG, "Flow update allBlockedApps: ${list.size} apps")
                }
            }

            // Stream 3: Screen time limits
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                repo.allScreenTimeLimits.collect { list ->
                    screenLimitsCache = list
                    withContext(Dispatchers.Default) {
                        screenLimitsByPackage = list.associateBy { it.packageName }
                    }
                }
            }

            // Stream 4: Groups
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                repo.allGroups.collect { list ->
                    groupsCache = list
                    withContext(Dispatchers.Default) {
                        groupsById = list.associateBy { it.id }
                    }
                }
            }

            // Stream 5: Group apps
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                repo.allGroupApps.collect { list ->
                    groupAppsCache = list
                    withContext(Dispatchers.Default) {
                        val map = mutableMapOf<String, MutableList<Long>>()
                        for (item in list) {
                            map.getOrPut(item.packageName) { mutableListOf() }.add(item.groupId)
                        }
                        groupIdsByPackage = map
                    }
                }
            }

            // Stream 6: Websites
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                repo.allBlockedWebsites.collect { list ->
                    blockedWebsitesCache = list
                    withContext(Dispatchers.Default) {
                        compiledWebsites = list.mapNotNull { rule ->
                            val norm = ContentScanner.normalizeUrlOrDomain(rule.domainOrUrl)
                            if (norm.isNotEmpty()) {
                                rule to ContentScanner.buildWordBoundaryRegex(norm, caseSensitive = false)
                            } else null
                        }
                    }
                }
            }

            // Stream 7: Keywords
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                repo.allBlockedKeywords.collect { list ->
                    blockedKeywordsCache = list
                    withContext(Dispatchers.Default) {
                        compiledKeywords = list.mapNotNull { rule ->
                            val pattern = rule.keyword.trim()
                            if (pattern.isNotEmpty()) {
                                rule to ContentScanner.buildWordBoundaryRegex(pattern, caseSensitive = rule.caseSensitive)
                            } else null
                        }
                    }
                }
            }

            // Master switch
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                prefRepo.isMasterEnabled.collect { isMasterEnabled = it }
            }

            // Invincible mode
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                prefRepo.isInvincibleModeEnabled.collect { isInvincibleModeEnabled = it }
            }

            // Cheat protection / Strict mode
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                prefRepo.isCheatProtectionEnabled.collect { isCheatProtectionEnabled = it }
            }

            // Clock tamper protection (always active, cannot be disabled)
            isClockTamperProtectionEnabled = true

            // Emergency break
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                prefRepo.activeEmergencyBreakUntil.collect { until ->
                    activeEmergencyBreakUntil = until
                    scheduleEmergencySnapback(until)
                }
            }

            // Reclaimed commitment
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                prefRepo.reclaimedCommitment.collect { cachedReclaimedCommitment = it }
            }

            // Today usage logs
            ruleCollectionJobs += scope.launch(Dispatchers.IO) {
                repo.getTodayUsageLogs().collect { logs ->
                    todayUsageCache = logs.associate { it.packageName to it.minutesUsed }
                }
            }
        }
    }

    private var snapbackJob: Job? = null

    private fun scheduleEmergencySnapback(until: Long) {
        snapbackJob?.cancel()
        val delayMs = until - TimeIntegrityManager.getTrueTimeMillis()
        if (delayMs > 0) {
            snapbackJob = serviceScope.launch(Dispatchers.Default) {
                delay(delayMs)
                // The Abrupt Snapback: Terminating access immediately with no warning, toast messages, or extension options
                val currentPkg = currentForegroundPackage
                if (!currentPkg.isNullOrEmpty() && currentPkg != packageName && !isSystemOverlay(currentPkg)) {
                    checkForegroundPackage(currentPkg)
                }
            }
        }
    }

    private fun isSystemOverlay(pkg: String?): Boolean {
        if (pkg == null) return false
        return pkg == "com.android.systemui" ||
               pkg.contains("inputmethod") ||
               pkg.contains(".ime") ||
               pkg == "android"
    }

    /**
     * Finds the package that currently has input focus.
     * In split-screen and floating-window multitasking, ONLY the window with input focus
     * has its usage time accrued.
     */
    fun getCurrentlyFocusedPackage(): String? {
        if (AppForegroundTracker.isFocusLockInForeground) {
            return packageName
        }
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                // Priority 1: Application window with input focus (isFocused == true)
                val focusedAppWindow = windowList.firstOrNull {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
                }
                val focusedAppPkg = focusedAppWindow?.root?.packageName?.toString()
                if (!focusedAppPkg.isNullOrEmpty() && !isSystemOverlay(focusedAppPkg) && !isInputMethod(focusedAppPkg)) {
                    return focusedAppPkg
                }

                // Priority 2: Any non-IME, non-system focused window
                val nonImeFocused = windowList.firstOrNull {
                    it.isFocused &&
                    it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                    it.type != AccessibilityWindowInfo.TYPE_SYSTEM
                }
                val nonImePkg = nonImeFocused?.root?.packageName?.toString()
                if (!nonImePkg.isNullOrEmpty() && !isSystemOverlay(nonImePkg) && !isInputMethod(nonImePkg)) {
                    return nonImePkg
                }

                // Priority 3: Active application window (e.g. app hosting active IME keyboard)
                val activeAppWindow = windowList.firstOrNull {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
                }
                val activePkg = activeAppWindow?.root?.packageName?.toString()
                if (!activePkg.isNullOrEmpty() && !isSystemOverlay(activePkg) && !isInputMethod(activePkg)) {
                    return activePkg
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect windows for input focus", e)
        }

        if (!AppForegroundTracker.isFocusLockInForeground) {
            val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
            val activePkg = activeRoot?.packageName?.toString()
            if (!activePkg.isNullOrEmpty() && !isSystemOverlay(activePkg) && !isInputMethod(activePkg)) {
                return activePkg
            }
        }
        return null
    }

    /**
     * Collects all packages visible on screen across all interactive windows (base app, split-screen, floating windows).
     * Blocking enforcement MUST evaluate EVERY visible window, focused or not.
     */
    private fun getAllVisiblePackages(eventPkg: String?): Set<String> {
        if (AppForegroundTracker.isFocusLockInForeground) {
            return emptySet()
        }
        val result = LinkedHashSet<String>()
        if (!eventPkg.isNullOrEmpty() && eventPkg != packageName && !isSystemOverlay(eventPkg) && !isInputMethod(eventPkg)) {
            result.add(eventPkg)
        }

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (win in windowList) {
                    if (win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                    val root = try { win.root } catch (e: Exception) { null }
                    val pkg = root?.packageName?.toString()
                    if (!pkg.isNullOrEmpty() && pkg != packageName && !isSystemOverlay(pkg) && !isInputMethod(pkg)) {
                        result.add(pkg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect visible window packages", e)
        }

        // Never call rootInActiveWindow on Main thread if Focus Lock is in foreground or event is an IME
        if (!AppForegroundTracker.isFocusLockInForeground && eventPkg != packageName && !isInputMethod(eventPkg ?: "")) {
            val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
            val activePkg = activeRoot?.packageName?.toString()
            if (!activePkg.isNullOrEmpty() && activePkg != packageName && !isSystemOverlay(activePkg) && !isInputMethod(activePkg)) {
                result.add(activePkg)
            }
        }

        return result
    }

    /**
     * Inspects every visible window (via getWindows()) and active event package to detect any blocked app,
     * including apps running in freeform floating or split-screen windows.
     */
    private fun findBlockedPackageInVisibleWindows(eventPkg: String?): String? {
        if (com.example.util.EmergencyBreakManager.isBreakActive(this)) {
            return null
        }
        if (AppForegroundTracker.isFocusLockInForeground) {
            return null
        }
        val visiblePkgs = getAllVisiblePackages(eventPkg)
        for (pkg in visiblePkgs) {
            if (SettingsTamperDetector.isSettingsAppPackage(pkg)) continue
            if (cachedBlockedPackages.contains(pkg) || blockedAppsByPackage.containsKey(pkg) || isPackageRestricted(pkg)) {
                return pkg
            }
        }
        return null
    }

    private var lastScanTimeMs = 0L

    private fun isInputMethod(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("ime") ||
                lower.contains("touchtype") ||
                lower.contains("swiftkey") ||
                lower.contains("honeyboard") ||
                lower.contains("sogou") ||
                lower.contains("gboard") ||
                lower.contains("fleksy") ||
                lower.contains("chrooma") ||
                lower.contains("anysoftkeyboard") ||
                pkg == "com.google.android.inputmethod.latin" ||
                pkg == "com.touchtype.swiftkey" ||
                pkg == "com.touchtype.swiftkey.beta" ||
                pkg == "com.samsung.android.honeyboard" ||
                pkg == "com.sohu.inputmethod.sogou" ||
                pkg == "com.baidu.input"
    }

    private fun isRecognizedBrowserOrMonitored(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        if (ContentScanner.BROWSER_URL_BAR_IDS.containsKey(pkg)) return true
        val lower = pkg.lowercase()
        if (lower.contains("browser") || lower.contains("chrome") || lower.contains("firefox") || lower.contains("opera") || lower.contains("edge") || lower.contains("brave")) return true
        return cachedBlockedPackages.contains(pkg) || blockedAppsByPackage.containsKey(pkg) || screenLimitsByPackage.containsKey(pkg) || groupIdsByPackage.containsKey(pkg)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Fix 1: Completely Pause AccessibilityService While Focus Lock Is in the Foreground
        if (AppForegroundTracker.isFocusLockInForeground) {
            return
        }

        // a. Immediately return if event == null or event.packageName == null
        if (event == null) return
        val eventPkg = event.packageName?.toString() ?: return

        val myPackageName = packageName
        val appPkg = applicationContext.packageName

        // b. Self & System Ignore: Immediately return if event is Focus Lock's own UI, systemui, or keyboard IME
        if (eventPkg == myPackageName || eventPkg == appPkg || eventPkg == "com.aistudio.focuslock.fclk" || eventPkg == "com.android.systemui") {
            return
        }
        if (isInputMethod(eventPkg)) {
            return
        }

        val className = event.className?.toString() ?: ""
        if (className.contains("BlockedActivity")) {
            return
        }

        val eventType = event.eventType

        // -------------------------------------------------------------------------
        // 1. SURGICAL SETTINGS ANTI-TAMPER PROTECTION (24/7 Active, Independent of Breaks):
        // Step A (0.1ms): Instant in-memory check on event.text, contentDescription, className,
        // and source?.text (on TYPE_VIEW_CLICKED) BEFORE touching rootInActiveWindow.
        // Step B: Fast shallow findAccessibilityNodeInfosByText fallback (Recents / Animations).
        // Step C: Instant "Cancel" ACTION_CLICK on Turn off / Stop confirmation dialogs.
        // -------------------------------------------------------------------------
        val isSettingsEvent = (eventPkg.contains("settings", ignoreCase = true) &&
                !eventPkg.contains("focuslock", ignoreCase = true)) ||
                (eventPkg == "android" && SettingsTamperDetector.isSettingsAppPackage(currentForegroundPackage))

        if (isSettingsEvent) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                SettingsTamperDetector.isSettingsAppPackage(eventPkg)
            ) {
                currentForegroundPackage = eventPkg
                FocusForegroundService.onForegroundPackageChanged(eventPkg)
            }

            val isProtectionActive = isMasterEnabled ||
                    com.example.util.KillswitchManager.isKillswitchActive(this) ||
                    isCheatProtectionEnabled ||
                    isInvincibleModeEnabled

            if (isProtectionActive) {
                if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                ) {
                    if (tryInstantStepATamperIntercept(event, eventType, className)) {
                        return
                    }
                }
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                ) {
                    if (checkAndInterceptSettingsTamper(eventPkg, className)) {
                        return
                    }
                }
            }
        }

        // KILLSWITCH ENFORCEMENT: Operates even if Master Switch is OFF
        if (com.example.util.KillswitchManager.isKillswitchActive(this) &&
            !com.example.util.KillswitchManager.isEmergencyBreakActive(this)
        ) {
            if (!com.example.util.KillswitchManager.isAllowedKillswitchPackage(this, eventPkg)) {
                com.example.util.InterceptTracker.recordAppIntercept(this, eventPkg)
                performGlobalAction(GLOBAL_ACTION_HOME)
                launchMainActivityForKillswitch()
                return
            }
        }

        if (!isMasterEnabled) return

        // d. Throttle Window Content Scans: run at most once every 300ms and avoid recursive scans for unmonitored apps
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastScanTimeMs < 300L) {
                return
            }
            lastScanTimeMs = now

            if (!isRecognizedBrowserOrMonitored(eventPkg) && !isRecognizedBrowserOrMonitored(currentForegroundPackage)) {
                return
            }
        }

        // 0. The Emergency Failsafe System: 10-Minute Hard Cap (Pauses distraction restrictions ONLY)
        if (com.example.util.EmergencyBreakManager.isBreakActive(this)) {
            if (!isSystemOverlay(eventPkg) && eventPkg != myPackageName) {
                currentForegroundPackage = eventPkg
                FocusForegroundService.onForegroundPackageChanged(eventPkg)
            }
            return
        }

        // 2. FLOATING WINDOW & MULTI-WINDOW BLOCKING ENFORCEMENT
        val blockedPkg = findBlockedPackageInVisibleWindows(eventPkg)
        if (blockedPkg != null) {
            if (cachedBlockedPackages.contains(blockedPkg) || blockedAppsByPackage.containsKey(blockedPkg)) {
                triggerBlockScreen(blockedPkg)
            } else {
                checkForegroundPackage(blockedPkg)
            }
            return
        }

        // 3. OS "HIDE APPS" TEXT INTERCEPTOR:
        // Blocks user from accessing OEM Settings or Launcher "Hide Apps" loophole
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            if (checkAndInterceptHideApps(event)) {
                return
            }
        }

        // Offload window traversal, visible package collection, and text scanning
        // to background thread (Dispatchers.Default) to eliminate UI thread jitter!
        serviceScope.launch(Dispatchers.Default) {
            handleEventInBackground(eventPkg, eventType)
        }
    }

    private val isUnwindingSettings = AtomicBoolean(false)

    private val cachedAppLabel: String by lazy {
        try {
            getString(com.example.R.string.app_name)
        } catch (_: Exception) {
            "Focus Lock"
        }
    }

    private fun dismissTamperConfirmationDialog(root: AccessibilityNodeInfo?) {
        if (root == null) return
        try {
            fun tryClickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
                var curr: AccessibilityNodeInfo? = node
                var hops = 0
                while (curr != null && hops < 3) {
                    if (curr.isClickable) {
                        val clicked = try { curr.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
                        if (curr !== node) {
                            try { curr.recycle() } catch (_: Exception) {}
                        }
                        if (clicked) return true
                        break
                    }
                    val nextParent = try { curr.parent } catch (_: Exception) { null }
                    if (curr !== node) {
                        try { curr.recycle() } catch (_: Exception) {}
                    }
                    curr = nextParent
                    hops++
                }
                if (curr != null && curr !== node) {
                    try { curr.recycle() } catch (_: Exception) {}
                }
                return false
            }

            // Fast indexed lookup via findAccessibilityNodeInfosByText("Cancel")
            val cancelMatches = try { root.findAccessibilityNodeInfosByText("Cancel") } catch (_: Exception) { null }
            if (!cancelMatches.isNullOrEmpty()) {
                for (match in cancelMatches) {
                    try {
                        val text = match.text?.toString()?.lowercase()?.trim() ?: ""
                        val desc = match.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                        if (text.contains("cancel") || desc.contains("cancel")) {
                            if (tryClickNodeOrParent(match)) {
                                Log.d(TAG, "Auto-clicked Cancel via findAccessibilityNodeInfosByText in 0ms")
                            }
                        }
                    } finally {
                        try { match.recycle() } catch (_: Exception) {}
                    }
                }
            }

            // Recursive traversal fallback for OEM dialog buttons (e.g. button2, deny, keep on)
            val candidateNodes = mutableListOf<AccessibilityNodeInfo>()
            fun searchNodes(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || depth > 30) return
                val text = node.text?.toString()?.lowercase()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val isCancelLabel = text == "cancel" || text == "deny" || text == "keep on" ||
                        text.contains("cancel") || desc == "cancel" || desc == "deny" ||
                        viewId.endsWith(":id/button2") || viewId.endsWith(":id/cancel")
                if (isCancelLabel) {
                    candidateNodes.add(node)
                }
                val count = try { node.childCount } catch (_: Exception) { 0 }
                for (i in 0 until count) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) {
                        searchNodes(child, depth + 1)
                    }
                }
            }
            searchNodes(root, 0)
            for (candidate in candidateNodes) {
                try {
                    if (tryClickNodeOrParent(candidate)) {
                        Log.d(TAG, "Auto-clicked Cancel button on confirmation dialog in 0ms")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed clicking cancel node", e)
                } finally {
                    if (candidate !== root) {
                        try { candidate.recycle() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error searching for cancel dialog button", e)
        }
    }

    private fun dismissTamperConfirmationAcrossWindows(primaryRoot: AccessibilityNodeInfo? = null) {
        dismissTamperConfirmationDialog(primaryRoot)
        try {
            val winList = windows
            if (!winList.isNullOrEmpty()) {
                for (win in winList) {
                    val winRoot = try { win.root } catch (_: Exception) { null }
                    if (winRoot != null) {
                        dismissTamperConfirmationDialog(winRoot)
                        try { winRoot.recycle() } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun isScrollContainerNode(node: AccessibilityNodeInfo?): Boolean {
        val cls = node?.className?.toString()?.lowercase() ?: return false
        return cls.contains("recyclerview") ||
                cls.contains("listview") ||
                cls.contains("scrollview") ||
                cls.contains("gridview") ||
                cls.contains("viewpager")
    }

    /**
     * Step A — Instant In-Memory Check (0.1ms, Zero Full-Tree IPC Delay):
     * Inspects event.text, event.contentDescription, event.className, and (on TYPE_VIEW_CLICKED)
     * event.source?.text BEFORE calling rootInActiveWindow.
     */
    private fun tryInstantStepATamperIntercept(
        event: AccessibilityEvent,
        eventType: Int,
        eventClassName: String
    ): Boolean {
        val eventTexts = ArrayList<String>(8)
        for (t in event.text) {
            t?.toString()?.takeIf { it.isNotBlank() }?.let { eventTexts.add(it) }
        }
        event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { eventTexts.add(it) }

        // On TYPE_VIEW_CLICKED, inspect event.source?.text (and immediate row children if text is empty) without traversing full window tree
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = try { event.source } catch (_: Exception) { null }
            if (source != null) {
                try {
                    if (!isScrollContainerNode(source)) {
                        source.text?.toString()?.takeIf { it.isNotBlank() }?.let { eventTexts.add(it) }
                        source.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { eventTexts.add(it) }
                        if (eventTexts.isEmpty()) {
                            val childCount = minOf(try { source.childCount } catch (_: Exception) { 0 }, 4)
                            for (i in 0 until childCount) {
                                val child = try { source.getChild(i) } catch (_: Exception) { null }
                                if (child != null) {
                                    try {
                                        child.text?.toString()?.takeIf { it.isNotBlank() }?.let { eventTexts.add(it) }
                                        child.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { eventTexts.add(it) }
                                    } finally {
                                        try { child.recycle() } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { source.recycle() } catch (_: Exception) {}
                }
            }
        }

        val eventTextsLower = eventTexts.map { it.lowercase().trim() }

        // Step C fast check if confirmation dialog text is in the event itself
        val hasTurnOffOrStop = eventTextsLower.any {
            it.contains("turn off") || it.contains("stop") || it.contains("deactivate") || it.contains("force stop")
        }
        if (hasTurnOffOrStop &&
            SettingsTamperDetector.hasAppOrFocusLockName(eventTextsLower, packageName, cachedAppLabel) &&
            !SettingsTamperDetector.isSafePermissionScreen(eventTextsLower)
        ) {
            val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            try {
                dismissTamperConfirmationAcrossWindows(activeRoot)
            } finally {
                try { activeRoot?.recycle() } catch (_: Exception) {}
            }
            triggerRapidSettingsExitUnwind()
            return true
        }

        if (SettingsTamperDetector.matchesInstantStepAEventTrigger(
                eventTexts = eventTexts,
                eventClassName = eventClassName,
                appPackageName = packageName,
                appLabel = cachedAppLabel
            )
        ) {
            // Do not accidentally block Focus Lock if user is on a whitelisted permission screen
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                SettingsTamperDetector.hasAppOrFocusLockName(eventTextsLower, packageName, cachedAppLabel)
            ) {
                // Only peek at root window if needed to ensure safe permission screens (e.g. Display over other apps list) stay open
                val quickRoot = try { rootInActiveWindow } catch (_: Exception) { null }
                try {
                    if (quickRoot != null && isSafePermissionWindowIndexed(quickRoot)) {
                        return false
                    }
                } finally {
                    try { quickRoot?.recycle() } catch (_: Exception) {}
                }
            }
            Log.w(TAG, "Step A 0.1ms anti-tamper triggered (type=$eventType, class=$eventClassName, texts=$eventTexts)")
            triggerRapidSettingsExitUnwind()
            return true
        }

        return false
    }

    private fun hasIndexedTextMatch(root: AccessibilityNodeInfo?, vararg queries: String): Boolean {
        if (root == null) return false
        for (query in queries) {
            if (query.isBlank()) continue
            val matches = try { root.findAccessibilityNodeInfosByText(query) } catch (_: Exception) { null }
            if (!matches.isNullOrEmpty()) {
                val queryLower = query.lowercase().trim()
                var matched = false
                for (node in matches) {
                    try {
                        val txt = node.text?.toString()?.lowercase()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                        if (txt.contains(queryLower) || desc.contains(queryLower)) {
                            matched = true
                        }
                    } finally {
                        try { node.recycle() } catch (_: Exception) {}
                    }
                }
                if (matched) return true
            }
        }
        return false
    }

    private fun isSafePermissionWindowIndexed(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (hasIndexedTextMatch(
                root,
                "Display over other apps",
                "Appear on top",
                "Permit usage access",
                "Usage access",
                "Notification access",
                "Alarms & reminders",
                "Alarms and reminders",
                "Battery optimization",
                "Ignore battery optimizations",
                "Auto-launch permission"
            )
        ) {
            return true
        }
        if (hasIndexedTextMatch(root, "Activate this device admin app") &&
            !hasIndexedTextMatch(root, "Deactivate")
        ) {
            return true
        }
        return false
    }

    private fun triggerRapidSettingsExitUnwind() {
        // Always fire an immediate 0ms BACK on every trigger so fast taps are canceled instantly
        performGlobalAction(GLOBAL_ACTION_BACK)

        if (!isUnwindingSettings.compareAndSet(false, true)) return

        Log.w(TAG, "Rapid Settings Exit Unwind triggered: 0ms BACK + 6 pulses (25, 55, 85, 115, 150, 190ms)")
        FocusHaptics.rejectOrDelete(this)

        // Fire 6 rapid back pulses so Android pops every Settings fragment all the way out to the Home screen
        val pulseDelays = longArrayOf(25L, 55L, 85L, 115L, 150L, 190L)
        for (delay in pulseDelays) {
            mainHandler.postDelayed({
                val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
                val currentPkg = activeRoot?.packageName?.toString() ?: ""
                try { activeRoot?.recycle() } catch (_: Exception) {}
                if (currentPkg.contains("settings", ignoreCase = true) || currentPkg.isEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, delay)
        }

        mainHandler.postDelayed({
            isUnwindingSettings.set(false)
        }, 220L)
    }

    private fun checkAndInterceptSettingsTamper(
        eventPkg: String? = null,
        eventClassName: String? = null
    ): Boolean {
        val isProtectionActive = isMasterEnabled ||
                com.example.util.KillswitchManager.isKillswitchActive(this) ||
                isCheatProtectionEnabled ||
                isInvincibleModeEnabled

        if (!isProtectionActive) return false

        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val rootPkg = root?.packageName?.toString()
        val isSettingsWindow = SettingsTamperDetector.isSettingsAppPackage(eventPkg) ||
                SettingsTamperDetector.isSettingsAppPackage(currentForegroundPackage) ||
                SettingsTamperDetector.isSettingsAppPackage(rootPkg) ||
                (eventPkg == "android" && SettingsTamperDetector.isSettingsAppPackage(currentForegroundPackage))

        if (!isSettingsWindow) {
            try { root?.recycle() } catch (_: Exception) {}
            return false
        }

        if (root == null) {
            if (SettingsTamperDetector.isAccessibilityServiceDetailFragment(eventClassName)) {
                triggerRapidSettingsExitUnwind()
                return true
            }
            return isUnwindingSettings.get()
        }

        try {
            // Safe Whitelist: NEVER interfere with permission grant screens
            if (isSafePermissionWindowIndexed(root)) {
                return false
            }

            // Step C: Instant "Cancel" click on confirmation dialog ("Turn off" / "Stop" + "Focus Lock")
            val hasFocusLockNode = hasIndexedTextMatch(root, cachedAppLabel, "Focus Lock")
            if (hasFocusLockNode && hasIndexedTextMatch(root, "Turn off", "Stop", "Deactivate")) {
                Log.w(TAG, "Step C Confirmation dialog detected! Clicking Cancel and unwinding Settings.")
                dismissTamperConfirmationAcrossWindows(root)
                triggerRapidSettingsExitUnwind()
                return true
            }

            if (isUnwindingSettings.get()) return true

            // Guard against normal Settings -> Apps manager (All apps / App management / Manage apps)
            val isNormalAppsManager = SettingsTamperDetector.isNonAccessibilityRootExitClass(eventClassName) ||
                (hasIndexedTextMatch(root, "All apps", "App management", "Manage apps", "See all apps", "Permission manager") &&
                    !hasIndexedTextMatch(root, "Downloaded apps", "Downloaded services", "Installed services"))
            if (isNormalAppsManager) {
                return false
            }

            // Guard against general main Accessibility screen when NOT viewing Downloaded apps or Focus Lock detail
            val isMainAccessibilityScreen = hasIndexedTextMatch(
                root,
                "Accessibility Menu",
                "Press Power button to end calls",
                "Visibility enhancements",
                "Hearing enhancements",
                "Display size and text",
                "Color and motion",
                "Interaction controls"
            )

            // Step B — Fast Shallow Window Fallback (For Reopening From Recents & OEM Skins):
            val hasDownloadedAppsOrServices = hasIndexedTextMatch(
                root,
                "Downloaded apps",
                "Downloaded services",
                "Installed services"
            )
            val hasInstalledApps = hasIndexedTextMatch(root, "Installed apps")
            val hasAccessibilityOrToggleContext = hasIndexedTextMatch(
                root,
                "Accessibility",
                "Use Focus Lock",
                "Use service",
                "Shortcut",
                "Focus Lock uses this service",
                "About this function",
                "Auto-launch management"
            )

            if (!isMainAccessibilityScreen &&
                (hasDownloadedAppsOrServices || (hasInstalledApps && (hasAccessibilityOrToggleContext || hasFocusLockNode)))
            ) {
                Log.w(TAG, "Step B shallow fallback triggered on Downloaded apps / Installed services")
                triggerRapidSettingsExitUnwind()
                return true
            }

            // Focus Lock inside Accessibility toggle/detail, App Info (Force stop/Uninstall), or Device Admin
            val hasFocusLockDetailOrDangerousAction = hasFocusLockNode && (
                (!isMainAccessibilityScreen && hasAccessibilityOrToggleContext) ||
                    hasIndexedTextMatch(root, "Use Focus Lock", "Use service", "Focus Lock uses this service", "About this function") ||
                    hasIndexedTextMatch(root, "Force stop", "Uninstall", "Deactivate")
            )
            if (hasFocusLockDetailOrDangerousAction) {
                Log.w(TAG, "Step B shallow fallback triggered on Focus Lock tamper screen")
                triggerRapidSettingsExitUnwind()
                return true
            }

            // Signature D — Date & Time tamper protection
            if (hasIndexedTextMatch(root, "Automatic date & time", "Automatic date and time", "Use network-provided time") ||
                (hasIndexedTextMatch(root, "Date & time", "Date and time") &&
                    hasIndexedTextMatch(root, "Time zone", "Set time", "Set date"))
            ) {
                Log.w(TAG, "Step B shallow fallback triggered on System Date & Time tamper screen")
                triggerRapidSettingsExitUnwind()
                return true
            }
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }

        return false
    }

    /**
     * Intercepts phone manufacturer "Hide Apps" loophole screens across OEM Launchers,
     * Security apps, and native System Settings.
     */
    private fun checkAndInterceptHideApps(event: AccessibilityEvent): Boolean {
        if (com.example.util.EmergencyBreakManager.isBreakActive(this)) return false
        // ONLY execute the "Hide apps" text scanner if the package name contains:
        // "settings", "launcher", "security", or "safecenter"
        val eventPkg = event.packageName?.toString()
        if (!ContentScanner.isHideAppsTargetPackage(eventPkg)) {
            return false
        }

        // Step 1: Extract text from AccessibilityEvent
        for (cs in event.text) {
            val text = cs?.toString() ?: continue
            if (ContentScanner.isHideAppsString(text)) {
                triggerHideAppIntercept()
                return true
            }
        }
        val eventDesc = event.contentDescription?.toString()
        if (eventDesc != null && ContentScanner.isHideAppsString(eventDesc)) {
            triggerHideAppIntercept()
            return true
        }

        // Step 2: Extract text from rootInActiveWindow
        val now = SystemClock.elapsedRealtime()
        // Throttle TYPE_WINDOW_CONTENT_CHANGED window node searches to prevent scrolling lag
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastHideAppScanTime < 250) {
                return false
            }
        }
        lastHideAppScanTime = now

        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return false
        val rootPkg = root.packageName?.toString()
        // Ensure root window also belongs to target system packages, avoiding false positives on chat apps/browsers
        if (!ContentScanner.isHideAppsTargetPackage(rootPkg)) {
            return false
        }

        try {
            // 2A: Query indexed texts using findAccessibilityNodeInfosByText
            val targetSearchQueries = listOf("Hide apps", "Hidden apps", "App Hider")
            for (query in targetSearchQueries) {
                val matchedNodes = root.findAccessibilityNodeInfosByText(query)
                if (!matchedNodes.isNullOrEmpty()) {
                    for (node in matchedNodes) {
                        try {
                            val nodeText = node.text?.toString()
                            val nodeDesc = node.contentDescription?.toString()
                            if (ContentScanner.isHideAppsString(nodeText) || ContentScanner.isHideAppsString(nodeDesc)) {
                                triggerHideAppIntercept()
                                return true
                            }
                        } finally {
                            try { node.recycle() } catch (_: Exception) {}
                        }
                    }
                }
            }

            // 2B: Shallow bounded traversal (max depth 10, max 60 nodes) for non-indexed custom OEM views
            if (scanNodeTreeForHideApps(root, depth = 0, maxDepth = 10, count = intArrayOf(0), maxNodes = 60)) {
                triggerHideAppIntercept()
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning window for hide apps", e)
        }

        return false
    }

    private fun scanNodeTreeForHideApps(
        node: AccessibilityNodeInfo?,
        depth: Int,
        maxDepth: Int,
        count: IntArray,
        maxNodes: Int
    ): Boolean {
        if (node == null || depth > maxDepth || count[0] >= maxNodes) return false
        count[0]++

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (ContentScanner.isHideAppsString(text) || ContentScanner.isHideAppsString(desc)) {
            return true
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = scanNodeTreeForHideApps(child, depth + 1, maxDepth, count, maxNodes)
                try { child.recycle() } catch (_: Exception) {}
                if (found) return true
            }
        }
        return false
    }

    private fun showQuickFeedback(customMessage: String? = null) {
        val message = customMessage ?: MotivationLibrary.getRandomQuickFeedback(cachedReclaimedCommitment)
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(
                    applicationContext,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show quick feedback toast", e)
            }
        }
    }

    private fun triggerHideAppIntercept() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHideAppInterceptTime < 1500) {
            return
        }
        lastHideAppInterceptTime = now

        Log.w(TAG, "Hide Apps screen intercepted! Forcefully ejecting user.")

        // Step A: Immediately execute performGlobalAction(GLOBAL_ACTION_HOME) or performGlobalAction(GLOBAL_ACTION_BACK)
        val homeKicked = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!homeKicked) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        // Additional safeguard: send HOME intent
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start HOME intent fallback", e)
        }

        // Step B: Show Category B Quick Toast / HUD with MotivationLibrary line
        showQuickFeedback()
    }

    private fun handleEventInBackground(eventPkg: String?, eventType: Int) {
        val myPackageName = packageName
        TimeIntegrityManager.getTrustedCurrentTimeMillis(this)

        // Invincible Mode Intercept: prevent hiding apps via Launchers/Security apps/Settings
        if (isInvincibleModeEnabled && ContentScanner.isHideAppsTargetPackage(eventPkg)) {
            val root = try { rootInActiveWindow } catch (e: Exception) { null }
            val rootPkg = root?.packageName?.toString()
            if (root != null && ContentScanner.isHideAppsTargetPackage(rootPkg)) {
                try {
                    val hideKeywords = listOf("hide apps", "hidden apps", "hide application", "app hider")
                    var foundHideMenu = false
                    for (keyword in hideKeywords) {
                        val foundNodes = root.findAccessibilityNodeInfosByText(keyword)
                        if (!foundNodes.isNullOrEmpty()) {
                            foundHideMenu = true
                            break
                        }
                    }
                    
                    if (foundHideMenu) {
                        Log.d(TAG, "Invincible Mode: Intercepting hide apps menu in $eventPkg")
                        triggerHideAppIntercept()
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invincible Mode: Failed to scan launcher nodes", e)
                }
            }
        }

        // =========================================================================
        // 1. BLOCKING ENFORCEMENT: Evaluate visible packages
        // =========================================================================
        val visiblePackages = getAllVisiblePackages(eventPkg)
        for (pkg in visiblePackages) {
            if (pkg != myPackageName) {
                checkForegroundPackage(pkg)
            }
        }

        // =========================================================================
        // 2. TIME TRACKING: Attribute usage ONLY to the window with input focus!
        // Re-evaluate which window is focused on every relevant accessibility event
        // so the timer hands off correctly as input moves between apps, while
        // unfocused apps (e.g. video playback in floating window) do not steal focus.
        // =========================================================================
        val focusedPkg = getCurrentlyFocusedPackage() ?: when (eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (!eventPkg.isNullOrEmpty() && !isSystemOverlay(eventPkg)) eventPkg else currentForegroundPackage
            }
            else -> currentForegroundPackage // Unfocused floating windows or background state changes NEVER steal focus
        }

        if (!focusedPkg.isNullOrEmpty()) {
            val targetPkg = if (focusedPkg == myPackageName) myPackageName else focusedPkg
            if (currentForegroundPackage != targetPkg) {
                Log.d(TAG, "Input focus handed off from '$currentForegroundPackage' to '$targetPkg'")
            }
            currentForegroundPackage = targetPkg
            FocusForegroundService.onForegroundPackageChanged(targetPkg)
        }

        // =========================================================================
        // 3. CONTENT SCANNING (Website & Keyword blocking):
        // =========================================================================
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastScanTime > 250) {
                lastScanTime = now
                scanVisibleWindowsForContent()
            }
        }
    }

    private fun isPackageRestricted(pkgName: String): Boolean {
        if (cachedBlockedPackages.contains(pkgName) || blockedAppsByPackage.containsKey(pkgName)) {
            return true
        }
        val trueNow = TimeIntegrityManager.getTrueTimeMillis()
        val limit = screenLimitsByPackage[pkgName]
        if (limit != null) {
            if (limit.cooldownEndTimestamp > trueNow) {
                return true
            }
            if (limit.dailyLimitMinutes > 0) {
                val today = TimeIntegrityManager.getTrueDateString()
                val todayUsed = todayUsageCache[pkgName]
                    ?: (if (limit.lastResetDate == today) limit.usedMinutesToday else 0)
                if (todayUsed >= limit.dailyLimitMinutes) return true
            }
        }
        val groupIdsForApp = groupIdsByPackage[pkgName]
        if (!groupIdsForApp.isNullOrEmpty()) {
            val cal = TimeIntegrityManager.getTrueCalendar()
            for (gId in groupIdsForApp) {
                val group = groupsById[gId] ?: continue
                if (group.sessionLimitEnabled && group.cooldownEndTimestamp > trueNow) {
                    return true
                }
                if (GroupRuleEvaluator.isScheduleActive(group, cal) ||
                    GroupRuleEvaluator.isBudgetExhausted(group)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkForegroundPackage(pkgName: String) {
        if (pkgName == packageName || pkgName == "com.android.settings") return

        // 0. The Emergency Failsafe System: 10-Minute Hard Cap
        if (com.example.util.EmergencyBreakManager.isBreakActive(this)) {
            return
        }

        // Debounce to prevent multiple triggers in short succession
        val now = SystemClock.elapsedRealtime()
        if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1500) {
            return
        }

        // 1. Hard Blocked Apps (O(1) instant map or set lookup)
        val hardBlocked = blockedAppsByPackage[pkgName]
        if (hardBlocked != null || cachedBlockedPackages.contains(pkgName)) {
            triggerBlockScreen(pkgName)
            return
        }

        val trueNow = TimeIntegrityManager.getTrueTimeMillis()

        // 2. Session Cooldown Locks (Checked before standard daily limits)
        val limit = screenLimitsByPackage[pkgName]
        if (limit != null && limit.cooldownEndTimestamp > trueNow) {
            val remainingMs = limit.cooldownEndTimestamp - trueNow
            executeBlock(
                pkgName = pkgName,
                title = getAppNameFromPackage(pkgName),
                reason = "Session limit reached. Take a break (${com.example.util.SessionLimitHelper.formatRemaining(remainingMs)} remaining).",
                type = BlockedActivity.TYPE_SESSION_COOLDOWN,
                cooldownEndTime = limit.cooldownEndTimestamp
            )
            return
        }

        val groupIdsForApp = groupIdsByPackage[pkgName]
        if (!groupIdsForApp.isNullOrEmpty()) {
            for (gId in groupIdsForApp) {
                val group = groupsById[gId] ?: continue
                if (group.sessionLimitEnabled && group.cooldownEndTimestamp > trueNow) {
                    val remainingMs = group.cooldownEndTimestamp - trueNow
                    executeBlock(
                        pkgName = pkgName,
                        title = group.name,
                        reason = "Group session limit reached. Break active across all apps (${com.example.util.SessionLimitHelper.formatRemaining(remainingMs)} remaining).",
                        type = BlockedActivity.TYPE_SESSION_COOLDOWN,
                        cooldownEndTime = group.cooldownEndTimestamp
                    )
                    return
                }
            }
        }

        // 3. Screen Time Limits (Daily limits)
        if (limit != null && limit.dailyLimitMinutes > 0) {
            val today = TimeIntegrityManager.getTrueDateString()
            val todayUsed = todayUsageCache[pkgName]
                ?: (if (limit.lastResetDate == today) limit.usedMinutesToday else 0)
            if (todayUsed >= limit.dailyLimitMinutes) {
                val h = limit.dailyLimitMinutes / 60
                val m = limit.dailyLimitMinutes % 60
                val limitStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                executeBlock(
                    pkgName = pkgName,
                    title = getAppNameFromPackage(pkgName),
                    reason = "Daily screen time limit ($limitStr) has been reached.",
                    type = BlockedActivity.TYPE_SCREEN_TIME
                )
                return
            }
        }

        // 4. Group Rules (O(1) indexed lookups instead of list filtering)
        if (!groupIdsForApp.isNullOrEmpty()) {
            val cal = TimeIntegrityManager.getTrueCalendar()
            for (gId in groupIdsForApp) {
                val group = groupsById[gId] ?: continue

                // Schedule check
                if (GroupRuleEvaluator.isScheduleActive(group, cal)) {
                    executeBlock(
                        pkgName = pkgName,
                        title = group.name,
                        reason = "Active schedule window (${group.scheduleStart} – ${group.scheduleEnd}). Focus lock is in effect.",
                        type = BlockedActivity.TYPE_GROUP_SCHEDULE,
                        nextWindow = group.scheduleEnd
                    )
                    return
                }

                // Budget check
                if (GroupRuleEvaluator.isBudgetExhausted(group)) {
                    val h = group.dailyBudgetMinutes / 60
                    val m = group.dailyBudgetMinutes % 60
                    val budgetStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                    executeBlock(
                        pkgName = pkgName,
                        title = group.name,
                        reason = "Daily group budget ($budgetStr) across all apps in this group has been exhausted.",
                        type = BlockedActivity.TYPE_GROUP_BUDGET
                    )
                    return
                }
            }
        }
    }

    private fun scanVisibleWindowsForContent() {
        if (AppForegroundTracker.isFocusLockInForeground) return
        if (!isMasterEnabled) return
        if (compiledWebsites.isEmpty() && compiledKeywords.isEmpty()) return

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (win in windowList) {
                    if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val winRoot = win.root ?: continue
                        val winPkg = winRoot.packageName?.toString() ?: continue
                        if (winPkg != packageName && !isSystemOverlay(winPkg) && !isInputMethod(winPkg)) {
                            scanContentOnScreen(winPkg, winRoot)
                        }
                    }
                }
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning visible windows for content", e)
        }

        if (AppForegroundTracker.isFocusLockInForeground) return
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg != packageName && !isSystemOverlay(rootPkg) && !isInputMethod(rootPkg)) {
            scanContentOnScreen(rootPkg, root)
        }
    }

    private fun scanContentOnScreen(pkgName: String, rootNode: AccessibilityNodeInfo? = null) {
        if (com.example.util.EmergencyBreakManager.isBreakActive(this)) return
        if (AppForegroundTracker.isFocusLockInForeground) return
        if (pkgName == packageName || isInputMethod(pkgName)) return
        val root = rootNode ?: try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return

        if (root.packageName?.toString() == packageName) return

        try {
            // Fix 2.2: Obtain visible screen bounds to filter out off-screen/pre-rendered views
            val displayMetrics = resources.displayMetrics
            val screenBounds = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)

            val currentWebsites = compiledWebsites
            val currentKeywords = compiledKeywords

            // Fast-path: Check recognized browser address bar
            if (currentWebsites.isNotEmpty()) {
                val urlBarText = ContentScanner.extractBrowserUrl(root, pkgName)
                if (urlBarText != null) {
                    for ((rule, regex) in currentWebsites) {
                        val match = regex.find(urlBarText)
                        if (match != null) {
                            onWebsiteBlockTriggered(
                                pkgName,
                                ContentScanner.WebsiteMatchResult(
                                    rule = rule,
                                    matchedSnippet = match.value,
                                    sourceText = urlBarText,
                                    source = ContentScanner.TextSource.URL_BAR
                                )
                            )
                            return
                        }
                    }
                }
            }

            // Full deep text scan across active window filtered by visible screen bounds
            val screenNodes = ContentScanner.extractScreenNodes(root, screenBounds = screenBounds, maxDepth = 35)
            if (screenNodes.isEmpty()) return

            // 1. Check blocked websites in text content (with pre-compiled word-boundary regexes)
            if (currentWebsites.isNotEmpty()) {
                for (item in screenNodes) {
                    for ((rule, regex) in currentWebsites) {
                        val match = regex.find(item.text)
                        if (match != null) {
                            onWebsiteBlockTriggered(
                                pkgName,
                                ContentScanner.WebsiteMatchResult(
                                    rule = rule,
                                    matchedSnippet = match.value,
                                    sourceText = item.text,
                                    source = item.source
                                )
                            )
                            return
                        }
                    }
                }
            }

            // 2. Check blocked keywords in text content (with pre-compiled word-boundary regexes)
            if (currentKeywords.isNotEmpty()) {
                for (item in screenNodes) {
                    for ((rule, regex) in currentKeywords) {
                        val match = regex.find(item.text)
                        if (match != null) {
                            onKeywordBlockTriggered(
                                pkgName,
                                ContentScanner.KeywordMatchResult(
                                    rule = rule,
                                    matchedSnippet = match.value,
                                    sourceText = item.text,
                                    source = item.source
                                )
                            )
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning content on screen", e)
        }
    }

    private fun onKeywordBlockTriggered(pkgName: String, match: ContentScanner.KeywordMatchResult) {
        val snippet = match.matchedSnippet
        val sourceDesc = match.source.description

        Log.w(
            TAG,
            "=== RESTRICTED KEYWORD DETECTED ===\n" +
            "Keyword Rule: \"${match.rule.keyword}\"\n" +
            "Matched Snippet: \"$snippet\"\n" +
            "Source: $sourceDesc\n" +
            "Target App: $pkgName"
        )

        val now = SystemClock.elapsedRealtime()
        if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1500) {
            return
        }
        lastBlockedTime = now
        lastBlockedPackage = pkgName

        // Record intercept via InterceptTracker
        com.example.util.InterceptTracker.recordKeywordOrWebIntercept(this, "Keyword: \"${match.rule.keyword}\"")

        // Eject user from the view displaying the restricted keyword
        val backed = performGlobalAction(GLOBAL_ACTION_BACK)
        if (!backed) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // Category B: Quick Toast / HUD Snackbars (Use QUICK_FEEDBACK_LINES)
        showQuickFeedback()
    }

    private fun onWebsiteBlockTriggered(pkgName: String, match: ContentScanner.WebsiteMatchResult) {
        val snippet = match.matchedSnippet
        val sourceDesc = match.source.description

        Log.w(
            TAG,
            "=== BLOCK TRIGGERED (WEBSITE) ===\n" +
            "Website Rule: \"${match.rule.domainOrUrl}\"\n" +
            "Matched Snippet: \"$snippet\"\n" +
            "Source: $sourceDesc\n" +
            "Target App: $pkgName"
        )

        // Record intercept via InterceptTracker
        com.example.util.InterceptTracker.recordKeywordOrWebIntercept(this, match.rule.domainOrUrl)

        // Category A: Full-Screen Block Overlay (Use FULL_SCREEN_QUOTES)
        executeBlock(
            pkgName = pkgName,
            title = match.rule.domainOrUrl,
            reason = "Restricted website \"$snippet\" detected via $sourceDesc.",
            type = BlockedActivity.TYPE_WEBSITE
        )
    }

    private inline fun runOnMainImmediate(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            serviceScope.launch(Dispatchers.Main.immediate) {
                action()
            }
        }
    }

    private fun triggerBlockScreen(packageName: String) {
        if (packageName == "com.android.settings") return

        val now = SystemClock.elapsedRealtime()
        if (lastBlockedPackage == packageName && now - lastBlockedTime < 1000) {
            return
        }
        lastBlockedPackage = packageName
        lastBlockedTime = now

        // Record intercept via InterceptTracker
        com.example.util.InterceptTracker.recordAppIntercept(this, packageName)

        val appTitle = getAppNameFromPackage(packageName)
        val reason = "This app is in your blocked apps list."
        val quote = MotivationLibrary.getRandomFullScreenQuote()

        // 0ms Simultaneous Execution on Main Thread:
        // Collapse/close restricted app and attach WindowManager overlay in the exact same synchronous step.
        runOnMainImmediate {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)

            if (!OverlayManager.isShowingOverlay()) {
                OverlayManager.showOverlay(
                    context = this,
                    title = appTitle,
                    reason = reason,
                    type = BlockedActivity.TYPE_APP,
                    quote = quote,
                    isAccessibilityOverlay = true
                )
            }
        }

        // Failsafe against persistent pinned floating windows that resist the initial Home action
        scheduleFloatingWindowFailsafe(packageName, appTitle, reason, BlockedActivity.TYPE_APP, quote)
    }

    private fun executeBlock(
        pkgName: String,
        title: String,
        reason: String,
        type: String,
        nextWindow: String? = null,
        cooldownEndTime: Long = 0L
    ) {
        if (pkgName == packageName || pkgName == "com.android.settings") return

        val now = SystemClock.elapsedRealtime()
        if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1000) {
            return
        }

        lastBlockedTime = now
        lastBlockedPackage = pkgName

        // Record intercept via InterceptTracker
        if (type == BlockedActivity.TYPE_WEBSITE) {
            com.example.util.InterceptTracker.recordKeywordOrWebIntercept(this, title)
        } else {
            com.example.util.InterceptTracker.recordAppIntercept(this, pkgName)
        }

        val quote = MotivationLibrary.getRandomFullScreenQuote()

        // 0ms Simultaneous Execution on Main Thread:
        // Close restricted app and attach WindowManager overlay in the exact same synchronous step.
        runOnMainImmediate {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)

            if (!OverlayManager.isShowingOverlay()) {
                OverlayManager.showOverlay(
                    context = this,
                    title = title,
                    reason = reason,
                    type = type,
                    quote = quote,
                    nextWindow = nextWindow,
                    isAccessibilityOverlay = true,
                    cooldownEndTime = cooldownEndTime
                )
            }
        }

        // Failsafe against persistent pinned floating windows that resist the initial Home action
        scheduleFloatingWindowFailsafe(pkgName, title, reason, type, quote, nextWindow, cooldownEndTime)
    }

    private fun scheduleFloatingWindowFailsafe(
        pkgName: String,
        title: String,
        reason: String,
        type: String,
        quote: String,
        nextWindow: String? = null,
        cooldownEndTime: Long = 0L
    ) {
        mainHandler.postDelayed({
            val stillBlocked = findBlockedPackageInVisibleWindows(null)
            if (stillBlocked != null) {
                Log.w(TAG, "Floating window ($stillBlocked) still on screen 250ms after block! Re-enforcing Home ejection.")
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)

                if (!OverlayManager.isShowingOverlay()) {
                    OverlayManager.showOverlay(
                        context = this,
                        title = title,
                        reason = reason,
                        type = type,
                        quote = quote,
                        nextWindow = nextWindow,
                        isAccessibilityOverlay = true,
                        cooldownEndTime = cooldownEndTime
                    )
                }

                // Re-check after another 250ms if user somehow kept pinned floating window alive
                mainHandler.postDelayed({
                    val persistentBlocked = findBlockedPackageInVisibleWindows(null)
                    if (persistentBlocked != null) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }, 250L)
            }
        }, 250L)
    }

    private fun getAppNameFromPackage(pkgName: String): String {
        return appNameCache.getOrPut(pkgName) {
            try {
                val pm = packageManager
                val info = pm.getApplicationInfo(pkgName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                pkgName
            }
        }
    }

    fun handleEmergencyBreakExpired() {
        Log.w(TAG, "Emergency break expired! Immediately re-evaluating active window to enforce restrictions.")
        serviceScope.launch(Dispatchers.Main) {
            val rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
            val currentPkg = rootNode?.packageName?.toString() ?: currentForegroundPackage
            if (!currentPkg.isNullOrEmpty() && currentPkg != packageName && !isSystemOverlay(currentPkg)) {
                if (isPackageRestricted(currentPkg)) {
                    Log.w(TAG, "User still inside restricted app ($currentPkg) after break expiration! Forcefully ejecting.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    triggerBlockScreen(currentPkg)
                } else if (rootNode != null) {
                    scanContentOnScreen(currentPkg, rootNode)
                }
            }
        }
    }

    private var lastKillswitchLaunchTime = 0L

    fun launchMainActivityForKillswitch() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastKillswitchLaunchTime > 1500L) {
            lastKillswitchLaunchTime = now
            try {
                val intent = Intent(this, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch MainActivity for Killswitch", e)
            }
        }
    }

    fun handleKillswitchBreakExpired() {
        Log.w(TAG, "Killswitch emergency break expired! Immediately re-evaluating active window.")
        serviceScope.launch(Dispatchers.Main) {
            val rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
            val currentPkg = rootNode?.packageName?.toString() ?: currentForegroundPackage ?: getCurrentlyFocusedPackage()
            if (!currentPkg.isNullOrEmpty() && !com.example.util.KillswitchManager.isAllowedKillswitchPackage(this@FocusAccessibilityService, currentPkg)) {
                Log.w(TAG, "User still inside non-allowed app ($currentPkg) after Killswitch break expiration! Forcefully ejecting.")
                performGlobalAction(GLOBAL_ACTION_HOME)
                launchMainActivityForKillswitch()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService interrupted")
    }
}
