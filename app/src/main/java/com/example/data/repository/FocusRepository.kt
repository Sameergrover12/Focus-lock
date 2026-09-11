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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class FocusRepository(private val focusDao: FocusDao) {

    companion object {
        fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
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

    suspend fun getUsageLogSync(packageName: String, date: String = getTodayDateString()): DailyUsageLog? =
        focusDao.getUsageLog(packageName, date)

    suspend fun getUsageLogsForDateSync(date: String = getTodayDateString()): List<DailyUsageLog> =
        focusDao.getUsageLogsForDateSync(date)

    /**
     * Today's Total Screen Time: returns the absolute physical device screen-on time for today.
     * Guaranteed never to exceed the elapsed minutes since midnight.
     * Immune to multi-window, split-screen, or floating-window multiplication.
     */
    fun getTotalMinutesUsedToday(): Flow<Int> =
        focusDao.getScreenOnMinutesForDate(getTodayDateString()).map { screenOn ->
            val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()
            (screenOn ?: 0).coerceIn(0, maxAllowed)
        }

    fun getDeviceScreenOnMinutesToday(): Flow<Int> =
        focusDao.getScreenOnMinutesForDate(getTodayDateString()).map { minutes ->
            val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()
            (minutes ?: 0).coerceIn(0, maxAllowed)
        }

    suspend fun getDeviceScreenOnMinutesTodaySync(): Int {
        val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()
        val recorded = focusDao.getScreenOnMinutesForDateSync(getTodayDateString())
        return (recorded ?: 0).coerceIn(0, maxAllowed)
    }

    suspend fun updateDeviceScreenOnTime(minutes: Int, date: String = getTodayDateString()) {
        val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()
        val sanitized = minutes.coerceIn(0, maxAllowed)
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
        val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()
        val finalMinutes = totalMinutes.coerceIn(0, maxAllowed)

        val existing = focusDao.getUsageLog(packageName, date)
        val beforeMinutes = existing?.minutesUsed ?: 0

        val name = if (appName.isNotBlank()) appName else existing?.appName ?: packageName
        val icon = iconBase64 ?: existing?.iconBase64

        android.util.Log.d(
            "FocusUsageTracker",
            "[LIVE-TRACK] $packageName before: ${beforeMinutes}m -> writing absolute: ${finalMinutes}m"
        )

        focusDao.insertOrUpdateDailyUsageLog(
            DailyUsageLog(
                packageName = packageName,
                date = date,
                minutesUsed = finalMinutes,
                appName = name,
                iconBase64 = icon
            )
        )
        // Keep ScreenTimeLimit's usedMinutesToday in sync with daily usage log
        focusDao.updateScreenTimeUsage(packageName, finalMinutes, date)

        val confirmed = focusDao.getUsageLog(packageName, date)?.minutesUsed
        android.util.Log.d(
            "FocusUsageTracker",
            "[LIVE-TRACK] $packageName confirmed in DB: ${confirmed}m"
        )

        return finalMinutes
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
     * 1. Reconciles non-overlapping device screen-on time from UsageEvents as sanity reference.
     * 2. Live, single-focus tracked numbers take priority while Focus Lock is running.
     * 3. Periodic UsageStatsManager sync fills genuine gaps (apps used before service started today),
     *    overwriting with OS absolute numbers without compounding or overwriting live-tracked periods.
     */
    suspend fun syncUsageStatsFromSystem(context: android.content.Context) {
        if (!com.example.util.PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val todayDate = getTodayDateString()
                val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()

                // 1. Sync real device screen-on time from UsageEvents (non-overlapping interactive time)
                val osScreenOn = ScreenTimeHelper.queryDeviceScreenOnMinutes(context)
                if (osScreenOn != null) {
                    val currentRecorded = getDeviceScreenOnMinutesTodaySync()
                    val reconciledScreenOn = maxOf(currentRecorded, osScreenOn).coerceIn(0, maxAllowed)
                    updateDeviceScreenOnTime(reconciledScreenOn, todayDate)
                }

                val usageStatsManager = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return@withContext

                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfToday = calendar.timeInMillis
                val now = System.currentTimeMillis()

                // Query full day stats from OS
                val aggregatedMap = mutableMapOf<String, Long>()
                try {
                    val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfToday, now)
                    if (!aggregated.isNullOrEmpty()) {
                        for ((pkg, stat) in aggregated) {
                            if (stat.totalTimeInForeground > 0) {
                                aggregatedMap[pkg] = stat.totalTimeInForeground
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FocusUsageTracker", "queryAndAggregateUsageStats failed", e)
                }

                try {
                    val dailyStats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startOfToday, now)
                    dailyStats?.forEach { stat ->
                        if (stat.totalTimeInForeground > 0 && stat.lastTimeUsed >= startOfToday) {
                            val existing = aggregatedMap[stat.packageName] ?: 0L
                            aggregatedMap[stat.packageName] = maxOf(existing, stat.totalTimeInForeground)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FocusUsageTracker", "queryUsageStats INTERVAL_DAILY failed", e)
                }

                if (aggregatedMap.isEmpty()) return@withContext

                val existingLogs = focusDao.getUsageLogsForDateSync(todayDate).associateBy { it.packageName }
                val pm = context.packageManager

                for ((pkg, totalForegroundMillis) in aggregatedMap) {
                    if (pkg == context.packageName || isSystemOverlay(pkg)) continue

                    val existingLog = existingLogs[pkg]
                    val existingMinutes = existingLog?.minutesUsed ?: 0
                    val osMinutes = ((totalForegroundMillis / 60000L).toInt()).coerceIn(0, maxAllowed)

                    if (osMinutes <= 0 && existingMinutes <= 0) continue

                    // Authoritative individual app time from UsageStatsManager (already handles overlapping windows)
                    val finalMinutes = maxOf(osMinutes, existingMinutes).coerceIn(0, maxAllowed)

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
                        "[SYNC] $pkg before: ${existingMinutes}m -> UsageStatsManager: ${osMinutes}m (resolved: ${finalMinutes}m)"
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

                    val confirmed = focusDao.getUsageLog(pkg, todayDate)?.minutesUsed
                    android.util.Log.d(
                        "FocusUsageTracker",
                        "[SYNC] $pkg confirmed in DB: ${confirmed}m"
                    )
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

                // Internal sanity check: verify total tracked usage against real device screen-on duration
                val totalAppUsage = focusDao.getTotalMinutesUsedForDateSync(todayDate) ?: 0
                val screenOn = getDeviceScreenOnMinutesTodaySync()
                if (screenOn > 0 && totalAppUsage > screenOn) {
                    android.util.Log.w(
                        "FocusUsageTracker",
                        "Sanity check warning: Total tracked app usage (${totalAppUsage}m) exceeds physical screen-on duration (${screenOn}m)."
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("FocusUsageTracker", "Error syncing usage stats from system", e)
            }
        }
    }

    private fun isSystemOverlay(pkg: String): Boolean {
        return pkg == "com.android.systemui" ||
                pkg.contains("inputmethod") ||
                pkg.contains(".ime") ||
                pkg == "android"
    }
}
