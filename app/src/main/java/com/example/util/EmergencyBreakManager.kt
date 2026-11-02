package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.FocusApplication
import com.example.data.backup.BackupManager
import com.example.receiver.EmergencyBreakReceiver
import com.example.service.FocusAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central Emergency Break Manager.
 *
 * Controls the 10-Minute Emergency Break with hardware-anchored clock tracking,
 * 7-day cycle renewal (3 breaks / 7 days), 0ms volatile memory checks for
 * AccessibilityService, automatic resumption at 10:00:00, and immediate re-checks.
 */
object EmergencyBreakManager {

    private const val TAG = "EmergencyBreakManager"
    const val PREFS_NAME = "focus_emergency_breaks"
    const val KEY_END_TIME = "emergency_break_end_time"
    const val KEY_END_ELAPSED = "emergency_break_end_elapsed"
    const val KEY_REMAINING = "emergency_breaks_remaining"
    const val KEY_CYCLE_START = "emergency_break_cycle_start"

    const val MAX_BREAKS = 3
    const val BREAK_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    const val CYCLE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    const val REQUEST_CODE_BREAK = 9030

    @Volatile private var endTimeMillis: Long = 0L
    @Volatile private var endElapsedRealtime: Long = 0L
    @Volatile private var breaksRemaining: Int = MAX_BREAKS
    @Volatile private var cycleStartTime: Long = 0L
    @Volatile private var initialized = false

    private val resumptionHandler = Handler(Looper.getMainLooper())
    private val resumptionRunnable = Runnable {
        Log.i(TAG, "10-minute emergency break timer reached 10:00:00. Triggering immediate resumption.")
        onBreakExpired()
    }

    private val _isBreakActiveFlow = MutableStateFlow(false)
    val isBreakActiveFlow: StateFlow<Boolean> = _isBreakActiveFlow.asStateFlow()

    private val _breaksRemainingFlow = MutableStateFlow(MAX_BREAKS)
    val breaksRemainingFlow: StateFlow<Int> = _breaksRemainingFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        endTimeMillis = prefs.getLong(KEY_END_TIME, 0L)
        endElapsedRealtime = prefs.getLong(KEY_END_ELAPSED, 0L)
        cycleStartTime = prefs.getLong(KEY_CYCLE_START, 0L)

        val now = System.currentTimeMillis()
        if (cycleStartTime == 0L || now - cycleStartTime >= CYCLE_DURATION_MS) {
            cycleStartTime = now
            breaksRemaining = MAX_BREAKS
            prefs.edit()
                .putLong(KEY_CYCLE_START, cycleStartTime)
                .putInt(KEY_REMAINING, MAX_BREAKS)
                .apply()
        } else {
            breaksRemaining = prefs.getInt(KEY_REMAINING, MAX_BREAKS)
        }

