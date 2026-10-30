package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * TimeIntegrityManager provides 100% offline, hardware-anchored time tracking.
 * It prevents bypassing schedules, daily limits, and emergency breaks by altering
 * the Android system clock or timezone.
 *
 * Anchors System.currentTimeMillis() against SystemClock.elapsedRealtime().
 * Because SystemClock.elapsedRealtime() counts CPU uptime (including deep sleep)
 * and is physically immutable to user clock adjustments, true time is calculated as:
 * trueTimeMillis = anchorWallTimeMillis + (SystemClock.elapsedRealtime() - anchorElapsedRealtimeMillis)
 */
object TimeIntegrityManager {

    private const val TAG = "TimeIntegrityManager"
    private const val PREFS_NAME = "focus_time_integrity_prefs"
    private const val KEY_ANCHOR_WALL = "anchor_wall_time"
    private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed_realtime"
    private const val KEY_LAST_TRUE_TIME = "last_recorded_true_time"
    private const val KEY_TAMPER_FLAG = "tamper_detected_flag"
    private const val KEY_PROTECTION_ENABLED = "time_integrity_enabled"

    // Allowable drift threshold before flagging system clock manipulation (2 minutes = 120,000 ms)
    const val MAX_ALLOWED_DRIFT_MS = 120_000L

    private val lock = Any()
    private var prefs: SharedPreferences? = null

    private var anchorWallTimeMillis: Long = 0L
    private var anchorElapsedRealtimeMillis: Long = 0L
    private var lastRecordedTrueTimeMillis: Long = 0L
    private var isProtectionEnabled: Boolean = true

