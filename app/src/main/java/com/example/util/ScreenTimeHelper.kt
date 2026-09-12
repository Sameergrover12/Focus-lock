package com.example.util

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
     * Guaranteed never to be negative and never to exceed elapsed minutes in the current day.
     * Acts as the physical clock ceiling for any screen time measurement today.
     */
    fun getMinutesSinceMidnight(zoneId: ZoneId = getLocalZoneId()): Int {
        val startOfDay = getStartOfDayMillis(zoneId)
        val now = System.currentTimeMillis()
        val elapsed = (now - startOfDay).coerceAtLeast(0L)
        return (elapsed / 60000L).toInt()
    }

    /**
     * Merges overlapping or adjacent time intervals and returns the total non-overlapping duration in milliseconds.
     * For example, if App A was foreground [10:00, 10:30] and App B was foreground in split-screen/floating
     * window [10:15, 10:45], the merged interval is [10:00, 10:45] (45 minutes total, NOT 60).
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
     * Calculates total device screen time for today by:
     * 1. Tracking absolute screen ON and UNLOCKED intervals (using SCREEN_INTERACTIVE, SCREEN_NON_INTERACTIVE,
     *    KEYGUARD_HIDDEN, KEYGUARD_SHOWN).
     * 2. Merging overlapping foreground events across all applications (immune to floating windows, PIP,
     *    and multi-window double counting).
     * 3. Strictly ignoring any data and events that occurred before 00:00:00.000 local time today.
     * Guaranteed never to exceed elapsed minutes since local midnight.
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

            val screenUnlockedIntervals = mutableListOf<TimeInterval>()
            val foregroundIntervals = mutableListOf<TimeInterval>()

            // Track state for absolute screen on + unlocked intervals
            var isScreenOn = false
            var isKeyguardLocked = false
            var currentUnlockedScreenStart: Long? = null

            // Track state for foreground sessions per package (to merge overlapping windows)
            val openForegroundSessions = mutableMapOf<String, Long>()

            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val time = event.timeStamp
                // Strictly ignore events before 00:00:00.000 local time today
                if (time < startOfToday || time > now) continue

                when (event.eventType) {
                    // Screen Interactive / Non-Interactive (Events 15 & 16)
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        isScreenOn = true
                        if (!isKeyguardLocked && currentUnlockedScreenStart == null) {
                            currentUnlockedScreenStart = time
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        isScreenOn = false
                        val effectiveStart = currentUnlockedScreenStart ?: startOfToday
                        if (time > effectiveStart) {
                            screenUnlockedIntervals.add(TimeInterval(maxOf(effectiveStart, startOfToday), time))
                        }
                        currentUnlockedScreenStart = null
                    }
                    // Keyguard Hidden / Shown (Events 17 & 18)
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        isKeyguardLocked = false
                        if (isScreenOn && currentUnlockedScreenStart == null) {
                            currentUnlockedScreenStart = time
                        }
                    }
                    UsageEvents.Event.KEYGUARD_SHOWN -> {
                        isKeyguardLocked = true
                        val effectiveStart = currentUnlockedScreenStart ?: startOfToday
                        if (time > effectiveStart) {
                            screenUnlockedIntervals.add(TimeInterval(maxOf(effectiveStart, startOfToday), time))
                        }
                        currentUnlockedScreenStart = null
                    }
                    // Foreground Activity Events (Activity resumed, paused, stopped)
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        val pkg = event.packageName
                        if (!pkg.isNullOrEmpty() && !openForegroundSessions.containsKey(pkg)) {
                            openForegroundSessions[pkg] = time
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val pkg = event.packageName
                        if (!pkg.isNullOrEmpty()) {
                            val startTime = openForegroundSessions.remove(pkg) ?: startOfToday
                            val effectiveStart = maxOf(startTime, startOfToday)
                            if (time > effectiveStart) {
                                foregroundIntervals.add(TimeInterval(effectiveStart, time))
                            }
                        }
                    }
                }
            }

            // Account for currently active session up to now
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val currentlyInteractive = powerManager?.isInteractive ?: false
            val currentlyUnlocked = !(keyguardManager?.isKeyguardLocked ?: false)

            if (currentlyInteractive && currentlyUnlocked) {
                val screenStart = currentUnlockedScreenStart ?: (now - 5000L).coerceAtLeast(startOfToday)
                if (now > screenStart) {
                    screenUnlockedIntervals.add(TimeInterval(screenStart, now))
                }

                for ((_, startTime) in openForegroundSessions) {
                    val effectiveStart = maxOf(startTime, startOfToday)
                    if (now > effectiveStart) {
                        foregroundIntervals.add(TimeInterval(effectiveStart, now))
                    }
                }
            }

            // Calculate non-overlapping durations
            val screenUnlockedMillis = mergeIntervals(screenUnlockedIntervals)
            val mergedForegroundMillis = mergeIntervals(foregroundIntervals)

            // Select the most comprehensive non-overlapping duration
            val bestMillis = maxOf(screenUnlockedMillis, mergedForegroundMillis)
            val calculatedMinutes = (bestMillis / 60000L).toInt()

            val maxAllowed = getMinutesSinceMidnight()
            calculatedMinutes.coerceIn(0, maxAllowed)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query device screen time intervals", e)
            null
        }
    }

    /**
     * Queries individual application foreground usage strictly from 00:00:00.000 local time today to now.
     * Strictly ignores all data and events that occurred before local midnight today.
     * Prevents pulling in usage from yesterday evening.
     */
    fun queryAppForegroundMillisToday(context: Context): Map<String, Long> {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return emptyMap()
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()

            val startOfToday = getStartOfDayMillis()
            val now = System.currentTimeMillis()

            val events = usageStatsManager.queryEvents(startOfToday, now) ?: return emptyMap()

            val appIntervals = mutableMapOf<String, MutableList<TimeInterval>>()
            val openSessions = mutableMapOf<String, Long>()
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val time = event.timeStamp
                // STRICT RULE: Ignore all data and events that occurred before 00:00:00.000 local time today
                if (time < startOfToday || time > now) continue
                val pkg = event.packageName ?: continue

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        val prevStart = openSessions[pkg]
                        if (prevStart != null && time > prevStart) {
                            appIntervals.getOrPut(pkg) { mutableListOf() }
                                .add(TimeInterval(maxOf(prevStart, startOfToday), time))
                        }
                        openSessions[pkg] = time
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val startTime = openSessions.remove(pkg) ?: startOfToday
                        val effectiveStart = maxOf(startTime, startOfToday)
                        if (time > effectiveStart) {
                            appIntervals.getOrPut(pkg) { mutableListOf() }
                                .add(TimeInterval(effectiveStart, time))
                        }
                    }
                }
            }

            // Account for applications currently running in foreground at 'now'
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val currentlyInteractive = powerManager?.isInteractive ?: false
            val currentlyUnlocked = !(keyguardManager?.isKeyguardLocked ?: false)

            if (currentlyInteractive && currentlyUnlocked) {
                for ((pkg, startTime) in openSessions) {
                    val effectiveStart = maxOf(startTime, startOfToday)
                    if (now > effectiveStart) {
                        appIntervals.getOrPut(pkg) { mutableListOf() }
                            .add(TimeInterval(effectiveStart, now))
                    }
                }
            }

            // Merge per-app intervals to eliminate multi-window or internal activity overlap
            val resultMap = mutableMapOf<String, Long>()
            for ((pkg, intervals) in appIntervals) {
                val nonOverlappingMillis = mergeIntervals(intervals)
                if (nonOverlappingMillis > 0L) {
                    resultMap[pkg] = nonOverlappingMillis
                }
            }
            resultMap
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query per-app foreground millis today", e)
            emptyMap()
        }
    }
}