        // Check if an existing break is running across process restarts
        val nowElapsed = SystemClock.elapsedRealtime()
        val isActive = (endTimeMillis > now) && (endElapsedRealtime == 0L || endElapsedRealtime > nowElapsed)
        if (isActive) {
            val remainingMs = minOf(
                endTimeMillis - now,
                if (endElapsedRealtime > 0L) endElapsedRealtime - nowElapsed else Long.MAX_VALUE
            )
            resumptionHandler.removeCallbacks(resumptionRunnable)
            resumptionHandler.postDelayed(resumptionRunnable, remainingMs)
            _isBreakActiveFlow.value = true
        } else {
            if (endTimeMillis != 0L || endElapsedRealtime != 0L) {
                endTimeMillis = 0L
                endElapsedRealtime = 0L
                prefs.edit().putLong(KEY_END_TIME, 0L).putLong(KEY_END_ELAPSED, 0L).apply()
            }
            _isBreakActiveFlow.value = false
        }
        _breaksRemainingFlow.value = breaksRemaining
        initialized = true
    }

    private fun checkCycleReset(prefs: SharedPreferences) {
        val now = System.currentTimeMillis()
        if (cycleStartTime == 0L || now - cycleStartTime >= CYCLE_DURATION_MS) {
            cycleStartTime = now
            breaksRemaining = MAX_BREAKS
            prefs.edit()
                .putLong(KEY_CYCLE_START, cycleStartTime)
                .putInt(KEY_REMAINING, MAX_BREAKS)
                .apply()
            _breaksRemainingFlow.value = MAX_BREAKS
        }
    }

    /**
     * Fast check (0ms disk reads) to determine if a 10-minute break is currently active.
     */
    fun isBreakActive(context: Context? = null): Boolean {
        if (!initialized && context != null) {
            init(context.applicationContext)
        }
        val end = endTimeMillis
        if (end <= 0L) return false

        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedEnd = endElapsedRealtime

        val active = (now < end) && (elapsedEnd <= 0L || nowElapsed < elapsedEnd)
        if (!active) {
            if (endTimeMillis != 0L) {
                onBreakExpired(context)
            }
            return false
        }
        return true
    }

    fun getBreaksRemaining(context: Context? = null): Int {
        if (!initialized && context != null) {
            init(context.applicationContext)
        }
        val ctx = context ?: try { FocusApplication.instance } catch (_: Exception) { null }
        if (ctx != null) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                checkCycleReset(prefs)
            } catch (_: Exception) {}
        }
        return breaksRemaining
    }

    /**
     * Starts a 10-minute emergency break.
     * Decrements remaining breaks, records wall and elapsed end timestamps,
     * schedules exact Handler & AlarmManager resumptions, and syncs to cloud.
     */
    fun startTenMinuteBreak(context: Context): Boolean {
        if (!initialized) {
            init(context.applicationContext)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkCycleReset(prefs)

        if (isBreakActive(context) || breaksRemaining <= 0) {
            return false
        }

        breaksRemaining--
        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        endTimeMillis = now + BREAK_DURATION_MS
        endElapsedRealtime = nowElapsed + BREAK_DURATION_MS

        prefs.edit()
            .putInt(KEY_REMAINING, breaksRemaining)
            .putLong(KEY_END_TIME, endTimeMillis)
            .putLong(KEY_END_ELAPSED, endElapsedRealtime)
            .apply()

        _breaksRemainingFlow.value = breaksRemaining
        _isBreakActiveFlow.value = true

        // Schedule resumption at 10 minutes via Handler
        resumptionHandler.removeCallbacks(resumptionRunnable)
        resumptionHandler.postDelayed(resumptionRunnable, BREAK_DURATION_MS)

        // Schedule exact AlarmManager fallback
        scheduleAlarmFallback(context, endTimeMillis)

        // Trigger cloud backup async
        try {
            val app = context.applicationContext as? FocusApplication
            app?.backupManager?.backupToCloudAsync(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger cloud backup on emergency break start", e)
        }

        return true
    }

    /**
     * Called when the 10-minute break expires at 10:00:00.
     * Clears active state, ensures master protection is enabled, and triggers
     * immediate active window inspection in AccessibilityService.
     */
    fun onBreakExpired(context: Context? = null) {
        resumptionHandler.removeCallbacks(resumptionRunnable)
        endTimeMillis = 0L
        endElapsedRealtime = 0L
        _isBreakActiveFlow.value = false

        val ctx = context ?: try { FocusApplication.instance } catch (_: Exception) { null }
        if (ctx != null) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong(KEY_END_TIME, 0L)
                    .putLong(KEY_END_ELAPSED, 0L)
                    .apply()

                cancelAlarmFallback(ctx)

                // Ensure master Focus Protection is active
                val app = ctx.applicationContext as? FocusApplication
                app?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        it.preferencesRepository.setMasterEnabled(true)
                    }
                }

                // Immediately trigger active window inspection in AccessibilityService
                FocusAccessibilityService.instance?.handleEmergencyBreakExpired()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBreakExpired", e)
            }
        }
    }

    private fun scheduleAlarmFallback(context: Context, triggerAtMillis: Long) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, EmergencyBreakReceiver::class.java).apply {
                action = EmergencyBreakReceiver.ACTION_BREAK_EXPIRED
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BREAK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule alarm fallback for emergency break", e)
        }
    }

    private fun cancelAlarmFallback(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, EmergencyBreakReceiver::class.java).apply {
                action = EmergencyBreakReceiver.ACTION_BREAK_EXPIRED
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BREAK,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        } catch (_: Exception) {}
    }
}
