package com.example.util

object SessionLimitHelper {

    fun isCooldownActive(cooldownEndTimestamp: Long, now: Long = TimeIntegrityManager.getTrueTimeMillis()): Boolean {
        return cooldownEndTimestamp > 0L && now < cooldownEndTimestamp
    }

    fun getCooldownRemainingMs(cooldownEndTimestamp: Long, now: Long = TimeIntegrityManager.getTrueTimeMillis()): Long {
        return if (cooldownEndTimestamp > 0L) maxOf(0L, cooldownEndTimestamp - now) else 0L
    }

    fun shouldResetIdleSession(
        lastExitTimestamp: Long,
        cooldownSeconds: Int,
        now: Long = TimeIntegrityManager.getTrueTimeMillis()
    ): Boolean {
        if (lastExitTimestamp <= 0L || cooldownSeconds <= 0) return false
        val idleMs = now - lastExitTimestamp
        return idleMs >= (cooldownSeconds * 1000L)
    }

    /**
     * Converts seconds into a clean human-readable duration (e.g. "0h 10m 0s", "1h 30m", "45s")
     */
    fun formatHms(totalSeconds: Int): String {
        if (totalSeconds <= 0) return "0s"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60

        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || (h > 0 && s > 0)) append("${m}m ")
            if (s > 0 || (h == 0 && m == 0)) append("${s}s")
        }.trim()
    }

    /**
     * Converts remaining milliseconds into a live countdown display (e.g. "38m 15s" or "1h 05m 20s")
     */
    fun formatRemaining(remainingMs: Long): String {
        if (remainingMs <= 0L) return "0s"
        val totalSec = ((remainingMs + 999) / 1000).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60

        return when {
            h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s"
            m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
            else -> "${s}s"
        }
    }

    /**
     * Converts hours, minutes, and seconds into total seconds
     */
    fun toSeconds(hours: Int, minutes: Int, seconds: Int): Int {
        return (hours * 3600) + (minutes * 60) + seconds
    }
}
