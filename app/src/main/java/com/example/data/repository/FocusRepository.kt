package com.example.data.repository

import com.example.data.local.dao.FocusDao
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.DailyScreenTime
import com.example.data.local.entity.DailyUsageLog
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.util.ScreenTimeHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.Collections

class FocusRepository(private val focusDao: FocusDao) {

    companion object {
        fun getTodayDateString(): String = ScreenTimeHelper.getTodayDateString()
    }

    private val _currentDateFlow = MutableStateFlow(getTodayDateString())
    val currentDateFlow: StateFlow<String> = _currentDateFlow.asStateFlow()

    fun updateCurrentDate(newDate: String = getTodayDateString()) {
        if (_currentDateFlow.value != newDate) {
            _currentDateFlow.value = newDate
        }
    }

    // Packages that Focus Lock has actively live-tracked with single-window input focus today
    private val liveTrackedPackagesToday = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var serviceStartTimeToday: Long = 0L

    fun setServiceStartTime(timeMillis: Long) {
        if (serviceStartTimeToday == 0L) {
            serviceStartTimeToday = timeMillis
        }
    }

    fun markPackageAsLiveTracked(packageName: String) {
        liveTrackedPackagesToday.add(packageName)
    }

    fun isPackageLiveTracked(packageName: String): Boolean = liveTrackedPackagesToday.contains(packageName)

    fun resetDailyTrackingState() {
        liveTrackedPackagesToday.clear()
        serviceStartTimeToday = System.currentTimeMillis()
        updateCurrentDate()
    }

    // Blocked Apps
    val allBlockedApps: Flow<List<BlockedApp>> = focusDao.getAllBlockedApps()
    suspend fun getAllBlockedAppsSync(): List<BlockedApp> = focusDao.getAllBlockedAppsSync()
    suspend fun blockApp(packageName: String, appName: String) =
        focusDao.insertBlockedApp(BlockedApp(packageName = packageName, appName = appName))
    suspend fun unblockApp(packageName: String) = focusDao.deleteBlockedApp(packageName)
    suspend fun isAppBlocked(packageName: String): Boolean = focusDao.isAppBlocked(packageName)

    // Screen Time Limits
    val allScreenTimeLimits: Flow<List<ScreenTimeLimit>> = focusDao.getAllScreenTimeLimits()
    suspend fun getAllScreenTimeLimitsSync(): List<ScreenTimeLimit> = focusDao.getAllScreenTimeLimitsSync()
    suspend fun setScreenTimeLimit(packageName: String, dailyLimitMinutes: Int) {
        val today = getTodayDateString()
        // Single source of truth: enforce against the app's total daily usage counter
        val todayUsage = focusDao.getUsageLog(packageName, today)?.minutesUsed ?: 0
        focusDao.insertOrUpdateScreenTimeLimit(
            ScreenTimeLimit(
                packageName = packageName,
                dailyLimitMinutes = dailyLimitMinutes,
                usedMinutesToday = todayUsage,
                lastResetDate = today
            )
        )
    }
    suspend fun removeScreenTimeLimit(packageName: String) = focusDao.deleteScreenTimeLimit(packageName)
    suspend fun updateScreenTimeUsage(packageName: String, usedMinutes: Int, date: String) =
        focusDao.updateScreenTimeUsage(packageName, usedMinutes, date)

    suspend fun resetDailyLimitsIfNeeded() {
        val today = getTodayDateString()
        val allLimits = focusDao.getAllScreenTimeLimitsSync()
        for (limit in allLimits) {
            if (limit.lastResetDate != today) {
                val todayUsage = focusDao.getUsageLog(limit.packageName, today)?.minutesUsed ?: 0
                focusDao.updateScreenTimeUsage(limit.packageName, todayUsage, today)
            }
        }
        val allGroups = focusDao.getAllGroupsSync()
        for (group in allGroups) {
            if (group.lastResetDate != today) {
                focusDao.updateGroupUsage(group.id, 0, today)
            }
        }
    }

