package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
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

import com.example.ui.overlay.OverlayManager
import com.example.util.PermissionHelper

class FocusAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicScanJob: Job? = null

    // Local cached in-memory rules for fast checks
    @Volatile private var blockedAppsCache = listOf<BlockedApp>()
    @Volatile private var screenLimitsCache = listOf<ScreenTimeLimit>()
    @Volatile private var groupsCache = listOf<AppGroup>()
    @Volatile private var groupAppsCache = listOf<GroupApp>()
    @Volatile private var blockedWebsitesCache = listOf<BlockedWebsite>()
    @Volatile private var blockedKeywordsCache = listOf<BlockedKeyword>()
    @Volatile private var isMasterEnabled = true
    @Volatile private var isInvincibleModeEnabled = false
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

    private var lastBlockedTime = 0L
    private var lastBlockedPackage: String? = null
    private var lastScanTime = 0L
    private var lastEvaluatedPackage: String? = null
    private var lastHideAppInterceptTime = 0L
    private var lastHideAppScanTime = 0L

    companion object {
        private const val TAG = "FocusAccessibility"
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        @Volatile
        private var instance: FocusAccessibilityService? = null

        @Volatile
        var currentForegroundPackage: String? = null
            private set

        fun getActiveFocusedPackage(): String? {
            return instance?.getCurrentlyFocusedPackage() ?: currentForegroundPackage
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true
        currentForegroundPackage = null
        lastEvaluatedPackage = null
        Log.d(TAG, "FocusAccessibilityService connected")

        // Start listening to database changes to maintain warm memory caches
        observeDatabaseRules()

        // Start lightweight periodic rescan backstop for feed-style apps and coalesced events
        startPeriodicContentScanner()

        // Also ensure foreground service is running
        FocusForegroundService.startService(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
        currentForegroundPackage = null
        lastEvaluatedPackage = null
        FocusForegroundService.onForegroundPackageChanged(null)
        periodicScanJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "FocusAccessibilityService destroyed")
    }

    private fun startPeriodicContentScanner() {
        periodicScanJob?.cancel()
        periodicScanJob = serviceScope.launch(Dispatchers.Default) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            while (isActive) {
                delay(350) // Roughly 300–500ms lightweight periodic rescan

                if (!isMasterEnabled) continue

                // Pauses immediately when screen is off
                val isInteractive = powerManager?.isInteractive ?: true
                if (!isInteractive) continue

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

    private fun observeDatabaseRules() {
        val app = application as? FocusApplication ?: return
        val repo = app.repository
        val prefRepo = app.preferencesRepository

        // Database reads explicitly dispatched on Dispatchers.IO
        serviceScope.launch(Dispatchers.IO) {
            repo.allBlockedApps.collect { list ->
                blockedAppsCache = list
                withContext(Dispatchers.Default) {
                    blockedAppsByPackage = list.associateBy { it.packageName }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) {
            repo.allScreenTimeLimits.collect { list ->
                screenLimitsCache = list
                withContext(Dispatchers.Default) {
                    screenLimitsByPackage = list.associateBy { it.packageName }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) {
            repo.allGroups.collect { list ->
                groupsCache = list
                withContext(Dispatchers.Default) {
                    groupsById = list.associateBy { it.id }
                }
            }
        }
        serviceScope.launch(Dispatchers.IO) {
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
        serviceScope.launch(Dispatchers.IO) {
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
        serviceScope.launch(Dispatchers.IO) {
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
        serviceScope.launch(Dispatchers.IO) {
            prefRepo.isMasterEnabled.collect { isMasterEnabled = it }
        }
        serviceScope.launch(Dispatchers.IO) {
            prefRepo.isInvincibleModeEnabled.collect { isInvincibleModeEnabled = it }
        }
        serviceScope.launch(Dispatchers.IO) {
            prefRepo.activeEmergencyBreakUntil.collect { until ->
                activeEmergencyBreakUntil = until
                scheduleEmergencySnapback(until)
            }
        }
        serviceScope.launch(Dispatchers.IO) {
            prefRepo.reclaimedCommitment.collect { cachedReclaimedCommitment = it }
        }
        serviceScope.launch(Dispatchers.IO) {
            repo.getTodayUsageLogs().collect { logs ->
                todayUsageCache = logs.associate { it.packageName to it.minutesUsed }
            }
        }
    }

    private var snapbackJob: Job? = null

    private fun scheduleEmergencySnapback(until: Long) {
        snapbackJob?.cancel()
        val delayMs = until - System.currentTimeMillis()
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
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                // Priority 1: Application window with input focus (isFocused == true)
                val focusedAppWindow = windowList.firstOrNull {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
                }
                val focusedAppPkg = focusedAppWindow?.root?.packageName?.toString()
                if (!focusedAppPkg.isNullOrEmpty() && !isSystemOverlay(focusedAppPkg)) {
                    return focusedAppPkg
                }

                // Priority 2: Any non-IME, non-system focused window
                val nonImeFocused = windowList.firstOrNull {
                    it.isFocused &&
                    it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                    it.type != AccessibilityWindowInfo.TYPE_SYSTEM
                }
                val nonImePkg = nonImeFocused?.root?.packageName?.toString()
                if (!nonImePkg.isNullOrEmpty() && !isSystemOverlay(nonImePkg)) {
                    return nonImePkg
                }

                // Priority 3: Active application window (e.g. app hosting active IME keyboard)
                val activeAppWindow = windowList.firstOrNull {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
                }
                val activePkg = activeAppWindow?.root?.packageName?.toString()
                if (!activePkg.isNullOrEmpty() && !isSystemOverlay(activePkg)) {
                    return activePkg
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect windows for input focus", e)
        }

        val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
        val activePkg = activeRoot?.packageName?.toString()
        if (!activePkg.isNullOrEmpty() && !isSystemOverlay(activePkg)) {
            return activePkg
        }
        return null
    }

    /**
     * Collects all packages visible on screen across all windows (base app, split-screen, floating windows).
     * Blocking enforcement MUST evaluate EVERY visible window, focused or not.
     */
    private fun getAllVisiblePackages(eventPkg: String?): Set<String> {
        val result = mutableSetOf<String>()
        if (!eventPkg.isNullOrEmpty() && !isSystemOverlay(eventPkg)) {
            result.add(eventPkg)
        }

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (win in windowList) {
                    if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val pkg = win.root?.packageName?.toString()
                        if (!pkg.isNullOrEmpty() && !isSystemOverlay(pkg)) {
                            result.add(pkg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect visible window packages", e)
        }

        val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
        val activePkg = activeRoot?.packageName?.toString()
        if (!activePkg.isNullOrEmpty() && !isSystemOverlay(activePkg)) {
            result.add(activePkg)
        }

        return result
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isMasterEnabled) return

        val myPackageName = packageName
        val eventPkg = event.packageName?.toString()
        val className = event.className?.toString() ?: ""
        val eventType = event.eventType

        // Exclude BlockedActivity explicitly (do not scan or block the block screen itself)
        if (className.contains("BlockedActivity")) {
            currentForegroundPackage = myPackageName
            FocusForegroundService.onForegroundPackageChanged(myPackageName)
            return
        }

        // Fast-path: Focus Lock itself is in foreground
        if (eventPkg == myPackageName) {
            currentForegroundPackage = myPackageName
            FocusForegroundService.onForegroundPackageChanged(myPackageName)
            return
        }

        // -------------------------------------------------------------------------
        // 0. OS "HIDE APPS" TEXT INTERCEPTOR:
        // Blocks user from accessing OEM Settings or Launcher "Hide Apps" loophole
        // -------------------------------------------------------------------------
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            if (checkAndInterceptHideApps(event)) {
                return
            }
        }

        // Offload window traversal, visible package collection, blocking checks, focus evaluation,
        // and text scanning to background thread (Dispatchers.Default) to eliminate UI thread jitter!
        serviceScope.launch(Dispatchers.Default) {
            handleEventInBackground(eventPkg, eventType)
        }
    }

    /**
     * Intercepts phone manufacturer "Hide Apps" loophole screens across OEM Launchers,
     * Security apps, and native System Settings.
     */
    private fun checkAndInterceptHideApps(event: AccessibilityEvent): Boolean {
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
        val now = SystemClock.uptimeMillis()
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

    private var lastSettingsTamperTime = 0L

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
        val now = SystemClock.uptimeMillis()
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

    private fun triggerSettingsTamperIntercept(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastSettingsTamperTime < 1500) {
            return
        }
        lastSettingsTamperTime = now

        Log.w(TAG, "Critical settings tamper intercepted ($reason)! Forcefully ejecting user.")

        val homeKicked = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!homeKicked) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start HOME intent for settings tamper", e)
        }

        // Category B: Quick Toast / HUD with MotivationLibrary line
        showQuickFeedback()
    }

    private fun handleEventInBackground(eventPkg: String?, eventType: Int) {
        val myPackageName = packageName

        // Invincible Mode Intercept: prevent disabling device admin, uninstallation, revoking accessibility, or changing time
        if (isInvincibleModeEnabled && eventPkg == "com.android.settings") {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                    // Quick check if the current settings screen mentions our app by name or package
                    val foundNodesByName = root.findAccessibilityNodeInfosByText(appName)
                    val foundNodesByPkg = root.findAccessibilityNodeInfosByText(myPackageName)
                    
                    if (foundNodesByName.isNotEmpty() || foundNodesByPkg.isNotEmpty()) {
                        Log.d(TAG, "Invincible Mode: Intercepting settings access to $appName")
                        triggerSettingsTamperIntercept("Disabling permissions/admin for $appName")
                        return
                    }

                    // Check for Date & Time tampering attempts
                    val timeKeywords = listOf("Date & time", "Date and time", "Set time", "Automatic date & time")
                    var foundTimeTamper = false
                    for (tk in timeKeywords) {
                        val nodes = root.findAccessibilityNodeInfosByText(tk)
                        if (!nodes.isNullOrEmpty()) {
                            foundTimeTamper = true
                            break
                        }
                    }
                    if (foundTimeTamper) {
                        Log.d(TAG, "Invincible Mode: Intercepting Date & Time tampering")
                        triggerSettingsTamperIntercept("Date & Time tampering")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invincible Mode: Failed to scan settings nodes", e)
            }
        }

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
            val now = SystemClock.uptimeMillis()
            if (now - lastScanTime > 250) {
                lastScanTime = now
                scanVisibleWindowsForContent()
            }
        }
    }

    private fun checkForegroundPackage(pkgName: String) {
        if (pkgName == packageName) return

        // 0. The Emergency Failsafe System: 10-Minute Hard Cap
        // If an active 10-minute emergency break is currently running, bypass the block.
        // Once the 10 minutes elapse, the break expires silently and access is terminated immediately (The Abrupt Snapback).
        if (System.currentTimeMillis() < activeEmergencyBreakUntil) {
            return
        }

        // Debounce to prevent multiple triggers in short succession
        val now = SystemClock.uptimeMillis()
        if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1500) {
            return
        }

        // 1. Hard Blocked Apps (O(1) instant map lookup instead of linear scan)
        val hardBlocked = blockedAppsByPackage[pkgName]
        if (hardBlocked != null) {
            executeBlock(
                pkgName = pkgName,
                title = hardBlocked.appName,
                reason = "This app is in your blocked apps list.",
                type = BlockedActivity.TYPE_APP
            )
            return
        }

        // 2. Screen Time Limits (O(1) instant map lookup instead of linear scan)
        val limit = screenLimitsByPackage[pkgName]
        if (limit != null && limit.dailyLimitMinutes > 0) {
            val today = FocusRepository.getTodayDateString()
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

        // 3. Group Rules (O(1) indexed lookups instead of list filtering)
        val groupIdsForApp = groupIdsByPackage[pkgName]
        if (!groupIdsForApp.isNullOrEmpty()) {
            val cal = Calendar.getInstance()
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
        if (!isMasterEnabled) return
        if (compiledWebsites.isEmpty() && compiledKeywords.isEmpty()) return

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (win in windowList) {
                    if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val winRoot = win.root ?: continue
                        val winPkg = winRoot.packageName?.toString() ?: continue
                        if (winPkg != packageName && !isSystemOverlay(winPkg)) {
                            scanContentOnScreen(winPkg, winRoot)
                        }
                    }
                }
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning visible windows for content", e)
        }

        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        val rootPkg = root.packageName?.toString() ?: return
        if (rootPkg != packageName && !isSystemOverlay(rootPkg)) {
            scanContentOnScreen(rootPkg, root)
        }
    }

    private fun scanContentOnScreen(pkgName: String, rootNode: AccessibilityNodeInfo? = null) {
        if (pkgName == packageName) return
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

        val now = SystemClock.uptimeMillis()
        if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1500) {
            return
        }
        lastBlockedTime = now
        lastBlockedPackage = pkgName

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

        // Category A: Full-Screen Block Overlay (Use FULL_SCREEN_QUOTES)
        executeBlock(
            pkgName = pkgName,
            title = match.rule.domainOrUrl,
            reason = "Restricted website \"$snippet\" detected via $sourceDesc.",
            type = BlockedActivity.TYPE_WEBSITE
        )
    }

    private fun executeBlock(
        pkgName: String,
        title: String,
        reason: String,
        type: String,
        nextWindow: String? = null
    ) {
        serviceScope.launch(Dispatchers.Main) {
            if (pkgName == packageName) return@launch

            val now = SystemClock.uptimeMillis()
            if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1500) {
                return@launch
            }

            lastBlockedTime = now
            lastBlockedPackage = pkgName

            val quote = MotivationLibrary.getRandomFullScreenQuote()

            // If Display Over Other Apps permission is granted, draw WindowManager overlay
            if (PermissionHelper.canDrawOverlays(this@FocusAccessibilityService)) {
                OverlayManager.showOverlay(
                    context = this@FocusAccessibilityService,
                    title = title,
                    reason = reason,
                    type = type,
                    quote = quote,
                    nextWindow = nextWindow
                )
            } else {
                // Fallback: Launch full-screen BlockedActivity displaying quote and 4-second pattern interrupt
                try {
                    val intent = Intent(this@FocusAccessibilityService, BlockedActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(BlockedActivity.EXTRA_TITLE, title)
                        putExtra(BlockedActivity.EXTRA_REASON, reason)
                        putExtra(BlockedActivity.EXTRA_TYPE, type)
                        putExtra(BlockedActivity.EXTRA_PACKAGE, pkgName)
                        putExtra(BlockedActivity.EXTRA_NEXT_WINDOW, nextWindow)
                        putExtra(BlockedActivity.EXTRA_QUOTE, quote)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch BlockedActivity", e)
                }
            }
        }
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

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService interrupted")
    }
}
