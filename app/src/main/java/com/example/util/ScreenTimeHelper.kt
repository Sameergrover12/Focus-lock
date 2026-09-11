package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.util.Calendar

object ScreenTimeHelper {

    private const val TAG = "ScreenTimeHelper"

    /**
     * Calculates the number of minutes elapsed since midnight today.
     */
    fun getMinutesSinceMidnight(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /**
     * Queries Android UsageEvents for real device screen-on (interactive) sessions since midnight.
     * This provides a true, non-overlapping measure of how long the screen was physically on today,
     * immune to multi-window or split-screen multiplication.
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

            var totalInteractiveMillis = 0L
            var lastInteractiveTimestamp: Long? = null
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventType = event.eventType

                // Event.SCREEN_INTERACTIVE = 15, Event.SCREEN_NON_INTERACTIVE = 16
                if (eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                    lastInteractiveTimestamp = event.timeStamp
                } else if (eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    if (lastInteractiveTimestamp != null) {
                        totalInteractiveMillis += (event.timeStamp - lastInteractiveTimestamp).coerceAtLeast(0)
                        lastInteractiveTimestamp = null
                    }
                }
            }

            // If device is currently interactive / screen is on, include active session up to now
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = powerManager?.isInteractive ?: false
            if (isInteractive && lastInteractiveTimestamp != null) {
                totalInteractiveMillis += (now - lastInteractiveTimestamp).coerceAtLeast(0)
            }

            val minutes = (totalInteractiveMillis / 60000L).toInt()
            val maxAllowed = getMinutesSinceMidnight()
            minutes.coerceIn(0, maxAllowed)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query device screen on events", e)
            null
        }
    }
}
