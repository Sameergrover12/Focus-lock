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
 * TimeIntegrityManager provides 100% offline, hardware-anchored, always-on time integrity
 * enforcement across Android 8–15+ with zero false positives.
 *
 * Security Rules:
 * 1. Always-On: Clock Tamper Protection cannot be disabled (`isProtectionEnabled()` is permanently true).
 * 2. Monotonic Base: Uses `SystemClock.elapsedRealtime()` exclusively (never `uptimeMillis()`).
 * 3. Zero False Positives: When BOTH `AUTO_TIME == 1` and `AUTO_TIME_ZONE == 1` are enabled,
 *    the system clock and timezone are network-verified:
 *    - Never flags tamper.
 *    - Continuously updates and persists wall, elapsed, boot count, timezone ID, and high-water mark.
 *    - Evaluates schedules, daily limits, sessions, and resets using real local wall time and timezone.
 * 4. Anti-Clock & Anti-Timezone Spoofing: The moment `AUTO_TIME == 0` OR `AUTO_TIME_ZONE == 0`:
 *    - Freezes `anchorWallTimeMillis`, `anchorElapsedRealtimeMillis`, and `anchorTimeZoneId`.
 *    - Computes `hardwareTrueTimeMillis = anchorWallTimeMillis + (elapsedRealtime() - anchorElapsedRealtimeMillis)`.
 *    - Triggers `isTampered = true` immediately if `abs(currentWall - hardwareTrueTimeMillis) > 120_000L` (2 minutes)
 *      OR `(!autoTimeZone && TimeZone.getDefault().id != anchorTimeZoneId)`.
 *    - Enforces all rules using `hardwareTrueTimeMillis` and `TimeZone.getTimeZone(anchorTimeZoneId)`.
 * 5. Post-Reboot Backward Rollback Defense: Tracks `lastRecordedTrueTimeMillis` high-water mark alongside
 *    `Settings.Global.BOOT_COUNT`. If a user turns off Auto-Time, sets the clock backward, and reboots,
 *    on boot `currentWall < lastRecordedTrueTimeMillis` is caught immediately, flagging tamper and anchoring
 *    hardware true time at `lastRecordedTrueTimeMillis`.
 */
object TimeIntegrityManager {

    private const val TAG = "TimeIntegrityManager"
    private const val PREFS_NAME = "focus_time_integrity_prefs"
    private const val KEY_ANCHOR_WALL = "anchor_wall_time"
    private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed_realtime"
    private const val KEY_ANCHOR_TIMEZONE_ID = "anchor_timezone_id"
    private const val KEY_LAST_TRUE_TIME = "last_recorded_true_time"
    private const val KEY_SAVED_BOOT_COUNT = "saved_boot_count"
    private const val KEY_TAMPER_FLAG = "tamper_detected_flag"
    private const val KEY_PROTECTION_ENABLED = "time_integrity_enabled"

    // 2 minutes (120,000 ms) threshold before flagging manual clock manipulation
    const val MAX_ALLOWED_DRIFT_MS = 120_000L

    private val lock = Any()
    @Volatile private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private var anchorWallTimeMillis: Long = 0L
    private var anchorElapsedRealtimeMillis: Long = -1L
    private var anchorTimeZoneId: String = ""
    private var lastRecordedTrueTimeMillis: Long = 0L
    private var savedBootCount: Int = -1

    private val _isClockTamperedFlow = MutableStateFlow(false)
    val isClockTamperedFlow: StateFlow<Boolean> = _isClockTamperedFlow.asStateFlow()

    internal var wallClockProvider: () -> Long = { System.currentTimeMillis() }
    internal var elapsedClockProvider: () -> Long = { SystemClock.elapsedRealtime() }

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