    // Groups
    val allGroups: Flow<List<AppGroup>> = focusDao.getAllGroups()
    val allGroupApps: Flow<List<GroupApp>> = focusDao.getAllGroupApps()
    suspend fun getAllGroupsSync(): List<AppGroup> = focusDao.getAllGroupsSync()
    suspend fun getAllGroupAppsSync(): List<GroupApp> = focusDao.getAllGroupAppsSync()
    suspend fun getAppsForGroupSync(groupId: Long): List<String> = focusDao.getAppsForGroupSync(groupId)
    fun getAppsForGroup(groupId: Long): Flow<List<String>> = focusDao.getAppsForGroup(groupId)

    suspend fun saveGroup(group: AppGroup, packageNames: List<String>): Long {
        val today = getTodayDateString()
        val preparedGroup = if (group.lastResetDate != today) {
            group.copy(lastResetDate = today, usedMinutesToday = 0)
        } else {
            group
        }
        val id = if (preparedGroup.id == 0L) {
            focusDao.insertGroup(preparedGroup)
        } else {
            focusDao.updateGroup(preparedGroup)
            preparedGroup.id
        }
        focusDao.replaceAppsForGroup(id, packageNames)
        return id
    }

    suspend fun deleteGroup(groupId: Long) = focusDao.deleteGroupAndApps(groupId)
    suspend fun updateGroupUsage(groupId: Long, usedMinutes: Int, date: String) =
        focusDao.updateGroupUsage(groupId, usedMinutes, date)

    // Websites
    val allBlockedWebsites: Flow<List<BlockedWebsite>> = focusDao.getAllBlockedWebsites()
    suspend fun getAllBlockedWebsitesSync(): List<BlockedWebsite> = focusDao.getAllBlockedWebsitesSync()
    suspend fun addBlockedWebsite(domainOrUrl: String) =
        focusDao.insertBlockedWebsite(BlockedWebsite(domainOrUrl = domainOrUrl.trim()))
    suspend fun removeBlockedWebsite(id: Long) = focusDao.deleteBlockedWebsite(id)

    // Keywords
    val allBlockedKeywords: Flow<List<BlockedKeyword>> = focusDao.getAllBlockedKeywords()
    suspend fun getAllBlockedKeywordsSync(): List<BlockedKeyword> = focusDao.getAllBlockedKeywordsSync()
    suspend fun addBlockedKeyword(keyword: String, caseSensitive: Boolean = false) =
        focusDao.insertBlockedKeyword(BlockedKeyword(keyword = keyword.trim(), caseSensitive = caseSensitive))
    suspend fun removeBlockedKeyword(id: Long) = focusDao.deleteBlockedKeyword(id)

    // Daily Usage Logs & Device Screen-On Time
    fun getUsageLogsForDate(date: String): Flow<List<DailyUsageLog>> = focusDao.getUsageLogsForDate(date)

    fun getTodayUsageLogs(): Flow<List<DailyUsageLog>> =
        currentDateFlow.flatMapLatest { date ->
            focusDao.getUsageLogsForDate(date)
        }

    val recentDailyScreenTimes: Flow<List<DailyScreenTime>> = focusDao.getRecentDailyScreenTimes()
    val allRecentUsageLogs: Flow<List<DailyUsageLog>> = focusDao.getAllRecentUsageLogs()

    suspend fun getUsageLogSync(packageName: String, date: String = getTodayDateString()): DailyUsageLog? =
        focusDao.getUsageLog(packageName, date)

    suspend fun getUsageLogsForDateSync(date: String = getTodayDateString()): List<DailyUsageLog> =
        focusDao.getUsageLogsForDateSync(date)

    /**
     * Today's Total Screen Time: returns the true physical device screen-on time for today.
     * Calculated from UsageEvents ACTIVITY_RESUMED and ACTIVITY_PAUSED intervals,
     * merging overlapping times across the device.
     */
    fun getTotalMinutesUsedToday(): Flow<Int> =
        currentDateFlow.flatMapLatest { date ->
            focusDao.getScreenOnMinutesForDate(date).map { screenOn ->
                screenOn ?: 0
            }
        }

    fun getDeviceScreenOnMinutesToday(): Flow<Int> =
        currentDateFlow.flatMapLatest { date ->
            focusDao.getScreenOnMinutesForDate(date).map { minutes ->
                minutes ?: 0
            }
        }

