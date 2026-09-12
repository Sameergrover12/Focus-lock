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
import java.util.Calendar
import java.util.TimeZone

data class TimeInterval(val start: Long, val end: Long)

data class UsageEventsData(
    val totalScreenOnMinutes: Int,
    val totalScreenOnMillis: Long,
    val appUsageMillis: Map<String, Long>
)

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
     * Time Bounds: Calculate local midnight (Calendar.getInstance() set to 00:00:00 local time).
     */
    fun getStartOfDayMillis(zoneId: ZoneId = getLocalZoneId()): Long {
        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (e: Exception) {
            LocalDate.now(zoneId)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        }
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
     * Queries UsageEvents strictly from local midnight (00:00:00) to System.currentTimeMillis().
     * - Total Screen Time: ONLY by measuring durations between SCREEN_INTERACTIVE (15) and SCREEN_NON_INTERACTIVE (16).
     * - Individual App Time: exact duration between ACTIVITY_RESUMED (1) and ACTIVITY_PAUSED (2).
     * - Dangling Sessions: if RESUMED/INTERACTIVE without a closing event, adds (now - startEventTime).
     */
    fun queryUsageEventsToday(context: Context): UsageEventsData? {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return null
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

            val startOfToday = getStartOfDayMillis()
            val now = System.currentTimeMillis()

            if (now <= startOfToday) {
                return UsageEventsData(0, 0L, emptyMap())
            }

            val events = usageStatsManager.queryEvents(startOfToday, now) ?: return null
            parseUsageEvents(events, startOfToday, now)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query UsageEvents today", e)
            null
        }
    }

    /**
     * Pure parser for UsageEvents respecting exact architectural rules:
     * 1. Total Screen Time: ONLY durations between Event.SCREEN_INTERACTIVE (15) and Event.SCREEN_NON_INTERACTIVE (16).
     * 2. Individual App Time: exact durations between Event.ACTIVITY_RESUMED (1) and Event.ACTIVITY_PAUSED (2).
     * 3. Dangling Sessions: if loop finishes with an open RESUMED/INTERACTIVE event, add time to System.currentTimeMillis().
     */
    fun parseUsageEvents(
        events: UsageEvents,
        startOfToday: Long,
        now: Long
    ): UsageEventsData {
        var totalScreenOnMillis = 0L
        var lastScreenInteractiveTime: Long? = null

        val appResumedMap = mutableMapOf<String, Long>()
        val appUsageMillis = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val time = event.timeStamp
            if (time < startOfToday || time > now) continue

            when (event.eventType) {
                // Event.SCREEN_INTERACTIVE (15)
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (lastScreenInteractiveTime == null) {
                        lastScreenInteractiveTime = time
                    }
                }
                // Event.SCREEN_NON_INTERACTIVE (16)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    val start = lastScreenInteractiveTime
                    if (start != null) {
                        val duration = time - start
                        if (duration > 0) {
                            totalScreenOnMillis += duration
                        }
                        lastScreenInteractiveTime = null
                    }
                }

                // Event.ACTIVITY_RESUMED (1)
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = event.packageName
                    if (!pkg.isNullOrEmpty()) {
                        val prevStart = appResumedMap[pkg]
                        if (prevStart != null) {
                            val duration = time - prevStart
                            if (duration > 0) {
                                appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + duration
                            }
                        }
                        appResumedMap[pkg] = time
                    }
                }
                // Event.ACTIVITY_PAUSED (2)
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val pkg = event.packageName
                    if (!pkg.isNullOrEmpty()) {
                        val startTime = appResumedMap.remove(pkg)
                        if (startTime != null) {
                            val duration = time - startTime
                            if (duration > 0) {
                                appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + duration
                            }
                        }
                    }
                }
            }
        }

        // Dangling Sessions:
        // If the loop finishes and an app (or the screen) had a RESUMED/INTERACTIVE event but no closing event,
        // add the time from that start event to System.currentTimeMillis().
        if (lastScreenInteractiveTime != null && now > lastScreenInteractiveTime) {
            val duration = now - lastScreenInteractiveTime
            if (duration > 0) {
                totalScreenOnMillis += duration
            }
        }

        for ((pkg, startTime) in appResumedMap) {
            if (now > startTime) {
                val duration = now - startTime
                if (duration > 0) {
                    appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + duration
                }
            }
        }

        val totalMinutes = (totalScreenOnMillis / 60000L).toInt()

        return UsageEventsData(
            totalScreenOnMinutes = totalMinutes,
            totalScreenOnMillis = totalScreenOnMillis,
            appUsageMillis = appUsageMillis
        )
    }

    /**
     * Total Screen Time: Calculates device screen time from UsageEvents
     * measuring durations between SCREEN_INTERACTIVE and SCREEN_NON_INTERACTIVE events.
     */
    fun queryDeviceScreenOnMinutes(context: Context): Int? {
        return queryUsageEventsToday(context)?.totalScreenOnMinutes
    }

    /**
     * Individual App Time: Measured strictly from UsageEvents ACTIVITY_RESUMED and ACTIVITY_PAUSED.
     * Replaced queryUsageStats() completely.
     */
    fun queryAppUsageStats(context: Context): Map<String, Long> {
        return queryUsageEventsToday(context)?.appUsageMillis ?: emptyMap()
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
