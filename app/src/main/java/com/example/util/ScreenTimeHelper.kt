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

    private const val MAX_VALID_DAILY_DWELL_MILLIS = 18 * 60 * 60 * 1000L // 18 hours max sanity bound
    private const val MAX_ORPHAN_HISTORICAL_SESSION_MILLIS = 30 * 60 * 1000L // 30 minutes cap for unclosed past event
    private const val MAX_SINGLE_CONTINUOUS_EVENT_MILLIS = 6 * 60 * 60 * 1000L // 6 hours max single continuous interval

    /**
     * Calculates the daily screen dwell in milliseconds for a specific historical day interval.
     * Android only provides accurate daily foreground data for the last 10 calendar days (i = 0..9).
     * Any date older than 10 days returns 0L immediately so multi-week aggregated buckets never
     * produce fake 24h 0m (86,400,000 ms) towers.
     */
    fun queryHistoricalDayDwell(context: Context, startOfDay: Long, endOfDay: Long): Long {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return 0L
        }

        // Strict 10-day window check: days older than 10 calendar days (i >= 10) must return 0L
        val oldestAllowedStartOfDay = getStartOfDayMillis() - 9L * 24L * 60L * 60L * 1000L
        if (startOfDay < oldestAllowedStartOfDay) {
            return 0L
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0L

        // 1. Primary: raw UsageEvents between startOfDay and endOfDay
        try {
            val events = usageStatsManager.queryEvents(startOfDay, endOfDay)
            if (events != null) {
                val parsed = parseUsageEvents(events, startOfDay, endOfDay)
                if (parsed.totalScreenOnMillis in 1L until MAX_VALID_DAILY_DWELL_MILLIS) {
                    return parsed.totalScreenOnMillis
                }
                // If device OEM does not log SCREEN_INTERACTIVE/SCREEN_NON_INTERACTIVE in events,
                // check if parsed user app intervals were found
                if (parsed.appUsageMillis.isNotEmpty()) {
                    var appForegroundSum = 0L
                    for ((pkg, duration) in parsed.appUsageMillis) {
                        if (!isIgnoredSystemPackage(pkg, context)) {
                            appForegroundSum += duration
                        }
                    }
                    if (appForegroundSum in 1L until MAX_VALID_DAILY_DWELL_MILLIS) {
                        return appForegroundSum
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query UsageEvents for day range [$startOfDay, $endOfDay]", e)
        }

        // 2. Fallback for days within the 10-day window (e.g. days 7–9 when raw events are pruned):
        // Only accept true daily buckets (bucket span <= 36 hours) and reject any spike >= 18h.
        try {
            val statsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, endOfDay)
            if (!statsList.isNullOrEmpty()) {
                var totalForeground = 0L
                val maxDailyBucketSpan = 36L * 60L * 60L * 1000L
                for (stat in statsList) {
                    val bucketSpan = stat.lastTimeStamp - stat.firstTimeStamp
                    val isTrueDailyBucket = bucketSpan in 1L..maxDailyBucketSpan &&
                        stat.firstTimeStamp >= (startOfDay - 6L * 60L * 60L * 1000L) &&
                        stat.firstTimeStamp <= endOfDay
                    if (isTrueDailyBucket && !isIgnoredSystemPackage(stat.packageName, context)) {
                        totalForeground += stat.totalTimeInForeground
                    }
                }
                if (totalForeground in 1L until MAX_VALID_DAILY_DWELL_MILLIS) {
                    return totalForeground
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query UsageStats for day range [$startOfDay, $endOfDay]", e)
        }

        return 0L
    }

    /**
     * Pure parser for UsageEvents respecting exact architectural rules:
     * 1. Total Screen Time: ONLY durations between Event.SCREEN_INTERACTIVE (15) and Event.SCREEN_NON_INTERACTIVE (16).
     * 2. Individual App Time: exact durations between Event.ACTIVITY_RESUMED (1) and Event.ACTIVITY_PAUSED (2).
     * 3. Dangling Sessions: for today's live window, adds time up to [now] (capped safely); for historical closed days,
     *    caps any orphaned unclosed session so it never spans the entire 24-hour window.
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
        val isHistoricalClosedDay = now < (System.currentTimeMillis() - 60_000L)

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
                        val rawDuration = time - start
                        if (rawDuration > 0) {
                            val duration = if (isHistoricalClosedDay) {
                                minOf(rawDuration, MAX_SINGLE_CONTINUOUS_EVENT_MILLIS)
                            } else {
                                rawDuration
                            }
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
                            val rawDuration = time - prevStart
                            if (rawDuration > 0) {
                                val duration = minOf(rawDuration, MAX_SINGLE_CONTINUOUS_EVENT_MILLIS)
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
                            val rawDuration = time - startTime
                            if (rawDuration > 0) {
                                val duration = minOf(rawDuration, MAX_SINGLE_CONTINUOUS_EVENT_MILLIS)
                                appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + duration
                            }
                        }
                    }
                }
            }
        }

        // Dangling Sessions:
        // Never allow an unclosed event on a historical day to span across the entire 24-hour window.
        if (lastScreenInteractiveTime != null && now > lastScreenInteractiveTime) {
            val rawDuration = now - lastScreenInteractiveTime
            if (rawDuration > 0) {
                val cappedDuration = if (isHistoricalClosedDay) {
                    minOf(rawDuration, MAX_ORPHAN_HISTORICAL_SESSION_MILLIS)
                } else {
                    minOf(rawDuration, MAX_SINGLE_CONTINUOUS_EVENT_MILLIS)
                }
                totalScreenOnMillis += cappedDuration
            }
        }

        for ((pkg, startTime) in appResumedMap) {
            if (now > startTime) {
                val rawDuration = now - startTime
                if (rawDuration > 0) {
                    val cappedDuration = if (isHistoricalClosedDay) {
                        minOf(rawDuration, MAX_ORPHAN_HISTORICAL_SESSION_MILLIS)
                    } else {
                        minOf(rawDuration, MAX_SINGLE_CONTINUOUS_EVENT_MILLIS)
                    }
                    appUsageMillis[pkg] = (appUsageMillis[pkg] ?: 0L) + cappedDuration
                }
            }
        }

        if (totalScreenOnMillis >= MAX_VALID_DAILY_DWELL_MILLIS) {
            totalScreenOnMillis = 0L
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
