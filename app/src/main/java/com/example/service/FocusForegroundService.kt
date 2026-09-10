package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.FocusApplication
import com.example.MainActivity
import com.example.R
import com.example.data.repository.FocusRepository
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

    // Track active seconds per package for current minute chunk
    private val packageActiveSeconds = mutableMapOf<String, Int>()
    private val warnedPackagesToday = mutableSetOf<String>()
    private var lastRecordedDate = FocusRepository.getTodayDateString()

    companion object {
        private const val TAG = "FocusForegroundService"
        const val NOTIFICATION_ID = 1001
        const val WARNING_NOTIFICATION_ID_BASE = 2000

        @Volatile
        private var activeForegroundPackage: String? = null

        fun onForegroundPackageChanged(packageName: String?) {
            activeForegroundPackage = packageName
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
        Log.d(TAG, "FocusForegroundService created")
        startInForeground()
        startUsageTrackingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "FocusForegroundService destroyed")
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
            while (isActive) {
                delay(5000) // Poll every 5 seconds
                checkMidnightReset()
                val currentPkg = detectCurrentForegroundPackage()
                if (currentPkg != null && currentPkg != packageName) {
                    accumulateForegroundUsage(currentPkg, 5)
                }
            }
        }
    }

    private fun detectCurrentForegroundPackage(): String? {
        // First check accessibility service cached foreground package
        val fromAccessibility = activeForegroundPackage ?: FocusAccessibilityService.currentForegroundPackage
        if (!fromAccessibility.isNullOrEmpty()) {
            return fromAccessibility
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
            lastRecordedDate = today
            warnedPackagesToday.clear()
            packageActiveSeconds.clear()

            // Reset used minutes in DB for screen limits and groups
            val app = application as? FocusApplication ?: return
            val repo = app.repository

            val allLimits = repo.getAllScreenTimeLimitsSync()
            for (limit in allLimits) {
                repo.updateScreenTimeUsage(limit.packageName, 0, today)
            }

            val allGroups = repo.getAllGroupsSync()
            for (group in allGroups) {
                repo.updateGroupUsage(group.id, 0, today)
            }
        }
    }

    private suspend fun accumulateForegroundUsage(pkgName: String, secondsToAdd: Int) {
        val totalSec = (packageActiveSeconds[pkgName] ?: 0) + secondsToAdd
        if (totalSec >= 60) {
            val minutesToAdd = totalSec / 60
            packageActiveSeconds[pkgName] = totalSec % 60

            val app = application as? FocusApplication ?: return
            val repo = app.repository
            val today = FocusRepository.getTodayDateString()

            // 1. Update DailyUsageLog
            val currentLogs = repo.allBlockedApps // fetch today's usage
            repo.logAppUsage(pkgName, minutesToAdd, today)

            // 2. Check and update ScreenTimeLimit
            val limits = repo.getAllScreenTimeLimitsSync()
            val limit = limits.firstOrNull { it.packageName == pkgName }
            if (limit != null) {
                val newUsedMinutes = limit.usedMinutesToday + minutesToAdd
                repo.updateScreenTimeUsage(pkgName, newUsedMinutes, today)

                // 90% warning notification
                if (limit.dailyLimitMinutes > 0 &&
                    newUsedMinutes >= (limit.dailyLimitMinutes * 0.9).toInt() &&
                    newUsedMinutes < limit.dailyLimitMinutes &&
                    !warnedPackagesToday.contains(pkgName)
                ) {
                    warnedPackagesToday.add(pkgName)
                    sendWarningNotification(pkgName, newUsedMinutes, limit.dailyLimitMinutes)
                }
            }

            // 3. Check and update Groups containing this app
            val groupApps = repo.getAllGroupAppsSync()
            val matchingGroupIds = groupApps.filter { it.packageName == pkgName }.map { it.groupId }
            if (matchingGroupIds.isNotEmpty()) {
                val allGroups = repo.getAllGroupsSync()
                for (gId in matchingGroupIds) {
                    val group = allGroups.firstOrNull { it.id == gId }
                    if (group != null && group.budgetEnabled) {
                        val newGroupUsed = group.usedMinutesToday + minutesToAdd
                        repo.updateGroupUsage(gId, newGroupUsed, today)
                    }
                }
            }
        } else {
            packageActiveSeconds[pkgName] = totalSec
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
