package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.FocusDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FocusRepository

class FocusApplication : Application() {

    val database by lazy { FocusDatabase.getDatabase(this) }
    val repository by lazy { FocusRepository(database.focusDao()) }
    val preferencesRepository by lazy { UserPreferencesRepository(this) }
    val backupManager by lazy { com.example.data.backup.BackupManager(this, database, preferencesRepository) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.util.FirebaseInitializer.ensureInitialized(this)
        com.example.util.TimeIntegrityManager.init(this)
        backupManager
        createNotificationChannels()
        com.example.util.EmergencyBreakManager.init(this)
        com.example.util.KillswitchManager.init(this)
        com.example.util.InterceptTracker.init(this)
        com.example.receiver.FocusNotificationScheduler.scheduleNext9PmAlarm(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
                com.example.util.AppForegroundTracker.isFocusLockInForeground = true
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    com.example.util.AppForegroundTracker.isFocusLockInForeground = false
                }
            }

            override fun onActivityResumed(activity: android.app.Activity) {
                com.example.util.AppForegroundTracker.isFocusLockInForeground = true
            }

            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "Focus Lock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status of focus and screen time tracking"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Focus Lock Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when screen time limits or focus blocks are reached"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_FOREGROUND = "focus_lock_fg_channel"
        const val CHANNEL_ID_ALERTS = "focus_lock_alerts_channel"

        lateinit var instance: FocusApplication
            private set
    }
}
