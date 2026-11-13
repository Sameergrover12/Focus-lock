package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import com.example.FocusApplication
import com.example.receiver.KillswitchReceiver
import com.example.service.FocusAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central Killswitch Manager.
 *
 * Enforces a strict full-phone lockdown for deep focus.
 * When engaged, all applications on the device are locked down except
 * Focus Lock, the Home launcher, System UI, and the phone/dialer.
 * Allows exactly one 10-minute emergency break per Killswitch session.
 */
object KillswitchManager {

    private const val TAG = "KillswitchManager"
    const val PREFS_NAME = "focus_killswitch_prefs"
    const val KEY_END_TIME = "killswitch_end_time"
    const val KEY_END_ELAPSED = "killswitch_end_elapsed"
    const val KEY_BREAK_USED = "killswitch_break_used"
    const val KEY_BREAK_END_TIME = "killswitch_break_end_time"
    const val KEY_BREAK_END_ELAPSED = "killswitch_break_end_elapsed"

    const val BREAK_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    const val REQ_CODE_SESSION = 9040
    const val REQ_CODE_BREAK = 9041

    @Volatile private var killswitchEndTime: Long = 0L
    @Volatile private var killswitchEndElapsed: Long = 0L
    @Volatile private var killswitchBreakUsed: Boolean = false
    @Volatile private var killswitchBreakEndTime: Long = 0L
    @Volatile private var killswitchBreakEndElapsed: Long = 0L
    @Volatile private var initialized = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionExpiredRunnable = Runnable {
        Log.i(TAG, "Killswitch session expired on Main thread.")
        onKillswitchExpired()
    }
    private val breakExpiredRunnable = Runnable {
        Log.i(TAG, "Killswitch 10-minute emergency break expired on Main thread.")
        onBreakExpired()
    }

    private val _isKillswitchActiveFlow = MutableStateFlow(false)
    val isKillswitchActiveFlow: StateFlow<Boolean> = _isKillswitchActiveFlow.asStateFlow()

    private val _isEmergencyBreakActiveFlow = MutableStateFlow(false)
    val isEmergencyBreakActiveFlow: StateFlow<Boolean> = _isEmergencyBreakActiveFlow.asStateFlow()

    private val _killswitchEndTimeFlow = MutableStateFlow(0L)
    val killswitchEndTimeFlow: StateFlow<Long> = _killswitchEndTimeFlow.asStateFlow()

    private val _breakEndTimeFlow = MutableStateFlow(0L)
    val breakEndTimeFlow: StateFlow<Long> = _breakEndTimeFlow.asStateFlow()

    private val _breakUsedFlow = MutableStateFlow(false)
    val breakUsedFlow: StateFlow<Boolean> = _breakUsedFlow.asStateFlow()

    private var cachedLauncherPackage: String? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        killswitchEndTime = prefs.getLong(KEY_END_TIME, 0L)
        killswitchEndElapsed = prefs.getLong(KEY_END_ELAPSED, 0L)
        killswitchBreakUsed = prefs.getBoolean(KEY_BREAK_USED, false)
        killswitchBreakEndTime = prefs.getLong(KEY_BREAK_END_TIME, 0L)
        killswitchBreakEndElapsed = prefs.getLong(KEY_BREAK_END_ELAPSED, 0L)

        val now = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
        val nowElapsed = SystemClock.elapsedRealtime()

        val isSessionActive = (killswitchEndTime > now) &&
                (killswitchEndElapsed <= 0L || killswitchEndElapsed > nowElapsed)

