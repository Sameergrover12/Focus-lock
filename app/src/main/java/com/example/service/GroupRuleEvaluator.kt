package com.example.service

import com.example.data.local.entity.AppGroup
import java.util.Calendar

object GroupRuleEvaluator {

    /**
     * Checks if the group's schedule rule is currently active.
     */
    fun isScheduleActive(group: AppGroup, now: Calendar = Calendar.getInstance()): Boolean {
        if (!group.scheduleEnabled) return false

        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, ..., 7=Saturday
        if (!group.daysOfWeek.contains(currentDayOfWeek)) {
            return false
        }

        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = parseTimeToMinutes(group.scheduleStart)
        val endMinutes = parseTimeToMinutes(group.scheduleEnd)

        return if (startMinutes <= endMinutes) {
            // Normal range (e.g., 09:00 to 17:00)
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnight range (e.g., 22:00 to 06:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
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
     * Returns true if the group restricts apps right now (either schedule active OR budget exhausted).
     */
    fun isGroupBlocked(group: AppGroup, now: Calendar = Calendar.getInstance()): Boolean {
        val scheduleBlocked = isScheduleActive(group, now)
        val budgetBlocked = isBudgetExhausted(group)
        return scheduleBlocked || budgetBlocked
    }

    /**
     * Helper to parse "HH:mm" to minutes from midnight.
     */
    fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return (h * 60) + m
        }
        return 0
    }
}
