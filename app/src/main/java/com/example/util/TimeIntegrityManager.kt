package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.example.FocusApplication
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
 * TimeIntegrityManager provides 100% offline, hardware-anchored time tracking
 * with zero false positives.
 *
 * Rules:
 * 1. Uses SystemClock.elapsedRealtime() exclusively (never uptimeMillis(), which pauses in Doze).
 * 2. Checks Android's native AUTO_TIME & AUTO_TIME_ZONE first via isSystemAutoTimeEnabled(context).
 *    Whenever AUTO_TIME and AUTO_TIME_ZONE are both enabled, the device clock is network-synced:
 *    - Never shows "Clock drift detected!"
 *    - Automatically keeps the hardware anchor synced to System.currentTimeMillis() and
 *      SystemClock.elapsedRealtime()
 *    - Evaluates all schedules, budgets, and sessions using real local System.currentTimeMillis().
 * 3. Validates Settings.Global.BOOT_COUNT alongside anchorWallTimeMillis and anchorElapsedRealtimeMillis
 *    so device reboots cleanly re-anchor with zero drift.
 * 4. Only triggers "Clock drift detected!" and falls back to hardware monotonic time when ALL 3 hold:
 *    a) Clock Tamper Protection is enabled (isProtectionEnabled == true),
 *    b) Automatic Date & Time is disabled (isSystemAutoTimeEnabled(context) == false), AND
 *    c) System.currentTimeMillis() diverges from the same-boot elapsedRealtime() anchor by > 3 minutes (180,000 ms).
 */
object TimeIntegrityManager {

    private const val TAG = "TimeIntegrityManager"
    private const val PREFS_NAME = "focus_time_integrity_prefs"
    private const val KEY_ANCHOR_WALL = "anchor_wall_time"
    private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed_realtime"
    private const val KEY_LAST_TRUE_TIME = "last_recorded_true_time"
    private const val KEY_SAVED_BOOT_COUNT = "saved_boot_count"
    private const val KEY_TAMPER_FLAG = "tamper_detected_flag"
    private const val KEY_PROTECTION_ENABLED = "time_integrity_enabled"

    // 3 minutes (180,000 ms) threshold before flagging manual clock manipulation
    const val MAX_ALLOWED_DRIFT_MS = 180_000L

    private val lock = Any()
    @Volatile private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private var anchorWallTimeMillis: Long = 0L
    private var anchorElapsedRealtimeMillis: Long = 0L
    private var lastRecordedTrueTimeMillis: Long = 0L
    private var savedBootCount: Int = -1
    private var isProtectionEnabled: Boolean = true

    private val _isClockTamperedFlow = MutableStateFlow(false)
    val isClockTamperedFlow: StateFlow<Boolean> = _isClockTamperedFlow.asStateFlow()

    private fun resolveContext(context: Context? = null): Context? {
        if (context != null) {
            val appCtx = context.applicationContext ?: context
            appContext = appCtx
            return appCtx
        }
        appContext?.let { return it }
        return try {
            FocusApplication.instance.applicationContext.also { appContext = it }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks whether Android's native Automatic Date & Time and Automatic Time Zone are both ON.
     */
    fun isSystemAutoTimeEnabled(context: Context? = null): Boolean {
        val ctx = resolveContext(context) ?: return true
        return try {
            val resolver = ctx.contentResolver ?: return true
            val autoTime = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 1) == 1
            val autoTimeZone = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1
            autoTime && autoTimeZone
        } catch (e: Exception) {
            true
        }
    }

    private fun getCurrentBootCount(context: Context? = null): Int {
        val ctx = resolveContext(context) ?: return -1
        return try {
            val resolver = ctx.contentResolver ?: return -1
            Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)
        } catch (e: Exception) {
            -1
        }
    }