        if (isSessionActive) {
            val remainingSessionMs = minOf(
                killswitchEndTime - now,
                if (killswitchEndElapsed > 0L) killswitchEndElapsed - nowElapsed else Long.MAX_VALUE
            ).coerceAtLeast(0L)
            mainHandler.removeCallbacks(sessionExpiredRunnable)
            mainHandler.postDelayed(sessionExpiredRunnable, remainingSessionMs)
            _isKillswitchActiveFlow.value = true
            _killswitchEndTimeFlow.value = killswitchEndTime
            _breakUsedFlow.value = killswitchBreakUsed

            val isBreakActive = (killswitchBreakEndTime > now) &&
                    (killswitchBreakEndElapsed <= 0L || killswitchBreakEndElapsed > nowElapsed)

            if (isBreakActive) {
                val remainingBreakMs = minOf(
                    killswitchBreakEndTime - now,
                    if (killswitchBreakEndElapsed > 0L) killswitchBreakEndElapsed - nowElapsed else Long.MAX_VALUE
                ).coerceAtLeast(0L)
                mainHandler.removeCallbacks(breakExpiredRunnable)
                mainHandler.postDelayed(breakExpiredRunnable, remainingBreakMs)
                _isEmergencyBreakActiveFlow.value = true
                _breakEndTimeFlow.value = killswitchBreakEndTime
            } else {
                killswitchBreakEndTime = 0L
                killswitchBreakEndElapsed = 0L
                _isEmergencyBreakActiveFlow.value = false
                _breakEndTimeFlow.value = 0L
            }
        } else {
            resetState(prefs)
        }
        initialized = true
    }

    private fun resetState(prefs: android.content.SharedPreferences) {
        killswitchEndTime = 0L
        killswitchEndElapsed = 0L
        killswitchBreakUsed = false
        killswitchBreakEndTime = 0L
        killswitchBreakEndElapsed = 0L
        prefs.edit()
            .putLong(KEY_END_TIME, 0L)
            .putLong(KEY_END_ELAPSED, 0L)
            .putBoolean(KEY_BREAK_USED, false)
            .putLong(KEY_BREAK_END_TIME, 0L)
            .putLong(KEY_BREAK_END_ELAPSED, 0L)
            .apply()
        _isKillswitchActiveFlow.value = false
        _isEmergencyBreakActiveFlow.value = false
        _killswitchEndTimeFlow.value = 0L
        _breakEndTimeFlow.value = 0L
        _breakUsedFlow.value = false
    }

    /**
     * Fast 0ms memory check for AccessibilityService.
     */
    fun isKillswitchActive(context: Context? = null): Boolean {
        if (!initialized && context != null) {
            init(context.applicationContext)
        }
        val end = killswitchEndTime
        if (end <= 0L) return false

        val now = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedEnd = killswitchEndElapsed

        val active = (now < end) && (elapsedEnd <= 0L || nowElapsed < elapsedEnd)
        if (!active) {
            if (killswitchEndTime != 0L) {
                onKillswitchExpired(context)
            }
            return false
        }
        return true
    }

    /**
     * Fast check for emergency break during an active Killswitch session.
     */
    fun isEmergencyBreakActive(context: Context? = null): Boolean {
        if (!isKillswitchActive(context)) return false
        val breakEnd = killswitchBreakEndTime
        if (breakEnd <= 0L) return false

        val now = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedEnd = killswitchBreakEndElapsed

        val active = (now < breakEnd) && (elapsedEnd <= 0L || nowElapsed < elapsedEnd)
        if (!active) {
            if (killswitchBreakEndTime != 0L) {
                onBreakExpired(context)
            }
            return false
        }
        return true
    }

    fun isBreakUsed(): Boolean = killswitchBreakUsed

    fun getKillswitchEndTime(): Long = killswitchEndTime

    fun getBreakEndTime(): Long = killswitchBreakEndTime

    /**
     * Starts a full-phone lockdown session for [durationMinutes].
     */
    fun startKillswitch(context: Context, durationMinutes: Int) {
        if (!initialized) {
            init(context.applicationContext)
        }
        val durationMs = durationMinutes * 60_000L
        val now = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
        val nowElapsed = SystemClock.elapsedRealtime()

        killswitchEndTime = now + durationMs
        killswitchEndElapsed = nowElapsed + durationMs
        killswitchBreakUsed = false
        killswitchBreakEndTime = 0L
        killswitchBreakEndElapsed = 0L

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_END_TIME, killswitchEndTime)
            .putLong(KEY_END_ELAPSED, killswitchEndElapsed)
            .putBoolean(KEY_BREAK_USED, false)
            .putLong(KEY_BREAK_END_TIME, 0L)
            .putLong(KEY_BREAK_END_ELAPSED, 0L)
            .apply()

        _isKillswitchActiveFlow.value = true
        _isEmergencyBreakActiveFlow.value = false
        _killswitchEndTimeFlow.value = killswitchEndTime
        _breakEndTimeFlow.value = 0L
        _breakUsedFlow.value = false

        // Schedule expiration via Handler
        mainHandler.removeCallbacks(sessionExpiredRunnable)
        mainHandler.postDelayed(sessionExpiredRunnable, durationMs)

        // Schedule exact ELAPSED_REALTIME_WAKEUP AlarmManager fallback
        scheduleAlarmFallback(
            context,
            KillswitchReceiver.ACTION_SESSION_EXPIRED,
            killswitchEndElapsed,
            REQ_CODE_SESSION
        )

        // Trigger cloud backup
        try {
            val app = context.applicationContext as? FocusApplication
            app?.backupManager?.backupToCloudAsync(context)
        } catch (_: Exception) {}
    }

    /**
     * Unlocks the single 10-minute emergency break for the current Killswitch session.
     */
    fun startEmergencyBreak(context: Context): Boolean {
        if (!isKillswitchActive(context) || killswitchBreakUsed) {
            return false
        }

        val now = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
        val nowElapsed = SystemClock.elapsedRealtime()

        killswitchBreakUsed = true
        killswitchBreakEndTime = now + BREAK_DURATION_MS
        killswitchBreakEndElapsed = nowElapsed + BREAK_DURATION_MS

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_BREAK_USED, true)
            .putLong(KEY_BREAK_END_TIME, killswitchBreakEndTime)
            .putLong(KEY_BREAK_END_ELAPSED, killswitchBreakEndElapsed)
            .apply()

        _breakUsedFlow.value = true
        _isEmergencyBreakActiveFlow.value = true
        _breakEndTimeFlow.value = killswitchBreakEndTime

        // Schedule break expiration
        mainHandler.removeCallbacks(breakExpiredRunnable)
        mainHandler.postDelayed(breakExpiredRunnable, BREAK_DURATION_MS)

        // Schedule exact ELAPSED_REALTIME_WAKEUP AlarmManager fallback
        scheduleAlarmFallback(
            context,
            KillswitchReceiver.ACTION_BREAK_EXPIRED,
            killswitchBreakEndElapsed,
            REQ_CODE_BREAK
        )

        try {
            val app = context.applicationContext as? FocusApplication
            app?.backupManager?.backupToCloudAsync(context)
        } catch (_: Exception) {}

        return true
    }

    /**
     * Ends the emergency break early and immediately re-locks the phone.
     */
    fun endEmergencyBreakEarly(context: Context) {
        onBreakExpired(context)
    }

    fun onBreakExpired(context: Context? = null) {
        mainHandler.removeCallbacks(breakExpiredRunnable)
        killswitchBreakEndTime = 0L
        killswitchBreakEndElapsed = 0L
        _isEmergencyBreakActiveFlow.value = false
        _breakEndTimeFlow.value = 0L

        val ctx = context ?: try { FocusApplication.instance } catch (_: Exception) { null }
        if (ctx != null) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong(KEY_BREAK_END_TIME, 0L)
                    .putLong(KEY_BREAK_END_ELAPSED, 0L)
                    .apply()

                cancelAlarmFallback(ctx, KillswitchReceiver.ACTION_BREAK_EXPIRED, REQ_CODE_BREAK)

                // Immediately trigger active window check in AccessibilityService to eject outside apps
                FocusAccessibilityService.instance?.handleKillswitchBreakExpired()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBreakExpired", e)
            }
        }
    }

    fun onKillswitchExpired(context: Context? = null) {
        mainHandler.removeCallbacks(sessionExpiredRunnable)
        mainHandler.removeCallbacks(breakExpiredRunnable)

        val ctx = context ?: try { FocusApplication.instance } catch (_: Exception) { null }
        if (ctx != null) {
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                resetState(prefs)
                cancelAlarmFallback(ctx, KillswitchReceiver.ACTION_SESSION_EXPIRED, REQ_CODE_SESSION)
                cancelAlarmFallback(ctx, KillswitchReceiver.ACTION_BREAK_EXPIRED, REQ_CODE_BREAK)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onKillswitchExpired", e)
            }
        } else {
            killswitchEndTime = 0L
            killswitchEndElapsed = 0L
            killswitchBreakUsed = false
            killswitchBreakEndTime = 0L
            killswitchBreakEndElapsed = 0L
            _isKillswitchActiveFlow.value = false
            _isEmergencyBreakActiveFlow.value = false
            _killswitchEndTimeFlow.value = 0L
            _breakEndTimeFlow.value = 0L
            _breakUsedFlow.value = false
        }
    }

    /**
     * Checks if a package is allowed to run while Killswitch is engaged.
     * Allowed:
     * 1. Focus Lock itself
     * 2. System UI
     * 3. Default Home Launcher
     * 4. Phone / Dialer / Emergency calls
     */
    fun isAllowedKillswitchPackage(context: Context, pkgName: String?): Boolean {
        if (pkgName.isNullOrEmpty()) return true
        val appPkg = context.packageName
        if (pkgName == appPkg || pkgName == "com.aistudio.focuslock.fclk") return true
        if (pkgName == "com.android.systemui") return true

        if (isHomeLauncherPackage(context, pkgName)) return true
        if (isDialerOrEmergencyPackage(context, pkgName)) return true

        return false
    }

    private fun isHomeLauncherPackage(context: Context, pkgName: String): Boolean {
        if (cachedLauncherPackage == pkgName) return true
        if (pkgName.contains("launcher", ignoreCase = true) ||
            pkgName.contains("home", ignoreCase = true) ||
            pkgName == "com.google.android.apps.nexuslauncher" ||
            pkgName == "com.sec.android.app.launcher"
        ) {
            return true
        }
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            val launcherPkg = resolveInfo?.activityInfo?.packageName
            if (launcherPkg != null) {
                cachedLauncherPackage = launcherPkg
                if (launcherPkg == pkgName) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isDialerOrEmergencyPackage(context: Context, pkgName: String): Boolean {
        val dialerPackages = setOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.incallui",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.emergency",
            "com.samsung.android.dialer",
            "com.samsung.android.app.telephonyui"
        )
        if (dialerPackages.contains(pkgName)) return true
        if (pkgName.contains("dialer", ignoreCase = true) || pkgName.contains("incallui", ignoreCase = true)) {
            return true
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val defaultDialer = telecomManager?.defaultDialerPackage
                if (defaultDialer != null && defaultDialer == pkgName) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun scheduleAlarmFallback(
        context: Context,
        action: String,
        triggerAtElapsedRealtime: Long,
        requestCode: Int
    ) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, KillswitchReceiver::class.java).apply {
                this.action = action
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pi)
                }
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule alarm fallback for Killswitch action: $action", e)
        }
    }

    private fun cancelAlarmFallback(context: Context, action: String, requestCode: Int) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, KillswitchReceiver::class.java).apply {
                this.action = action
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
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
