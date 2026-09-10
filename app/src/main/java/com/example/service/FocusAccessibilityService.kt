package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.FocusApplication
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.ui.blocked.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class FocusAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Local cached in-memory rules for fast checks
    @Volatile private var blockedAppsCache = listOf<BlockedApp>()
    @Volatile private var screenLimitsCache = listOf<ScreenTimeLimit>()
    @Volatile private var groupsCache = listOf<AppGroup>()
    @Volatile private var groupAppsCache = listOf<GroupApp>()
    @Volatile private var blockedWebsitesCache = listOf<BlockedWebsite>()
    @Volatile private var blockedKeywordsCache = listOf<BlockedKeyword>()
    @Volatile private var isMasterEnabled = true

    private var lastBlockedTime = 0L
    private var lastBlockedPackage: String? = null
    private var lastUrlScanTime = 0L
    private var lastKeywordScanTime = 0L

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
        Log.d(TAG, "FocusAccessibilityService connected")

        // Start listening to database changes to maintain warm memory caches
        observeDatabaseRules()

        // Also ensure foreground service is running
        FocusForegroundService.startService(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        serviceScope.cancel()
        Log.d(TAG, "FocusAccessibilityService destroyed")
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isMasterEnabled) return

        val pkgName = event.packageName?.toString() ?: return

        // NEVER block or scan Focus Lock itself
        if (pkgName == packageName) return

        val eventType = event.eventType

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentForegroundPackage = pkgName
            FocusForegroundService.onForegroundPackageChanged(pkgName)
            checkForegroundPackage(pkgName)
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val now = SystemClock.uptimeMillis()

            // Website scanning (in browser apps)
            if (blockedWebsitesCache.isNotEmpty() && (now - lastUrlScanTime > 350)) {
                lastUrlScanTime = now
                checkBrowserUrl(pkgName)
            }

            // Keyword scanning
            if (blockedKeywordsCache.isNotEmpty() && (now - lastKeywordScanTime > 600)) {
                lastKeywordScanTime = now
                checkKeywordsOnScreen(pkgName)
            }
        }
    }

    private fun checkForegroundPackage(pkgName: String) {
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

        // 2. Screen Time Limits (Soft limits)
        val limit = screenLimitsCache.firstOrNull { it.packageName == pkgName }
        if (limit != null && limit.dailyLimitMinutes > 0 && limit.usedMinutesToday >= limit.dailyLimitMinutes) {
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

    private fun checkBrowserUrl(pkgName: String) {
        val root = rootInActiveWindow ?: return
        try {
            // First check recognized browser address bar
            val urlText = ContentScanner.extractBrowserUrl(root, pkgName)
            if (urlText != null) {
                val matched = ContentScanner.matchBlockedWebsite(urlText, blockedWebsitesCache)
                if (matched != null) {
                    executeBlock(
                        pkgName = pkgName,
                        title = matched.domainOrUrl,
                        reason = "This website is in your blocked websites list.",
                        type = BlockedActivity.TYPE_WEBSITE
                    )
                    return
                }
            }

            // Fallback for other browsers: check text nodes for blocked domains
            if (ContentScanner.isKnownBrowser(pkgName) || pkgName.contains("browser") || pkgName.contains("chrome")) {
                val allTexts = ContentScanner.extractAllScreenText(root, maxDepth = 6)
                for (text in allTexts) {
                    val matched = ContentScanner.matchBlockedWebsite(text, blockedWebsitesCache)
                    if (matched != null) {
                        executeBlock(
                            pkgName = pkgName,
                            title = matched.domainOrUrl,
                            reason = "This website is in your blocked websites list.",
                            type = BlockedActivity.TYPE_WEBSITE
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking browser URL", e)
        }
    }

    private fun checkKeywordsOnScreen(pkgName: String) {
        val root = rootInActiveWindow ?: return
        try {
            val allTexts = ContentScanner.extractAllScreenText(root, maxDepth = 10)
            val matchedKeyword = ContentScanner.matchBlockedKeyword(allTexts, blockedKeywordsCache)
            if (matchedKeyword != null) {
                executeBlock(
                    pkgName = pkgName,
                    title = "\"${matchedKeyword.keyword}\"",
                    reason = "This screen contains a blocked keyword phrase.",
                    type = BlockedActivity.TYPE_KEYWORD
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keywords on screen", e)
        }
    }

    private fun executeBlock(
        pkgName: String,
        title: String,
        reason: String,
        type: String,
        nextWindow: String? = null
    ) {
        lastBlockedTime = SystemClock.uptimeMillis()
        lastBlockedPackage = pkgName

        // 1. Immediately dismiss foreground app via back then home
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // 2. Launch full-screen Blocked Activity
        try {
            val intent = Intent(this, BlockedActivity::class.java).apply {
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
