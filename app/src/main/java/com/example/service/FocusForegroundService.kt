package com.example.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.FocusApplication
import com.example.MainActivity
import com.example.R
import com.example.data.repository.FocusRepository
import com.example.util.ScreenTimeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null

    // In-memory active tracking state for today (idempotent accrual based on input focus)
    private val packageBaselineMinutesToday = mutableMapOf<String, Int>()
    private val packageLiveSecondsToday = mutableMapOf<String, Int>()
    private val packageLastWrittenMinutes = mutableMapOf<String, Int>()
    private val warnedPackagesToday = mutableSetOf<String>()
    private var lastRecordedDate = FocusRepository.getTodayDateString()

    // Real device screen-on accumulation (non-overlapping sanity reference)
    private var screenOnBaselineMinutes = 0
    private var screenOnLiveSeconds = 0
    private var screenOnLastWrittenMinutes = 0
    private var screenOnInitialized = false
    private var screenStateReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "FocusForegroundService"
        const val NOTIFICATION_ID = 1001
        const val WARNING_NOTIFICATION_ID_BASE = 2000

        @Volatile
        private var instance: FocusForegroundService? = null

        @Volatile
        private var activeForegroundPackage: String? = null

        fun onForegroundPackageChanged(packageName: String?) {
            activeForegroundPackage = packageName
        }

        fun updateBaselineForPackage(packageName: String, minutes: Int) {
            instance?.updatePackageBaseline(packageName, minutes)
        }

        fun startService(context: Context) {
            val intent = Intent(context, FocusForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FocusForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "FocusForegroundService created")
        startInForeground()

        val app = application as? FocusApplication
        app?.repository?.setServiceStartTime(System.currentTimeMillis())

        // Register screen state receiver for physical screen on/off events
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> Log.d(TAG, "Screen turned ON")
                    Intent.ACTION_SCREEN_OFF -> Log.d(TAG, "Screen turned OFF")
                }
            }
        }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            (application as? FocusApplication)?.repository?.resetDailyLimitsIfNeeded()
        }
        startUsageTrackingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            screenStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister screenStateReceiver", e)
        }
        trackingJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "FocusForegroundService destroyed")
    }

    fun updatePackageBaseline(packageName: String, minutes: Int) {
        val maxAllowed = ScreenTimeHelper.getMinutesSinceMidnight()
        val clamped = minutes.coerceIn(0, maxAllowed)
        packageBaselineMinutesToday[packageName] = clamped
        packageLiveSecondsToday[packageName] = 0
        packageLastWrittenMinutes[packageName] = clamped
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, FocusApplication.CHANNEL_ID_FOREGROUND)
            .setContentTitle("Focus Lock is Active")
            .setContentText("Enforcing focus rules and tracking screen time limits locally.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startUsageTrackingLoop() {
        trackingJob = serviceScope.launch {
            // Initial sync on service start to capture full-day usage prior to service launch
            try {
                val app = application as? FocusApplication
                app?.repository?.syncUsageStatsFromSystem(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Initial usage stats sync failed", e)
            }

            var loopCounter = 0
            while (isActive) {
                delay(5000) // Poll every 5 seconds
                loopCounter++
                checkMidnightReset()

                // Every 2 minutes (24 * 5s), reconcile full-day usage with UsageStatsManager
                if (loopCounter % 24 == 0) {
                    try {
                        val app = application as? FocusApplication
                        app?.repository?.syncUsageStatsFromSystem(applicationContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "Periodic usage stats sync failed", e)
                    }
                }

                // Check screen interactive state and unlocked state (do not log usage when screen is off or device is locked)
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isInteractive = powerManager?.isInteractive ?: false
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isInteractive || isLocked) {
                    continue
                }

                // Accumulate true device screen-on duration (non-overlapping real time)
                accumulateScreenOnTime(5)

                val currentPkg = detectCurrentForegroundPackage()
                if (currentPkg != null && currentPkg != packageName && !isSystemOverlay(currentPkg)) {
                    accumulateForegroundUsage(currentPkg, 5)
                }
            }
        }
    }

    private fun detectCurrentForegroundPackage(): String? {
        // If accessibility service is running, it is the primary live source for active window focus
        if (FocusAccessibilityService.isServiceRunning.value) {
            val fromAccessibility = FocusAccessibilityService.getActiveFocusedPackage() ?: activeForegroundPackage
            if (!fromAccessibility.isNullOrEmpty()) {
                return fromAccessibility
            }
        } else {
            // Also check activeForegroundPackage if set
            if (!activeForegroundPackage.isNullOrEmpty()) {
                return activeForegroundPackage
            }
        }

        // Fallback to UsageStatsManager if granted
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 10000
            val usageEvents = usageStatsManager?.queryEvents(beginTime, endTime)
            var lastPkg: String? = null
            if (usageEvents != null) {
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastPkg = event.packageName
                    }
                }
            }
            lastPkg
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun checkMidnightReset() {
        val today = FocusRepository.getTodayDateString()
        if (today != lastRecordedDate) {
            Log.d(TAG, "Midnight rollover detected: $lastRecordedDate -> $today. Resetting daily in-memory tracking maps.")
            lastRecordedDate = today
            warnedPackagesToday.clear()
            packageBaselineMinutesToday.clear()
            packageLiveSecondsToday.clear()
            packageLastWrittenMinutes.clear()
            screenOnBaselineMinutes = 0
            screenOnLiveSeconds = 0
            screenOnLastWrittenMinutes = 0
            screenOnInitialized = false

            val app = application as? FocusApplication ?: return
            val repo = app.repository
            repo.updateCurrentDate(today)
            repo.resetDailyTrackingState()
            repo.resetDailyLimitsIfNeeded()
        }
    }

    private suspend fun accumulateScreenOnTime(secondsToAdd: Int) {
        checkMidnightReset()
        val app = application as? FocusApplication ?: return
        val repo = app.repository

        if (!screenOnInitialized) {
            screenOnBaselineMinutes = repo.getDeviceScreenOnMinutesTodaySync()
            screenOnLiveSeconds = 0
            screenOnLastWrittenMinutes = screenOnBaselineMinutes
            screenOnInitialized = true
        }

        screenOnLiveSeconds += secondsToAdd
        val totalScreenOn = screenOnBaselineMinutes + (screenOnLiveSeconds / 60)

        if (totalScreenOn > screenOnLastWrittenMinutes) {
            repo.updateDeviceScreenOnTime(totalScreenOn)
            screenOnLastWrittenMinutes = totalScreenOn
        }
    }

    private val appMetadataCache = mutableMapOf<String, Pair<String, String?>>()

    private fun getAppMetadata(pkgName: String): Pair<String, String?> {
        val cached = appMetadataCache[pkgName]
        if (cached != null) return cached

        var name = pkgName
        var iconBase64: String? = null
        try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkgName, 0)
            name = pm.getApplicationLabel(info).toString()
            val drawable = pm.getApplicationIcon(info)
            val bitmap = drawableToBitmap(drawable)
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, stream)
            iconBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            // Uninstalled or system package
        }
        val result = Pair(name, iconBase64)
        appMetadataCache[pkgName] = result
        return result
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return android.graphics.Bitmap.createScaledBitmap(drawable.bitmap, 64, 64, true)
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(64) else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(64) else 64
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun isSystemOverlay(pkg: String): Boolean {
        return ScreenTimeHelper.isIgnoredSystemPackage(pkg, this)
    }

    private suspend fun accumulateForegroundUsage(pkgName: String, secondsToAdd: Int) {
        checkMidnightReset()
        if (pkgName == packageName || pkgName == applicationContext.packageName || isSystemOverlay(pkgName)) return

        val app = application as? FocusApplication ?: return
        val repo = app.repository
        val today = FocusRepository.getTodayDateString()

        // 1. Mark package as actively live-tracked
        repo.markPackageAsLiveTracked(pkgName)

        // 2. Initialize baseline from DB once for today if not yet cached in memory
        if (!packageBaselineMinutesToday.containsKey(pkgName)) {
            val existingLog = repo.getUsageLogSync(pkgName, today)
            val initialBaseline = existingLog?.minutesUsed ?: 0
            packageBaselineMinutesToday[pkgName] = initialBaseline
            packageLiveSecondsToday[pkgName] = 0
            packageLastWrittenMinutes[pkgName] = initialBaseline
        }

        // 3. Accumulate focused active seconds
        val currentLiveSec = (packageLiveSecondsToday[pkgName] ?: 0) + secondsToAdd
        packageLiveSecondsToday[pkgName] = currentLiveSec

        // 4. Calculate total minutes as of now (absolute, never additive on stored DB value)
        val baseline = packageBaselineMinutesToday[pkgName] ?: 0
        val accruedMinutes = currentLiveSec / 60
        val totalMinutes = baseline + accruedMinutes

        val lastWritten = packageLastWrittenMinutes[pkgName] ?: baseline
        if (totalMinutes > lastWritten) {
            val (appName, iconBase64) = getAppMetadata(pkgName)
            val finalPersistedMinutes = repo.setAppUsageAbsolute(
                packageName = pkgName,
                totalMinutes = totalMinutes,
                appName = appName,
                iconBase64 = iconBase64,
                date = today
            )
            packageLastWrittenMinutes[pkgName] = finalPersistedMinutes

            // 5. 90% warning notification based on single shared daily usage counter
            val limits = repo.getAllScreenTimeLimitsSync()
            val limit = limits.firstOrNull { it.packageName == pkgName }
            if (limit != null && limit.dailyLimitMinutes > 0) {
                if (finalPersistedMinutes >= (limit.dailyLimitMinutes * 0.9).toInt() &&
                    finalPersistedMinutes < limit.dailyLimitMinutes &&
                    !warnedPackagesToday.contains(pkgName)
                ) {
                    warnedPackagesToday.add(pkgName)
                    sendWarningNotification(pkgName, finalPersistedMinutes, limit.dailyLimitMinutes)
                }
            }

            // 6. Check and update Groups containing this app (sum of members' current daily usage)
            val groupApps = repo.getAllGroupAppsSync()
            val matchingGroupIds = groupApps.filter { it.packageName == pkgName }.map { it.groupId }.distinct()
            if (matchingGroupIds.isNotEmpty()) {
                val allGroups = repo.getAllGroupsSync()
                val todayLogs = repo.getUsageLogsForDateSync(today).associateBy { it.packageName }
                for (gId in matchingGroupIds) {
                    val group = allGroups.firstOrNull { it.id == gId }
                    if (group != null && group.budgetEnabled) {
                        val memberPkgs = groupApps.filter { it.groupId == gId }.map { it.packageName }
                        val totalGroupMinutes = memberPkgs.sumOf { memberPkg ->
                            if (memberPkg == pkgName) finalPersistedMinutes else todayLogs[memberPkg]?.minutesUsed ?: 0
                        }
                        repo.updateGroupUsage(gId, totalGroupMinutes, today)
                    }
                }
            }
        }
    }

    private fun sendWarningNotification(pkgName: String, usedMinutes: Int, limitMinutes: Int) {
        try {
            val pm = packageManager
            val appLabel = try {
                val info = pm.getApplicationInfo(pkgName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                pkgName
            }

            val remaining = (limitMinutes - usedMinutes).coerceAtLeast(1)
            val notification = NotificationCompat.Builder(this, FocusApplication.CHANNEL_ID_ALERTS)
                .setContentTitle("Screen Time Alert: $appLabel")
                .setContentText("You have used 90% of your daily allowance ($remaining min remaining).")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(this).notify(
                WARNING_NOTIFICATION_ID_BASE + pkgName.hashCode() % 1000,
                notification
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted for alert", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send warning notification", e)
        }
    }
}
