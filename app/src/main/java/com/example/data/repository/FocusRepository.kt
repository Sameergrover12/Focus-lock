package com.example.data.repository

import com.example.data.local.dao.FocusDao
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.DailyUsageLog
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusRepository(private val focusDao: FocusDao) {

    companion object {
        fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
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

    // Daily Usage Logs
    fun getUsageLogsForDate(date: String): Flow<List<DailyUsageLog>> = focusDao.getUsageLogsForDate(date)
    fun getTotalMinutesUsedToday(): Flow<Int?> = focusDao.getTotalMinutesUsedForDate(getTodayDateString())

    suspend fun logAppUsage(
        packageName: String,
        minutesToAdd: Int,
        appName: String = "",
        iconBase64: String? = null,
        date: String = getTodayDateString()
    ): Int {
        val existing = focusDao.getUsageLog(packageName, date)
        val newTotal = (existing?.minutesUsed ?: 0) + minutesToAdd
        val name = if (appName.isNotBlank()) appName else existing?.appName ?: packageName
        val icon = iconBase64 ?: existing?.iconBase64
        focusDao.insertOrUpdateDailyUsageLog(
            DailyUsageLog(
                packageName = packageName,
                date = date,
                minutesUsed = newTotal,
                appName = name,
                iconBase64 = icon
            )
        )
        // Keep ScreenTimeLimit's usedMinutesToday in sync with daily usage log
        focusDao.updateScreenTimeUsage(packageName, newTotal, date)
        return newTotal
    }

    /**
     * Fix 1: Reconciles full daily screen time from UsageStatsManager into DailyUsageLog.
     * Captures app usage that occurred earlier today before Focus Lock started,
     * apps installed partway through the day, and persists uninstalled app records.
     */
    suspend fun syncUsageStatsFromSystem(context: android.content.Context) {
        if (!com.example.util.PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val usageStatsManager = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return@withContext

                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfToday = calendar.timeInMillis
                val now = System.currentTimeMillis()
                val todayDate = getTodayDateString()

                val aggregatedMap = mutableMapOf<String, Long>()

                // 1. Query aggregated stats (API 21+)
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
                    android.util.Log.w("FocusRepository", "queryAndAggregateUsageStats failed", e)
                }

                // 2. Query INTERVAL_DAILY usage stats as specified by prompt
                try {
                    val dailyStats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startOfToday, now)
                    dailyStats?.forEach { stat ->
                        if (stat.totalTimeInForeground > 0 && stat.lastTimeUsed >= startOfToday) {
                            val existing = aggregatedMap[stat.packageName] ?: 0L
                            aggregatedMap[stat.packageName] = maxOf(existing, stat.totalTimeInForeground)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FocusRepository", "queryUsageStats INTERVAL_DAILY failed", e)
                }

                if (aggregatedMap.isEmpty()) return@withContext

                // Existing local DB logs for today - our permanent source of truth
                val existingLogs = focusDao.getUsageLogsForDateSync(todayDate).associateBy { it.packageName }
                val pm = context.packageManager

                for ((pkg, totalForegroundMillis) in aggregatedMap) {
                    if (pkg == context.packageName || isSystemOverlay(pkg)) continue

                    val osMinutes = (totalForegroundMillis / 60000L).toInt()
                    val existingLog = existingLogs[pkg]
                    val existingMinutes = existingLog?.minutesUsed ?: 0

                    // Reconcile: authoritative daily usage is at least osMinutes, never lower than existing logged minutes
                    val finalMinutes = maxOf(existingMinutes, osMinutes)
                    if (finalMinutes <= 0) continue

                    // App Name & Icon caching (cached in DB so uninstalled apps retain name & icon)
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
                            // App might be uninstalled or system package
                            if (appName.isBlank()) {
                                appName = pkg
                            }
                        }
                    }

                    // Upsert into permanent local database
                    focusDao.insertOrUpdateDailyUsageLog(
                        DailyUsageLog(
                            packageName = pkg,
                            date = todayDate,
                            minutesUsed = finalMinutes,
                            appName = appName,
                            iconBase64 = iconBase64
                        )
                    )

                    // Keep ScreenTimeLimit's usedMinutesToday in sync
                    focusDao.updateScreenTimeUsage(pkg, finalMinutes, todayDate)
                }

                // Reconcile group budgets for groups containing these apps
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
                android.util.Log.e("FocusRepository", "Error syncing usage stats from system", e)
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