    fun isSystemAutoTimeSettingEnabled(context: Context? = null): Boolean {
        val ctx = resolveContext(context) ?: return true
        return try {
            val resolver = ctx.contentResolver ?: return true
            Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    fun isSystemAutoTimeZoneSettingEnabled(context: Context? = null): Boolean {
        val ctx = resolveContext(context) ?: return true
        return try {
            val resolver = ctx.contentResolver ?: return true
            Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Checks whether BOTH Android's Automatic Date & Time and Automatic Time Zone are enabled.
     */
    fun isSystemAutoTimeEnabled(context: Context? = null): Boolean {
        return isSystemAutoTimeSettingEnabled(context) && isSystemAutoTimeZoneSettingEnabled(context)
    }

    private fun getCurrentBootCount(context: Context? = null): Int {
        val ctx = resolveContext(context) ?: return -1
        return try {
            val resolver = ctx.contentResolver ?: return -1
            Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
    }

    private fun currentDefaultTimeZoneId(): String {
        return try {
            TimeZone.getDefault().id.takeIf { it.isNotBlank() } ?: "UTC"
        } catch (_: Exception) {
            "UTC"
        }
    }

    fun init(context: Context) {
        synchronized(lock) {
            val ctx = resolveContext(context) ?: context
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadPersistedAnchors(ctx)
        }
    }

    private fun loadPersistedAnchors(context: Context? = null) {
        val ctx = resolveContext(context)
        val p = prefs ?: (ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.also { prefs = it }) ?: return

        // Always force protection enabled in SharedPreferences (ignore any legacy false value)
        if (!p.getBoolean(KEY_PROTECTION_ENABLED, true)) {
            p.edit().putBoolean(KEY_PROTECTION_ENABLED, true).apply()
        }

        val currentWall = wallClockProvider()
        val currentElapsed = elapsedClockProvider()
        val currentBootCount = getCurrentBootCount(ctx)
        val currentTzId = currentDefaultTimeZoneId()

        val storedAnchorWall = p.getLong(KEY_ANCHOR_WALL, 0L)
        val storedAnchorElapsed = p.getLong(KEY_ANCHOR_ELAPSED, -1L)
        val storedAnchorTz = p.getString(KEY_ANCHOR_TIMEZONE_ID, "") ?: ""
        val storedLastTrue = p.getLong(KEY_LAST_TRUE_TIME, 0L)
        val storedBootCount = p.getInt(KEY_SAVED_BOOT_COUNT, -1)

        anchorWallTimeMillis = storedAnchorWall
        anchorElapsedRealtimeMillis = storedAnchorElapsed
        anchorTimeZoneId = storedAnchorTz
        lastRecordedTrueTimeMillis = storedLastTrue
        savedBootCount = storedBootCount
        _isClockTamperedFlow.value = p.getBoolean(KEY_TAMPER_FLAG, false)

        evaluateIntegrityInternal(
            ctx = ctx,
            currentWall = currentWall,
            currentElapsed = currentElapsed,
            currentBootCount = currentBootCount,
            currentTzId = currentTzId
        )
    }

    private fun reAnchorInternal(
        currentWall: Long = wallClockProvider(),
        currentElapsed: Long = elapsedClockProvider(),
        currentBootCount: Int = getCurrentBootCount(),
        currentTzId: String = currentDefaultTimeZoneId(),
        tampered: Boolean = false
    ) {
        anchorWallTimeMillis = currentWall
        anchorElapsedRealtimeMillis = currentElapsed
        anchorTimeZoneId = currentTzId
        lastRecordedTrueTimeMillis = maxOf(lastRecordedTrueTimeMillis, currentWall)
        if (currentBootCount != -1) {
            savedBootCount = currentBootCount
        }
        setTampered(tampered)
        persistAnchors(lastRecordedTrueTimeMillis)
    }

    /**
     * Evaluates clock and timezone integrity and returns the trusted epoch time in milliseconds.
     *
     * - When BOTH `AUTO_TIME == 1` and `AUTO_TIME_ZONE == 1`:
     *   continuously refreshes hardware anchors, clears any tamper warning, and returns `currentWall`.
     * - When `AUTO_TIME == 0` OR `AUTO_TIME_ZONE == 0`:
     *   freezes the last trusted wall/elapsed/timezone anchor, checks for backward rollback across reboot,
     *   wall clock drift > 2 minutes (120,000 ms), or manual timezone spoofing, and returns
     *   `hardwareTrueTimeMillis` while enforcing rules on the frozen `anchorTimeZoneId`.
     */
    fun getTrustedCurrentTimeMillis(context: Context? = null): Long {
        synchronized(lock) {
            val ctx = resolveContext(context)
            if (prefs == null && ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadPersistedAnchors(ctx)
            }

            val currentWall = wallClockProvider()
            val currentElapsed = elapsedClockProvider()
            val currentBootCount = getCurrentBootCount(ctx)
            val currentTzId = currentDefaultTimeZoneId()

            return evaluateIntegrityInternal(
                ctx = ctx,
                currentWall = currentWall,
                currentElapsed = currentElapsed,
                currentBootCount = currentBootCount,
                currentTzId = currentTzId
            )
        }
    }

    /**
     * Alias for [getTrustedCurrentTimeMillis] used across the app.
     */
    fun getTrueTimeMillis(): Long = getTrustedCurrentTimeMillis(null)

    /**
     * Returns the trusted TimeZone for schedule & midnight evaluations.
     * When automatic timezone is OFF and timezone differs from the frozen anchor timezone,
     * returns the frozen anchor timezone so changing timezone in Settings cannot shift schedules.
     */
    fun getTrueTimeZone(context: Context? = null): TimeZone {
        synchronized(lock) {
            val ctx = resolveContext(context)
            if (prefs == null && ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadPersistedAnchors(ctx)
            }
            val autoTz = isSystemAutoTimeZoneSettingEnabled(ctx)
            if (!autoTz && anchorTimeZoneId.isNotBlank()) {
                return TimeZone.getTimeZone(anchorTimeZoneId)
            }
            return TimeZone.getDefault()
        }
    }

    private fun evaluateIntegrityInternal(
        ctx: Context?,
        currentWall: Long,
        currentElapsed: Long,
        currentBootCount: Int,
        currentTzId: String
    ): Long {
        val autoTime = isSystemAutoTimeSettingEnabled(ctx)
        val autoTimeZone = isSystemAutoTimeZoneSettingEnabled(ctx)
        val bothAutoEnabled = autoTime && autoTimeZone

        // 1. Both Automatic Date & Time AND Automatic Time Zone are ON -> Zero False Positives
        if (bothAutoEnabled) {
            val wasTampered = _isClockTamperedFlow.value
            val tzChanged = anchorTimeZoneId != currentTzId
            val bootChanged = currentBootCount != -1 && savedBootCount != currentBootCount
            anchorWallTimeMillis = currentWall
            anchorElapsedRealtimeMillis = currentElapsed
            anchorTimeZoneId = currentTzId
            lastRecordedTrueTimeMillis = currentWall
            if (currentBootCount != -1) {
                savedBootCount = currentBootCount
            }
            if (wasTampered) {
                setTampered(false)
            }
            if (wasTampered || tzChanged || bootChanged) {
                persistAnchors(currentWall)
            } else {
                persistAnchorsThrottled(currentWall)
            }
            return currentWall
        }

        // 2. First-ever initialization (no persisted anchor yet)
        if (anchorWallTimeMillis <= 0L || anchorElapsedRealtimeMillis < 0L) {
            reAnchorInternal(
                currentWall = currentWall,
                currentElapsed = currentElapsed,
                currentBootCount = currentBootCount,
                currentTzId = currentTzId,
                tampered = false
            )
            return currentWall
        }

        // 3. Handle device reboot / elapsedRealtime reset while AUTO_TIME or AUTO_TIME_ZONE is OFF
        val bootChanged = (currentBootCount != -1 && savedBootCount != -1 && currentBootCount != savedBootCount) ||
            (savedBootCount == -1 && currentBootCount != -1)
        val elapsedReset = currentElapsed < anchorElapsedRealtimeMillis

        if (bootChanged || elapsedReset) {
            val highWater = lastRecordedTrueTimeMillis
            val rolledBackAcrossReboot = !autoTime && highWater > 0L && currentWall < highWater
            val tzSpoofedAcrossReboot = !autoTimeZone && anchorTimeZoneId.isNotBlank() && currentTzId != anchorTimeZoneId

            val resolvedTrueWall = if (rolledBackAcrossReboot) {
                highWater
            } else {
                currentWall
            }
            val resolvedTz = if (!autoTimeZone && anchorTimeZoneId.isNotBlank()) {
                anchorTimeZoneId
            } else {
                currentTzId
            }
            val tamperedOnBoot = rolledBackAcrossReboot || tzSpoofedAcrossReboot

            if (tamperedOnBoot) {
                Log.w(
                    TAG,
                    "Post-reboot clock/timezone tamper detected! rolledBack=$rolledBackAcrossReboot " +
                        "(wall=$currentWall, highWater=$highWater), tzSpoof=$tzSpoofedAcrossReboot ($currentTzId != $anchorTimeZoneId)"
                )
            } else {
                Log.i(
                    TAG,
                    "Boot/elapsed reset while manual time/tz active; preserving safe anchor (wall=$resolvedTrueWall, tz=$resolvedTz)."
                )
            }

            anchorWallTimeMillis = resolvedTrueWall
            anchorElapsedRealtimeMillis = currentElapsed
            anchorTimeZoneId = resolvedTz
            lastRecordedTrueTimeMillis = maxOf(lastRecordedTrueTimeMillis, resolvedTrueWall)
            if (currentBootCount != -1) {
                savedBootCount = currentBootCount
            }
            setTampered(tamperedOnBoot)
            persistAnchors(lastRecordedTrueTimeMillis)
            return resolvedTrueWall
        }

        // 4. Same-boot check while AUTO_TIME == 0 OR AUTO_TIME_ZONE == 0:
        //    Freeze anchors (`anchorWallTimeMillis`, `anchorElapsedRealtimeMillis`, `anchorTimeZoneId`)!
        if (anchorTimeZoneId.isBlank()) {
            anchorTimeZoneId = currentTzId
        }
        val elapsedSinceAnchor = (currentElapsed - anchorElapsedRealtimeMillis).coerceAtLeast(0L)
        val hardwareTrueTime = anchorWallTimeMillis + elapsedSinceAnchor
        val drift = abs(currentWall - hardwareTrueTime)

        val clockTampered = !autoTime && (
            drift > MAX_ALLOWED_DRIFT_MS ||
                (lastRecordedTrueTimeMillis > 0L && currentWall + MAX_ALLOWED_DRIFT_MS < lastRecordedTrueTimeMillis)
            )
        val timezoneTampered = !autoTimeZone && currentTzId != anchorTimeZoneId
        val tampered = clockTampered || timezoneTampered

        return if (tampered) {
            if (!_isClockTamperedFlow.value) {
                Log.w(
                    TAG,
                    "Tamper detected! clockTampered=$clockTampered (drift=${drift}ms, wall=$currentWall, hardware=$hardwareTrueTime), " +
                        "tzTampered=$timezoneTampered ($currentTzId vs anchor $anchorTimeZoneId)"
                )
            }
            setTampered(true)
            val trustedNow = maxOf(hardwareTrueTime, lastRecordedTrueTimeMillis)
            lastRecordedTrueTimeMillis = trustedNow
            persistAnchorsThrottled(trustedNow)
            trustedNow
        } else {
            // Within 2-minute tolerance AND timezone matches anchorTimeZoneId
            if (_isClockTamperedFlow.value) {
                setTampered(false)
            }
            val trustedNow = if (autoTime) currentWall else maxOf(hardwareTrueTime, lastRecordedTrueTimeMillis)
            lastRecordedTrueTimeMillis = maxOf(lastRecordedTrueTimeMillis, trustedNow)
            // Keep anchorTimeZoneId synced ONLY if autoTimeZone is ON
            if (autoTimeZone) {
                anchorTimeZoneId = currentTzId
            }
            persistAnchorsThrottled(lastRecordedTrueTimeMillis)
            trustedNow
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
            if (prefs == null && ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                loadPersistedAnchors(ctx)
            }
            val currentWall = wallClockProvider()
            val currentElapsed = elapsedClockProvider()
            val currentBootCount = getCurrentBootCount(ctx)
            val currentTzId = currentDefaultTimeZoneId()
            Log.i(TAG, "ACTION_TIME_CHANGED / TIMEZONE_CHANGED received. Validating integrity...")
            evaluateIntegrityInternal(ctx, currentWall, currentElapsed, currentBootCount, currentTzId)
        }
    }

    /**
     * Called by BootReceiver when ACTION_BOOT_COMPLETED or ACTION_MY_PACKAGE_REPLACED is received.
     * Validates high-water mark and timezone across reboot instead of blindly resetting.
     */
    fun onBootCompleted(context: Context) {
        synchronized(lock) {
            val ctx = resolveContext(context) ?: context
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadPersistedAnchors(ctx)
        }
    }

    /**
     * Returns true if clock or timezone tampering is currently active.
     */
    fun isClockTampered(context: Context? = null): Boolean {
        getTrustedCurrentTimeMillis(context)
        return _isClockTamperedFlow.value
    }

    /**
     * Returns a Calendar initialized to the trusted TimeZone and trusted epoch time.
     */
    fun getTrueCalendar(
        timeZone: TimeZone? = null,
        context: Context? = null
    ): Calendar {
        val trustedMillis = getTrustedCurrentTimeMillis(context)
        val resolvedZone = timeZone ?: getTrueTimeZone(context)
        return Calendar.getInstance(resolvedZone).apply {
            timeInMillis = trustedMillis
        }
    }

    /**
     * Returns today's date formatted as "yyyy-MM-dd" evaluated against trusted time and trusted timezone.
     */
    fun getTrueDateString(zoneId: ZoneId? = null, context: Context? = null): String {
        val trueMillis = getTrustedCurrentTimeMillis(context)
        val resolvedZone = zoneId ?: getTrueTimeZone(context).toZoneId()
        val instant = Instant.ofEpochMilli(trueMillis)
        val localDate = LocalDate.ofInstant(instant, resolvedZone)
        return localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    /**
     * Always-on protection: cannot be turned off.
     */
    fun isProtectionEnabled(): Boolean = true

    /**
     * Kept for source compatibility; ignores any attempt to disable protection.
     */
    fun setProtectionEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        synchronized(lock) {
            prefs?.edit()?.putBoolean(KEY_PROTECTION_ENABLED, true)?.apply()
        }
    }

    fun resetTamperFlag(context: Context? = null) {
        synchronized(lock) {
            val ctx = resolveContext(context)
            reAnchorInternal(
                currentWall = wallClockProvider(),
                currentElapsed = elapsedClockProvider(),
                currentBootCount = getCurrentBootCount(ctx),
                currentTzId = currentDefaultTimeZoneId(),
                tampered = false
            )
            Log.i(TAG, "Tamper flag cleared and re-anchored to current wall time.")
        }
    }

    private var lastPersistElapsedMs: Long = -1L

    private fun persistAnchorsThrottled(currentTrueTime: Long) {
        val nowElapsed = elapsedClockProvider()
        if (lastPersistElapsedMs < 0L || nowElapsed - lastPersistElapsedMs >= 30_000L) {
            lastPersistElapsedMs = nowElapsed
            persistAnchors(currentTrueTime)
        }
    }

    private fun persistAnchors(currentTrueTime: Long = lastRecordedTrueTimeMillis) {
        prefs?.edit()
            ?.putBoolean(KEY_PROTECTION_ENABLED, true)
            ?.putLong(KEY_ANCHOR_WALL, anchorWallTimeMillis)
            ?.putLong(KEY_ANCHOR_ELAPSED, anchorElapsedRealtimeMillis)
            ?.putString(KEY_ANCHOR_TIMEZONE_ID, anchorTimeZoneId)
            ?.putLong(KEY_LAST_TRUE_TIME, currentTrueTime)
            ?.putInt(KEY_SAVED_BOOT_COUNT, savedBootCount)
            ?.putBoolean(KEY_TAMPER_FLAG, _isClockTamperedFlow.value)
            ?.apply()
    }
}