    private val _isClockTamperedFlow = MutableStateFlow(false)
    val isClockTamperedFlow: StateFlow<Boolean> = _isClockTamperedFlow.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadPersistedAnchors()
            }
        }
    }

    private fun loadPersistedAnchors() {
        val p = prefs ?: return
        isProtectionEnabled = p.getBoolean(KEY_PROTECTION_ENABLED, true)
        val savedTamper = p.getBoolean(KEY_TAMPER_FLAG, false)
        _isClockTamperedFlow.value = savedTamper

        val savedAnchorWall = p.getLong(KEY_ANCHOR_WALL, 0L)
        val savedAnchorElapsed = p.getLong(KEY_ANCHOR_ELAPSED, 0L)
        val savedLastTrue = p.getLong(KEY_LAST_TRUE_TIME, 0L)

        val currentElapsed = SystemClock.elapsedRealtime()
        val currentWall = System.currentTimeMillis()

        if (savedAnchorWall == 0L || savedAnchorElapsed == 0L) {
            // First run: establish initial baseline anchor
            anchorWallTimeMillis = currentWall
            anchorElapsedRealtimeMillis = currentElapsed
            lastRecordedTrueTimeMillis = currentWall
            persistAnchors()
            Log.d(TAG, "Initialized first time baseline anchor: wall=$anchorWallTimeMillis, elapsed=$anchorElapsedRealtimeMillis")
        } else {
            // Device reboot detected if elapsedRealtime has reset to a value less than saved elapsed
            val deviceRebooted = currentElapsed < savedAnchorElapsed
            if (deviceRebooted) {
                Log.i(TAG, "Device reboot detected (currentElapsed=$currentElapsed < savedAnchorElapsed=$savedAnchorElapsed)")
                // Check if the current wall time has been rolled backward past last recorded true time
                if (currentWall < savedLastTrue) {
                    Log.w(TAG, "Clock rolled backward during or across reboot! (currentWall=$currentWall < savedLastTrue=$savedLastTrue)")
                    setTampered(true)
                    // Monotonicity enforcement: clamp anchor wall time to at least savedLastTrue
                    anchorWallTimeMillis = savedLastTrue
                } else {
                    anchorWallTimeMillis = maxOf(currentWall, savedLastTrue)
                }
                anchorElapsedRealtimeMillis = currentElapsed
                lastRecordedTrueTimeMillis = anchorWallTimeMillis
                persistAnchors()
            } else {
                anchorWallTimeMillis = savedAnchorWall
                anchorElapsedRealtimeMillis = savedAnchorElapsed
                lastRecordedTrueTimeMillis = savedLastTrue
                // Check for immediate drift
                checkDriftInternal(currentWall, currentElapsed)
            }
        }
    }

    /**
     * Calculates the genuine True Time in milliseconds.
     * Guarantees monotonic progression so time can never run backwards.
     */
    fun getTrueTimeMillis(): Long {
        synchronized(lock) {
            if (!isProtectionEnabled) {
                return System.currentTimeMillis()
            }

            if (anchorWallTimeMillis == 0L) {
                val currentWall = System.currentTimeMillis()
                val currentElapsed = SystemClock.elapsedRealtime()
                anchorWallTimeMillis = currentWall
                anchorElapsedRealtimeMillis = currentElapsed
                lastRecordedTrueTimeMillis = currentWall
                return currentWall
            }

            val currentElapsed = SystemClock.elapsedRealtime()
            val currentWall = System.currentTimeMillis()

            // Handle unexpected elapsed reboot while service was running
            if (currentElapsed < anchorElapsedRealtimeMillis) {
                Log.w(TAG, "ElapsedRealtime reset detected in getTrueTimeMillis")
                anchorWallTimeMillis = maxOf(currentWall, lastRecordedTrueTimeMillis)
                anchorElapsedRealtimeMillis = currentElapsed
            }

            val elapsedSinceAnchor = (currentElapsed - anchorElapsedRealtimeMillis).coerceAtLeast(0L)
            val calculatedTrueTime = anchorWallTimeMillis + elapsedSinceAnchor

            // Monotonicity guarantee: true time can never be less than last recorded true time
            val trueTime = maxOf(calculatedTrueTime, lastRecordedTrueTimeMillis)
            lastRecordedTrueTimeMillis = trueTime

            // Periodically check drift against wall clock
            checkDriftInternal(currentWall, currentElapsed)

            // Persist periodically if moved by more than 30 seconds
            val p = prefs
            if (p != null) {
                val lastSaved = p.getLong(KEY_LAST_TRUE_TIME, 0L)
                if (trueTime - lastSaved > 30_000L) {
                    p.edit().putLong(KEY_LAST_TRUE_TIME, trueTime).apply()
                }
            }

            return trueTime
        }
    }

    /**
     * Checks if the system wall clock has drifted significantly from hardware-anchored true time.
     */
    private fun checkDriftInternal(currentWall: Long, currentElapsed: Long) {
        val elapsedSinceAnchor = (currentElapsed - anchorElapsedRealtimeMillis).coerceAtLeast(0L)
        val calculatedTrueTime = anchorWallTimeMillis + elapsedSinceAnchor
        val drift = abs(currentWall - calculatedTrueTime)

        if (drift > MAX_ALLOWED_DRIFT_MS) {
            Log.w(TAG, "System clock tampering detected! Drift: ${drift}ms > ${MAX_ALLOWED_DRIFT_MS}ms (wall=$currentWall, true=$calculatedTrueTime)")
            setTampered(true)
        }
    }

    private fun setTampered(tampered: Boolean) {
        if (_isClockTamperedFlow.value != tampered) {
            _isClockTamperedFlow.value = tampered
            prefs?.edit()?.putBoolean(KEY_TAMPER_FLAG, tampered)?.apply()
        }
    }

    /**
     * Called by BroadcastReceiver when ACTION_TIME_CHANGED (android.intent.action.TIME_SET)
     * or ACTION_TIMEZONE_CHANGED is received.
     */
    fun onTimeChanged() {
        synchronized(lock) {
            val currentWall = System.currentTimeMillis()
            val currentElapsed = SystemClock.elapsedRealtime()
            Log.i(TAG, "ACTION_TIME_CHANGED received. Validating integrity...")
            checkDriftInternal(currentWall, currentElapsed)
        }
    }

    /**
     * Called by BootReceiver when ACTION_BOOT_COMPLETED or ACTION_MY_PACKAGE_REPLACED is received.
     */
    fun onBootCompleted(context: Context) {
        init(context)
        synchronized(lock) {
            loadPersistedAnchors()
        }
    }

    /**
     * Returns true if time tampering has been detected.
     */
    fun isClockTampered(): Boolean = _isClockTamperedFlow.value

    /**
     * Returns a Calendar anchored to True Time.
     */
    fun getTrueCalendar(timeZone: TimeZone = TimeZone.getDefault()): Calendar {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = getTrueTimeMillis()
        return cal
    }

    /**
     * Returns today's date formatted as "yyyy-MM-dd" evaluated against True Time.
     */
    fun getTrueDateString(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val trueMillis = getTrueTimeMillis()
        val instant = Instant.ofEpochMilli(trueMillis)
        val localDate = LocalDate.ofInstant(instant, zoneId)
        return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    fun isProtectionEnabled(): Boolean = isProtectionEnabled

    fun setProtectionEnabled(enabled: Boolean) {
        synchronized(lock) {
            isProtectionEnabled = enabled
            prefs?.edit()?.putBoolean(KEY_PROTECTION_ENABLED, enabled)?.apply()
            if (!enabled) {
                setTampered(false)
            }
        }
    }

    fun resetTamperFlag() {
        synchronized(lock) {
            setTampered(false)
            // Re-anchor to current time if user legitimately adjusted timezone or clock
            val currentWall = System.currentTimeMillis()
            val currentElapsed = SystemClock.elapsedRealtime()
            anchorWallTimeMillis = currentWall
            anchorElapsedRealtimeMillis = currentElapsed
            lastRecordedTrueTimeMillis = currentWall
            persistAnchors()
            Log.i(TAG, "Tamper flag cleared and re-anchored to current wall time.")
        }
    }

    private fun persistAnchors() {
        prefs?.edit()
            ?.putLong(KEY_ANCHOR_WALL, anchorWallTimeMillis)
            ?.putLong(KEY_ANCHOR_ELAPSED, anchorElapsedRealtimeMillis)
            ?.putLong(KEY_LAST_TRUE_TIME, lastRecordedTrueTimeMillis)
            ?.apply()
    }
}
