package com.example.util

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.util.Calendar

data class TimeInterval(val start: Long, val end: Long)

object ScreenTimeHelper {

    private const val TAG = "ScreenTimeHelper"

    /**
     * Calculates the number of minutes elapsed since midnight today.
     * Acts as the physical clock ceiling for any screen time measurement today.
     */
    fun getMinutesSinceMidnight(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
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
     * 3. Reconciling with the device's current interactive and unlocked state.
     * Guaranteed never to exceed elapsed minutes since midnight.
     */
    fun queryDeviceScreenOnMinutes(context: Context): Int? {
        if (!PermissionHelper.isUsageStatsPermissionGranted(context)) {
            return null
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = calendar.timeInMillis
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
                        currentUnlockedScreenStart?.let { start ->
                            if (time > start) {
                                screenUnlockedIntervals.add(TimeInterval(start, time))
                            }
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
                        currentUnlockedScreenStart?.let { start ->
                            if (time > start) {
                                screenUnlockedIntervals.add(TimeInterval(start, time))
                            }
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
                        val startTime = openForegroundSessions.remove(pkg)
                        if (startTime != null && time > startTime) {
                            foregroundIntervals.add(TimeInterval(startTime, time))
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
                    if (now > startTime) {
                        foregroundIntervals.add(TimeInterval(startTime, now))
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
}
