package com.example.service

import android.content.Context
import com.example.data.local.entity.AppGroup
import com.example.util.TimeIntegrityManager
import java.util.Calendar
import java.util.TimeZone

object GroupRuleEvaluator {

    /**
     * Checks if the group's schedule rule is currently active using accurate local time
     * initialized from [TimeIntegrityManager.getTrustedCurrentTimeMillis].
     *
     * Handles both same-day schedules (e.g., 09:00 to 17:00) and overnight schedules
     * that cross midnight (e.g., 22:00 to 06:00).
     */
    fun isScheduleActive(
        group: AppGroup,
        now: Calendar = TimeIntegrityManager.getTrueCalendar(TimeZone.getDefault()),
        context: Context? = null
    ): Boolean {
        if (!group.scheduleEnabled) return false
        if (group.daysOfWeek.isEmpty()) return false

        val evalCal = if (context != null) {
            TimeIntegrityManager.getTrueCalendar(TimeZone.getDefault(), context)
        } else {
            now
        }

        val currentDayOfWeek = evalCal.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, ..., 7=Saturday
        val currentMinutes = evalCal.get(Calendar.HOUR_OF_DAY) * 60 + evalCal.get(Calendar.MINUTE)
        val startMinutes = parseTimeToMinutes(group.scheduleStart)
        val endMinutes = parseTimeToMinutes(group.scheduleEnd)

        return if (startMinutes <= endMinutes) {
            // Same-day range (e.g., 09:00 to 17:00)
            group.daysOfWeek.contains(currentDayOfWeek) && (currentMinutes in startMinutes..endMinutes)
        } else {
            // Overnight range crossing midnight (e.g., 22:00 to 06:00):
            // - Evening leg before midnight (currentMinutes >= startMinutes): check today's dayOfWeek
            // - Early morning leg after midnight (currentMinutes <= endMinutes): active if yesterday's
            //   schedule started overnight OR if today's dayOfWeek is in daysOfWeek.
            if (currentMinutes >= startMinutes) {
                group.daysOfWeek.contains(currentDayOfWeek)
            } else if (currentMinutes <= endMinutes) {
                val previousDayOfWeek = if (currentDayOfWeek == Calendar.SUNDAY) {
                    Calendar.SATURDAY
                } else {
                    currentDayOfWeek - 1
                }
                group.daysOfWeek.contains(previousDayOfWeek) || group.daysOfWeek.contains(currentDayOfWeek)
            } else {
                false
            }
        }
    }

    /**
     * Checks if the group's budget rule is exhausted.
     */
    fun isBudgetExhausted(group: AppGroup): Boolean {
        if (!group.budgetEnabled) return false
        return group.usedMinutesToday >= group.dailyBudgetMinutes
    }

    /**
     * Checks if the group's session cooldown is currently active.
     */
    fun isSessionCooldownActive(
        group: AppGroup,
        nowMillis: Long = TimeIntegrityManager.getTrustedCurrentTimeMillis()
    ): Boolean {
        return group.sessionLimitEnabled && group.cooldownSeconds > 0 && group.cooldownEndTimestamp > nowMillis
    }

    /**
     * Returns true if the group restricts apps right now (schedule active, budget exhausted, OR session cooldown).
     */
    fun isGroupBlocked(
        group: AppGroup,
        now: Calendar = TimeIntegrityManager.getTrueCalendar(TimeZone.getDefault()),
        nowMillis: Long = TimeIntegrityManager.getTrustedCurrentTimeMillis()
    ): Boolean {
        val scheduleBlocked = isScheduleActive(group, now)
        val budgetBlocked = isBudgetExhausted(group)
        val cooldownBlocked = isSessionCooldownActive(group, nowMillis)
        return scheduleBlocked || budgetBlocked || cooldownBlocked
    }

    /**
     * Helper to parse "HH:mm" to minutes from midnight.
     */
    fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.trim().split(":")
        if (parts.size == 2) {
            val h = parts[0].trim().toIntOrNull()?.coerceIn(0, 23) ?: 0
            val m = parts[1].trim().toIntOrNull()?.coerceIn(0, 59) ?: 0
            return (h * 60) + m
        }
        return 0
    }
}
