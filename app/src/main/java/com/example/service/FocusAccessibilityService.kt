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
import android.widget.Toast
import com.example.FocusApplication
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.data.repository.FocusRepository
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
import java.util.Calendar

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
    @Volatile private var todayUsageCache = mapOf<String, Int>()

    private var lastBlockedTime = 0L
    private var lastBlockedPackage: String? = null
    private var lastScanTime = 0L
    private var lastEvaluatedPackage: String? = null

    companion object {
        private const val TAG = "FocusAccessibility"
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        @Volatile
        var currentForegroundPackage: String? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
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

                scanContentOnScreen(currentPkg)
            }
        }
    }

    private fun observeDatabaseRules() {
        val app = application as? FocusApplication ?: return
        val repo = app.repository
        val prefRepo = app.preferencesRepository

        serviceScope.launch {
            repo.allBlockedApps.collect { blockedAppsCache = it }
        }
        serviceScope.launch {
            repo.allScreenTimeLimits.collect { screenLimitsCache = it }
        }
        serviceScope.launch {
            repo.allGroups.collect { groupsCache = it }
        }
        serviceScope.launch {
            repo.allGroupApps.collect { groupAppsCache = it }
        }
        serviceScope.launch {
            repo.allBlockedWebsites.collect { blockedWebsitesCache = it }
        }
        serviceScope.launch {
            repo.allBlockedKeywords.collect { blockedKeywordsCache = it }
        }
        serviceScope.launch {
            prefRepo.isMasterEnabled.collect { isMasterEnabled = it }
        }
        serviceScope.launch {
            repo.getUsageLogsForDate(FocusRepository.getTodayDateString()).collect { logs ->
                todayUsageCache = logs.associate { it.packageName to it.minutesUsed }
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isMasterEnabled) return

        val myPackageName = packageName

        // 1. Identify event package and active window package
        val eventPkg = event.packageName?.toString()
        val rootNode = rootInActiveWindow
        val activeWindowPkg = rootNode?.packageName?.toString()

        // Extract candidate package, prioritizing the active app over system overlays (keyboards, system UI)
        val detectedPkg = when {
            !eventPkg.isNullOrEmpty() && !isSystemOverlay(eventPkg) -> eventPkg
            !activeWindowPkg.isNullOrEmpty() && !isSystemOverlay(activeWindowPkg) -> activeWindowPkg
            !eventPkg.isNullOrEmpty() -> eventPkg
            !activeWindowPkg.isNullOrEmpty() -> activeWindowPkg
            else -> return
        }

        val className = event.className?.toString() ?: ""

        // Exclude BlockedActivity explicitly (do not scan or block the block screen itself)
        if (className.contains("BlockedActivity")) {
            currentForegroundPackage = myPackageName
            FocusForegroundService.onForegroundPackageChanged(myPackageName)
            return
        }

        // Log detected foreground package on every accessibility event as requested for debugging
        Log.d(TAG, "Foreground event: detectedPkg=$detectedPkg (eventPkg=$eventPkg, windowPkg=$activeWindowPkg, type=${event.eventType}, class=$className)")

        // 2. Absolute self-exclusion for Focus Lock's own application ID (Change 1)
        if (detectedPkg == myPackageName) {
            currentForegroundPackage = myPackageName
            FocusForegroundService.onForegroundPackageChanged(myPackageName)
            Log.d(TAG, "Focus Lock is foreground. Bypassing scan/block.")
            return
        }

        // 3. For any other app: Reassign and propagate foreground package immediately on every event
        currentForegroundPackage = detectedPkg
        FocusForegroundService.onForegroundPackageChanged(detectedPkg)

        val eventType = event.eventType

        // 4. Enforce app blocking, screen time limits, and group rules
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            lastEvaluatedPackage != detectedPkg
        ) {
            lastEvaluatedPackage = detectedPkg
            checkForegroundPackage(detectedPkg)
        }

        // 5. Website scanning and keyword scanning across foreground app
        // Triggered on window transitions, content updates, scrolls (e.g. Reddit feeds), and focus shifts
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScanTime > 250) {
                lastScanTime = now
                serviceScope.launch(Dispatchers.Default) {
                    scanContentOnScreen(detectedPkg)
                }
            }
        }
    }

    private fun checkForegroundPackage(pkgName: String) {
        if (pkgName == packageName) return

        // Debounce to prevent multiple triggers in short succession
        val now = SystemClock.uptimeMillis()
        if (lastBlockedPackage == pkgName && now - lastBlockedTime < 1500) {
            return
        }

        // 1. Hard Blocked Apps
        val hardBlocked = blockedAppsCache.firstOrNull { it.packageName == pkgName }
        if (hardBlocked != null) {
            executeBlock(
                pkgName = pkgName,
                title = hardBlocked.appName,
                reason = "This app is in your blocked apps list.",
                type = BlockedActivity.TYPE_APP
            )
            return
        }

        // 2. Screen Time Limits (Soft limits - enforced against single shared daily usage)
        val limit = screenLimitsCache.firstOrNull { it.packageName == pkgName }
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

        // 3. Group Rules (Schedule or Shared Budget)
        val groupIdsForApp = groupAppsCache.filter { it.packageName == pkgName }.map { it.groupId }
        if (groupIdsForApp.isNotEmpty()) {
            val cal = Calendar.getInstance()
            for (gId in groupIdsForApp) {
                val group = groupsCache.firstOrNull { it.id == gId } ?: continue

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

    private fun scanContentOnScreen(pkgName: String) {
        if (pkgName == packageName) return
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return

        if (root.packageName?.toString() == packageName) return

        try {
            // Fix 2.2: Obtain visible screen bounds to filter out off-screen/pre-rendered views
            val displayMetrics = resources.displayMetrics
            val screenBounds = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)

            // Fast-path: Check recognized browser address bar
            if (blockedWebsitesCache.isNotEmpty()) {
                val urlBarText = ContentScanner.extractBrowserUrl(root, pkgName)
                if (urlBarText != null) {
                    val urlItem = ContentScanner.ScannedNodeText(
                        text = urlBarText,
                        source = ContentScanner.TextSource.URL_BAR,
                        isUserAuthored = true
                    )
                    val matched = ContentScanner.findWebsiteMatch(listOf(urlItem), blockedWebsitesCache)
                    if (matched != null) {
                        onWebsiteBlockTriggered(pkgName, matched)
                        return
                    }
                }
            }

            // Full deep text scan across active window filtered by visible screen bounds
            val screenNodes = ContentScanner.extractScreenNodes(root, screenBounds = screenBounds, maxDepth = 35)
            if (screenNodes.isEmpty()) return

            // 1. Check blocked websites in text content (with word-boundary matching)
            if (blockedWebsitesCache.isNotEmpty()) {
                val matchedWebsite = ContentScanner.findWebsiteMatch(screenNodes, blockedWebsitesCache)
                if (matchedWebsite != null) {
                    onWebsiteBlockTriggered(pkgName, matchedWebsite)
                    return
                }
            }

            // 2. Check blocked keywords in text content (with word-boundary matching & contentDescription refinement)
            if (blockedKeywordsCache.isNotEmpty()) {
                val matchedKeyword = ContentScanner.findKeywordMatch(screenNodes, blockedKeywordsCache)
                if (matchedKeyword != null) {
                    onKeywordBlockTriggered(pkgName, matchedKeyword)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning content on screen", e)
        }
    }

    private fun onKeywordBlockTriggered(pkgName: String, match: ContentScanner.KeywordMatchResult) {
        val snippet = match.matchedSnippet
        val sourceDesc = match.source.description
        val contextSnippet = match.sourceText.replace("\n", " ").take(100)

        // Fix 2.4: Temporary debug log showing exact keyword, matched text, source, and context
        Log.w(
            TAG,
            "=== BLOCK TRIGGERED (KEYWORD) ===\n" +
            "Keyword Rule: \"${match.rule.keyword}\"\n" +
            "Matched Snippet: \"$snippet\"\n" +
            "Source: $sourceDesc\n" +
            "Context Text: \"$contextSnippet\"\n" +
            "Target App: $pkgName"
        )

        // Fix 2.4: On-screen toast fired immediately so exact trigger cause is visible in real-time
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "Focus Lock: Blocked keyword \"$snippet\"\nSource: $sourceDesc\nContext: \"$contextSnippet\"",
                Toast.LENGTH_LONG
            ).show()
        }

        executeBlock(
            pkgName = pkgName,
            title = "\"${match.rule.keyword}\"",
            reason = "Blocked keyword \"$snippet\" detected via $sourceDesc.",
            type = BlockedActivity.TYPE_KEYWORD
        )
    }

    private fun onWebsiteBlockTriggered(pkgName: String, match: ContentScanner.WebsiteMatchResult) {
        val snippet = match.matchedSnippet
        val sourceDesc = match.source.description
        val contextSnippet = match.sourceText.replace("\n", " ").take(100)

        // Fix 2.4: Temporary debug log for website blocks
        Log.w(
            TAG,
            "=== BLOCK TRIGGERED (WEBSITE) ===\n" +
            "Website Rule: \"${match.rule.domainOrUrl}\"\n" +
            "Matched Snippet: \"$snippet\"\n" +
            "Source: $sourceDesc\n" +
            "Context Text: \"$contextSnippet\"\n" +
            "Target App: $pkgName"
        )

        // Fix 2.4: On-screen toast for website blocks
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "Focus Lock: Blocked website \"$snippet\"\nSource: $sourceDesc",
                Toast.LENGTH_LONG
            ).show()
        }

        executeBlock(
            pkgName = pkgName,
            title = match.rule.domainOrUrl,
            reason = "Blocked website \"$snippet\" detected via $sourceDesc.",
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

            // 1. Perform back action to leave whatever content triggered the block
            performGlobalAction(GLOBAL_ACTION_BACK)

            // 2. Launch full-screen 3-second black interstitial (Change 5)
            try {
                val intent = Intent(this@FocusAccessibilityService, BlockedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(BlockedActivity.EXTRA_TITLE, title)
                    putExtra(BlockedActivity.EXTRA_REASON, reason)
                    putExtra(BlockedActivity.EXTRA_TYPE, type)
                    putExtra(BlockedActivity.EXTRA_PACKAGE, pkgName)
                    putExtra(BlockedActivity.EXTRA_NEXT_WINDOW, nextWindow)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch BlockedActivity", e)
            }
        }
    }

    private fun getAppNameFromPackage(pkgName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkgName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkgName
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService interrupted")
    }
}