    fun init(context: Context) {
        synchronized(lock) {
            val ctx = resolveContext(context) ?: context
            if (prefs == null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            loadPersistedAnchors(ctx)
        }
    }

    private fun loadPersistedAnchors(context: Context? = null) {
        val ctx = resolveContext(context)
        val p = prefs ?: (ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.also { prefs = it }) ?: return

        isProtectionEnabled = p.getBoolean(KEY_PROTECTION_ENABLED, true)

        val currentWall = System.currentTimeMillis()
        val currentElapsed = SystemClock.elapsedRealtime()
        val currentBootCount = getCurrentBootCount(ctx)

        val storedAnchorWall = p.getLong(KEY_ANCHOR_WALL, 0L)
        val storedAnchorElapsed = p.getLong(KEY_ANCHOR_ELAPSED, 0L)
        val storedBootCount = p.getInt(KEY_SAVED_BOOT_COUNT, -1)
        val autoTimeEnabled = isSystemAutoTimeEnabled(ctx)

        val bootChanged = (currentBootCount != -1 && storedBootCount != -1 && currentBootCount != storedBootCount) ||
                (storedBootCount == -1 && currentBootCount != -1)
        val elapsedRebooted = storedAnchorElapsed <= 0L || currentElapsed < storedAnchorElapsed

        if (!isProtectionEnabled ||
            autoTimeEnabled ||
            storedAnchorWall <= 0L ||
            bootChanged ||
            elapsedRebooted
        ) {
            // Re-anchor cleanly to current wall clock and elapsedRealtime with zero drift
            reAnchorInternal(currentWall, currentElapsed, currentBootCount)
            Log.d(
                TAG,
                "Anchored cleanly: wall=$anchorWallTimeMillis, elapsed=$anchorElapsedRealtimeMillis, " +
                    "bootCount=$savedBootCount, autoTime=$autoTimeEnabled"
            )
        } else {
            anchorWallTimeMillis = storedAnchorWall
            anchorElapsedRealtimeMillis = storedAnchorElapsed
            savedBootCount = currentBootCount
            lastRecordedTrueTimeMillis = p.getLong(KEY_LAST_TRUE_TIME, currentWall)
            evaluateIntegrityInternal(ctx, currentWall, currentElapsed, currentBootCount)
        }
    }

    private fun reAnchorInternal(
        currentWall: Long = System.currentTimeMillis(),
        currentElapsed: Long = SystemClock.elapsedRealtime(),
        currentBootCount: Int = getCurrentBootCount()
    ) {
        anchorWallTimeMillis = currentWall
        anchorElapsedRealtimeMillis = currentElapsed
        lastRecordedTrueTimeMillis = currentWall
        if (currentBootCount != -1) {
            savedBootCount = currentBootCount
        }
        setTampered(false)
        persistAnchors()
    }

    /**
     * Evaluates clock integrity and returns the trusted current epoch time in milliseconds.
     *
     * - If Clock Tamper Protection is OFF, or Automatic Date & Time + Time Zone are ON:
     *   keeps the hardware anchor synced to System.currentTimeMillis() & SystemClock.elapsedRealtime(),
     *   clears any drift warning, and returns System.currentTimeMillis().
     * - If the device rebooted (BOOT_COUNT changed or elapsedRealtime < anchorElapsedRealtime):
     *   immediately re-anchors with zero drift and returns System.currentTimeMillis().
     * - Only if Protection is ON, Auto Date & Time is OFF, and wall clock diverges from the
     *   same-boot elapsedRealtime anchor by > 3 minutes (180,000 ms):
     *   flags "Clock drift detected!" and returns the hardware-anchored monotonic time.
     */
    fun getTrustedCurrentTimeMillis(context: Context? = null): Long {
        synchronized(lock) {
            val ctx = resolveContext(context)
            if (prefs == null && ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadPersistedAnchors(ctx)
            }

            val currentWall = System.currentTimeMillis()
            val currentElapsed = SystemClock.elapsedRealtime()
            val currentBootCount = getCurrentBootCount(ctx)

            return evaluateIntegrityInternal(ctx, currentWall, currentElapsed, currentBootCount)
        }
    }

    /**
     * Alias for [getTrustedCurrentTimeMillis] used across the app.
     */
    fun getTrueTimeMillis(): Long = getTrustedCurrentTimeMillis(null)

    private fun evaluateIntegrityInternal(
        ctx: Context?,
        currentWall: Long,
        currentElapsed: Long,
        currentBootCount: Int
    ): Long {
        // 1. If protection is disabled, never flag drift and stay anchored to real wall time
        if (!isProtectionEnabled) {
            reAnchorInternal(currentWall, currentElapsed, currentBootCount)
            return currentWall
        }

        // 2. Handle first run or device reboot (BOOT_COUNT change or elapsedRealtime reset)
        val bootChanged = currentBootCount != -1 && savedBootCount != -1 && currentBootCount != savedBootCount
        val elapsedReset = anchorWallTimeMillis <= 0L ||
                anchorElapsedRealtimeMillis <= 0L ||
                currentElapsed < anchorElapsedRealtimeMillis

        if (bootChanged || elapsedReset) {
            Log.i(
                TAG,
                "Device boot/anchor reset detected (bootChanged=$bootChanged, elapsedReset=$elapsedReset). Re-anchoring with zero drift."
            )
            reAnchorInternal(currentWall, currentElapsed, currentBootCount)
            return currentWall
        }

        // 3. Check Android's native AUTO_TIME & AUTO_TIME_ZONE first (Zero False Positives)
        if (isSystemAutoTimeEnabled(ctx)) {
            anchorWallTimeMillis = currentWall
            anchorElapsedRealtimeMillis = currentElapsed
            lastRecordedTrueTimeMillis = currentWall
            if (currentBootCount != -1) {
                savedBootCount = currentBootCount
            }
            if (_isClockTamperedFlow.value) {
                setTampered(false)
            }
            persistAnchorsThrottled(currentWall)
            return currentWall
        }

        // 4. Auto Time is OFF and Protection is ON: compare wall clock against same-boot elapsedRealtime anchor
        val elapsedSinceAnchor = (currentElapsed - anchorElapsedRealtimeMillis).coerceAtLeast(0L)
        val hardwareAnchoredTime = anchorWallTimeMillis + elapsedSinceAnchor
        val drift = abs(currentWall - hardwareAnchoredTime)

        return if (drift > MAX_ALLOWED_DRIFT_MS) {
            if (!_isClockTamperedFlow.value) {
                Log.w(
                    TAG,
                    "Manual clock drift detected while AUTO_TIME is OFF! Drift=${drift}ms > ${MAX_ALLOWED_DRIFT_MS}ms " +
                        "(wall=$currentWall, hardware=$hardwareAnchoredTime)"
                )
            }
            setTampered(true)
            lastRecordedTrueTimeMillis = hardwareAnchoredTime
            persistAnchorsThrottled(hardwareAnchoredTime)
            hardwareAnchoredTime
        } else {
            // Within 3-minute tolerance: clear any prior drift flag and use accurate wall time
            if (_isClockTamperedFlow.value) {
                setTampered(false)
            }
            lastRecordedTrueTimeMillis = currentWall
            persistAnchorsThrottled(currentWall)
            currentWall
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
    fun onTimeChanged(context: Context? = null) {
        synchronized(lock) {
            val ctx = resolveContext(context)
            val currentWall = System.currentTimeMillis()
            val currentElapsed = SystemClock.elapsedRealtime()
            val currentBootCount = getCurrentBootCount(ctx)
            Log.i(TAG, "ACTION_TIME_CHANGED / TIMEZONE_CHANGED received. Validating integrity...")
            evaluateIntegrityInternal(ctx, currentWall, currentElapsed, currentBootCount)
        }
    }

    /**
     * Called by BootReceiver when ACTION_BOOT_COMPLETED or ACTION_MY_PACKAGE_REPLACED is received.
     */
    fun onBootCompleted(context: Context) {
        synchronized(lock) {
            val ctx = resolveContext(context) ?: context
            if (prefs == null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            isProtectionEnabled = prefs?.getBoolean(KEY_PROTECTION_ENABLED, true) ?: true
            // Device just booted or package replaced: re-anchor cleanly with zero drift
            reAnchorInternal(
                currentWall = System.currentTimeMillis(),
                currentElapsed = SystemClock.elapsedRealtime(),
                currentBootCount = getCurrentBootCount(ctx)
            )
        }
    }

    /**
     * Returns true if time tampering is currently active.
     */
    fun isClockTampered(): Boolean {
        // Re-verify auto-time so turning AUTO_TIME back on immediately clears any stale flag
        getTrustedCurrentTimeMillis(null)
        return _isClockTamperedFlow.value
    }

    /**
     * Returns a Calendar initialized to the current local TimeZone and trusted current time.
     */
    fun getTrueCalendar(
        timeZone: TimeZone = TimeZone.getDefault(),
        context: Context? = null
    ): Calendar {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = getTrustedCurrentTimeMillis(context)
        return cal
    }

    /**
     * Returns today's date formatted as "yyyy-MM-dd" evaluated against trusted local time.
     */
    fun getTrueDateString(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val trueMillis = getTrustedCurrentTimeMillis(null)
        val instant = Instant.ofEpochMilli(trueMillis)
        val localDate = LocalDate.ofInstant(instant, zoneId)
        return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    fun isProtectionEnabled(): Boolean = isProtectionEnabled

    fun setProtectionEnabled(enabled: Boolean) {
        synchronized(lock) {
            isProtectionEnabled = enabled
            prefs?.edit()?.putBoolean(KEY_PROTECTION_ENABLED, enabled)?.apply()
            // Re-anchor cleanly whenever the user toggles Clock Tamper Protection ON or OFF
            reAnchorInternal(
                currentWall = System.currentTimeMillis(),
                currentElapsed = SystemClock.elapsedRealtime(),
                currentBootCount = getCurrentBootCount()
            )
        }
    }

    fun resetTamperFlag(context: Context? = null) {
        synchronized(lock) {
            val ctx = resolveContext(context)
            reAnchorInternal(
                currentWall = System.currentTimeMillis(),
                currentElapsed = SystemClock.elapsedRealtime(),
                currentBootCount = getCurrentBootCount(ctx)
            )
            Log.i(TAG, "Tamper flag cleared and re-anchored to current wall time.")
        }
    }

    private var lastPersistElapsedMs: Long = 0L

    private fun persistAnchorsThrottled(currentTrueTime: Long) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastPersistElapsedMs >= 30_000L || lastPersistElapsedMs == 0L) {
            lastPersistElapsedMs = nowElapsed
            persistAnchors(currentTrueTime)
        }
    }

    private fun persistAnchors(currentTrueTime: Long = lastRecordedTrueTimeMillis) {
        prefs?.edit()
            ?.putLong(KEY_ANCHOR_WALL, anchorWallTimeMillis)
            ?.putLong(KEY_ANCHOR_ELAPSED, anchorElapsedRealtimeMillis)
            ?.putLong(KEY_LAST_TRUE_TIME, currentTrueTime)
            ?.putInt(KEY_SAVED_BOOT_COUNT, savedBootCount)
            ?.putBoolean(KEY_TAMPER_FLAG, _isClockTamperedFlow.value)
            ?.apply()
    }
}