    suspend fun getDeviceScreenOnMinutesTodaySync(): Int {
        val recorded = focusDao.getScreenOnMinutesForDateSync(getTodayDateString())
        return recorded ?: 0
    }

    suspend fun updateDeviceScreenOnTime(minutes: Int, date: String = getTodayDateString()) {
        val sanitized = maxOf(0, minutes)
        focusDao.insertOrUpdateScreenOnTime(DailyScreenTime(date = date, screenOnMinutes = sanitized))
    }

    /**
     * Idempotent absolute write of an app's daily usage total.
     * Overwrites the stored value with [totalMinutes] — never adds on top of what's already stored.
     */
    suspend fun setAppUsageAbsolute(
        packageName: String,
        totalMinutes: Int,
        appName: String = "",
        iconBase64: String? = null,
        date: String = getTodayDateString()
    ): Int {
        markPackageAsLiveTracked(packageName)
        val finalMinutes = maxOf(0, totalMinutes)
        val screenOnToday = getDeviceScreenOnMinutesTodaySync()
        // Individual apps cannot exceed total screen time
        val boundedMinutes = if (screenOnToday > 0) minOf(finalMinutes, screenOnToday) else finalMinutes

        val existing = focusDao.getUsageLog(packageName, date)
        val beforeMinutes = existing?.minutesUsed ?: 0

        val name = if (appName.isNotBlank()) appName else existing?.appName ?: packageName
        val icon = iconBase64 ?: existing?.iconBase64

        android.util.Log.d(
            "FocusUsageTracker",
            "[LIVE-TRACK] $packageName before: ${beforeMinutes}m -> writing absolute: ${boundedMinutes}m"
        )

        focusDao.insertOrUpdateDailyUsageLog(
            DailyUsageLog(
                packageName = packageName,
                date = date,
                minutesUsed = boundedMinutes,
                appName = name,
                iconBase64 = icon
            )
        )
        // Keep ScreenTimeLimit's usedMinutesToday in sync with daily usage log
        focusDao.updateScreenTimeUsage(packageName, boundedMinutes, date)

        val confirmed = focusDao.getUsageLog(packageName, date)?.minutesUsed
        android.util.Log.d(
            "FocusUsageTracker",
            "[LIVE-TRACK] $packageName confirmed in DB: ${confirmed}m"
        )

        return boundedMinutes
    }

    suspend fun logAppUsage(
        packageName: String,
        minutesToAdd: Int,
        appName: String = "",
        iconBase64: String? = null,
        date: String = getTodayDateString()
    ): Int {
        val existing = focusDao.getUsageLog(packageName, date)
        val newTotal = (existing?.minutesUsed ?: 0) + minutesToAdd
        return setAppUsageAbsolute(packageName, newTotal, appName, iconBase64, date)
    }

