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

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
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
