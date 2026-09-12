package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TimeInterval(val start: Long, val end: Long)

object ScreenTimeHelper {

    private const val TAG = "ScreenTimeHelper"

    /**
     * Resolves the device's local timezone.
     */
    fun getLocalZoneId(): ZoneId {
        return try {
            ZoneId.systemDefault()
        } catch (e: Exception) {
            ZoneId.of("UTC")
        }
    }

    /**
     * Calculates the 'Start of Day' (00:00:00.000) timestamp in milliseconds
     * strictly using the device's local timezone.
     */
    fun getStartOfDayMillis(zoneId: ZoneId = getLocalZoneId()): Long {
        return LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Returns today's date formatted as "yyyy-MM-dd" strictly in the device's local timezone.
     */
    fun getTodayDateString(zoneId: ZoneId = getLocalZoneId()): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return LocalDate.now(zoneId).format(formatter)
    }

    /**
     * Calculates elapsed minutes since 00:00:00.000 local time today.
     */
    fun getMinutesSinceMidnight(zoneId: ZoneId = getLocalZoneId()): Int {
        val startOfDay = getStartOfDayMillis(zoneId)
        val now = System.currentTimeMillis()
        val elapsed = (now - startOfDay).coerceAtLeast(0L)
        return (elapsed / 60000L).toInt()
    }

    /**
     * Merges overlapping or adjacent time intervals and returns the total non-overlapping duration in milliseconds.
     */
    fun mergeIntervals(intervals: List<TimeInterval>): Long {
        if (intervals.isEmpty()) return 0L
        val validIntervals = intervals.filter { it.end > it.start }.sortedBy { it.start }
        if (validIntervals.isEmpty()) return 0L

        var totalMillis = 0L
        var currentStart = validIntervals[0].start
        var currentEnd = validIntervals[0].end

        for (i in 1 until validIntervals.size) {
            val next = validIntervals[i]
            if (next.start <= currentEnd) {
                // Overlapping or adjacent interval: extend current window
                currentEnd = maxOf(currentEnd, next.end)
            } else {
                // Non-overlapping interval: commit previous interval and start new
                totalMillis += (currentEnd - currentStart)
                currentStart = next.start
                currentEnd = next.end
            }
        }
        totalMillis += (currentEnd - currentStart)
        return totalMillis
    }

    /**
     * Total Screen Time: Calculates the true screen time by querying UsageEvents
     * and calculating the time between ACTIVITY_RESUMED and ACTIVITY_PAUSED events
     * across the device, properly merging overlapping times.
     * Does not do manual math against midnight.
     */
    fun queryDeviceScreenOnMinutes(context: Context): Int? {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return null
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

            val startOfToday = getStartOfDayMillis()
            val now = System.currentTimeMillis()

            val events = usageStatsManager.queryEvents(startOfToday, now) ?: return null

            val foregroundIntervals = mutableListOf<TimeInterval>()
            val openForegroundSessions = mutableMapOf<String, Long>()
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val time = event.timeStamp
                if (time < startOfToday || time > now) continue
                val pkg = event.packageName
                if (pkg.isNullOrEmpty()) continue

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        openForegroundSessions[pkg] = time
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val startTime = openForegroundSessions.remove(pkg)
                        if (startTime != null && time > startTime) {
                            foregroundIntervals.add(TimeInterval(startTime, time))
                        }
                    }
                }
            }

            // If an activity was resumed and is still active right now, account for it up to now
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = powerManager?.isInteractive ?: true
            if (isInteractive) {
                for ((_, startTime) in openForegroundSessions) {
                    if (now > startTime) {
                        foregroundIntervals.add(TimeInterval(startTime, now))
                    }
                }
            }

            val mergedMillis = mergeIntervals(foregroundIntervals)
            val totalMinutes = (mergedMillis / 60000L).toInt()
            totalMinutes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query device screen time from UsageEvents", e)
            null
        }
    }

    /**
     * Individual App Time: Queries UsageStatsManager.queryUsageStats(...) and uses the
     * built-in totalTimeInForeground property from the UsageStats object for each package.
     * Does NOT do manual timestamp math (currentTime - startOfDay).
     */
    fun queryAppUsageStats(context: Context): Map<String, Long> {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return emptyMap()
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()

            val startOfToday = getStartOfDayMillis()
            val now = System.currentTimeMillis()

            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfToday,
                now
            )

            val resultMap = mutableMapOf<String, Long>()
            if (!statsList.isNullOrEmpty()) {
                for (stat in statsList) {
                    val foregroundTime = stat.totalTimeInForeground
                    if (foregroundTime > 0) {
                        val current = resultMap[stat.packageName] ?: 0L
                        resultMap[stat.packageName] = maxOf(current, foregroundTime)
                    }
                }
            }

            if (resultMap.isEmpty()) {
                val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfToday, now)
                if (!aggregated.isNullOrEmpty()) {
                    for ((pkg, stat) in aggregated) {
                        val foregroundTime = stat.totalTimeInForeground
                        if (foregroundTime > 0) {
                            resultMap[pkg] = foregroundTime
                        }
                    }
                }
            }

            resultMap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query app usage stats from UsageStatsManager", e)
            emptyMap()
        }
    }

    /**
     * Filters out background system processes like 'com.google.android.permissioncontroller' or
     * 'com.android.launcher' so they don't clog the UI.
     * Only shows apps the user actually interacts with.
     */
    fun isIgnoredSystemPackage(pkg: String, context: Context): Boolean {
        if (pkg.isBlank()) return true
        if (pkg == context.packageName || pkg == "com.example") return true
        if (pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg == "com.google.android.gms") return true

        // Permission controller
        if (pkg.contains("permissioncontroller")) return true

        // Launcher / Home screens
        if (pkg.contains("launcher") || pkg.contains("trebuchet") || pkg.contains("home")) return true
        if (pkg == "com.google.android.apps.nexuslauncher" ||
            pkg == "com.android.launcher3" ||
            pkg == "com.sec.android.app.launcher"
        ) return true

        // Keyboards / Input Methods
        if (pkg.contains("inputmethod") || pkg.contains(".ime") || pkg.contains("keyboard")) return true

        // System providers and framework daemons
        if (pkg.startsWith("com.android.providers.")) return true
        if (pkg.startsWith("com.android.server.")) return true
        if (pkg.startsWith("com.google.android.ext.")) return true

        // Check if package is registered as a launcher/home activity
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homeActivities = context.packageManager.queryIntentActivities(homeIntent, 0)
            if (homeActivities.any { it.activityInfo?.packageName == pkg }) {
                return true
            }
        } catch (e: Exception) {
            // ignore
        }

        // Only show apps the user actually interacts with: verify the app has a launch intent
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent == null) {
                return true
            }
        } catch (e: Exception) {
            return true
        }

        return false
    }
}