    /**
     * Reconciles usage stats from system:
     * 1. Reconciles non-overlapping device screen-on time strictly from UsageEvents (SCREEN_INTERACTIVE - SCREEN_NON_INTERACTIVE).
     * 2. Reconciles individual app foreground time strictly from UsageEvents (ACTIVITY_RESUMED - ACTIVITY_PAUSED).
     * 3. Dangling sessions are added up to current timestamp.
     * 4. Enforces rule that individual app usage cannot exceed total screen time.
     */
    suspend fun syncUsageStatsFromSystem(context: android.content.Context) {
        if (!com.example.util.PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val todayDate = getTodayDateString()
                updateCurrentDate(todayDate)

                // 1. Query UsageEvents strictly from local midnight to System.currentTimeMillis()
                val usageData = ScreenTimeHelper.queryUsageEventsToday(context) ?: return@withContext

                val osScreenOn = usageData.totalScreenOnMinutes
                updateDeviceScreenOnTime(osScreenOn, todayDate)

                val appUsageStatsMap = usageData.appUsageMillis
                val existingLogs = focusDao.getUsageLogsForDateSync(todayDate).associateBy { it.packageName }
                val pm = context.packageManager

                // 2. Filter System Apps: Filter out background system processes like 'com.google.android.permissioncontroller'
                // or 'com.android.launcher' so they don't clog the UI. Remove any stale/corrupt system rows from DB.
                for ((pkg, _) in existingLogs) {
                    if (ScreenTimeHelper.isIgnoredSystemPackage(pkg, context)) {
                        focusDao.deleteUsageLog(pkg, todayDate)
                    }
                }

                for ((pkg, totalForegroundMillis) in appUsageStatsMap) {
                    // Filter out background system processes
                    if (ScreenTimeHelper.isIgnoredSystemPackage(pkg, context)) {
                        continue
                    }

                    // Authoritative individual app time from UsageEvents (ACTIVITY_RESUMED to ACTIVITY_PAUSED)
                    val osMinutes = (totalForegroundMillis / 60000L).toInt()
                    if (osMinutes <= 0) {
                        if (existingLogs.containsKey(pkg)) {
                            focusDao.deleteUsageLog(pkg, todayDate)
                        }
                        continue
                    }

                    // Architectural Rule: Individual apps cannot exceed total screen time
                    val finalMinutes = if (osScreenOn > 0) minOf(osMinutes, osScreenOn) else osMinutes

                    val existingLog = existingLogs[pkg]
                    var appName = existingLog?.appName ?: ""
                    var iconBase64 = existingLog?.iconBase64

                    if (appName.isBlank() || iconBase64 == null) {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            if (appName.isBlank()) {
                                appName = pm.getApplicationLabel(appInfo).toString()
                            }
                            if (iconBase64 == null) {
                                val drawable = pm.getApplicationIcon(appInfo)
                                iconBase64 = com.example.util.ImageUtil.drawableToBase64(drawable)
                            }
                        } catch (e: Exception) {
                            if (appName.isBlank()) {
                                appName = pkg
                            }
                        }
                    }

                    android.util.Log.d(
                        "FocusUsageTracker",
                        "[SYNC-EVENTS] $pkg -> ACTIVITY_RESUMED to PAUSED: ${finalMinutes}m (raw: ${osMinutes}m, screenOn: ${osScreenOn}m)"
                    )

                    focusDao.insertOrUpdateDailyUsageLog(
                        DailyUsageLog(
                            packageName = pkg,
                            date = todayDate,
                            minutesUsed = finalMinutes,
                            appName = appName,
                            iconBase64 = iconBase64
                        )
                    )

                    focusDao.updateScreenTimeUsage(pkg, finalMinutes, todayDate)
                    com.example.service.FocusForegroundService.updateBaselineForPackage(pkg, finalMinutes)
                }

                // Clean up any stale logs for apps that now have 0 OS usage and are not actively live tracked
                for ((pkg, _) in existingLogs) {
                    if (!appUsageStatsMap.containsKey(pkg) && !isPackageLiveTracked(pkg)) {
                        focusDao.deleteUsageLog(pkg, todayDate)
                    }
                }

                // Reconcile group budgets
                val allGroups = focusDao.getAllGroupsSync()
                val groupApps = focusDao.getAllGroupAppsSync()
                val updatedLogs = focusDao.getUsageLogsForDateSync(todayDate).associateBy { it.packageName }

                for (group in allGroups) {
                    if (group.budgetEnabled) {
                        val memberPkgs = groupApps.filter { it.groupId == group.id }.map { it.packageName }
                        val totalGroupMinutes = memberPkgs.sumOf { memberPkg -> updatedLogs[memberPkg]?.minutesUsed ?: 0 }
                        focusDao.updateGroupUsage(group.id, totalGroupMinutes, todayDate)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FocusUsageTracker", "Error syncing usage stats from system", e)
            }
        }
    }

    private fun isSystemOverlay(pkg: String, context: android.content.Context? = null): Boolean {
        return if (context != null) {
            ScreenTimeHelper.isIgnoredSystemPackage(pkg, context)
        } else {
            pkg == "com.android.systemui" ||
            pkg.contains("launcher") ||
            pkg.contains("permissioncontroller") ||
            pkg.contains("inputmethod") ||
            pkg.contains(".ime") ||
            pkg == "android"
        }
    }
}
