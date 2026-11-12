package com.example.receiver

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.FocusApplication
import com.example.MainActivity
import com.example.R
import com.example.util.FocusNotificationMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FocusNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.d(TAG, "onReceive triggered with action: ${intent?.action}")

        // 1. Immediately schedule the next day's 9:00 PM alarm
        FocusNotificationScheduler.scheduleNext9PmAlarm(context)

        // 2. Prevent duplicate notifications on the same calendar date
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val memoryPrefs = context.getSharedPreferences(FocusNotificationMessages.PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedDate = memoryPrefs.getString("last_notified_date", null)
        if (lastNotifiedDate == todayStr) {
            Log.d(TAG, "Notification already delivered today ($todayStr), skipping duplicate")
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as? FocusApplication
                val repository = app?.repository

                val calendar = Calendar.getInstance()
                val isSunday = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

                // Fetch 30-day history and compute accurate 10-day average and past 7 days dwell
                val history = repository?.get30DayHistory(context) ?: emptyList()
                val last10Days = if (history.size >= 10) history.takeLast(10) else history
                val activeDays = last10Days.filter { it.minutes in 1 until (18 * 60) }
                val realAvgMinutes = if (activeDays.isNotEmpty()) {
                    activeDays.map { it.minutes }.average().toInt()
                } else {
                    120 // Graceful baseline if no history exists yet
                }

                val title: String
                val messageText: String

                if (isSunday) {
                    // Weekly recap notification ONLY on Sunday
                    title = "Focus Lock • Weekly Recap"

                    // Calculate past 7 days dwell (most recent 7 days ending on Today)
                    val past7Days = if (history.size >= 7) history.takeLast(7) else history
                    val past7DaysTotalDwell = past7Days.sumOf { it.minutes }

                    // Saved dwell calculation: sum of daily savings against baseline
                    val baselineWeeklyDwell = realAvgMinutes * 7
                    val rawSaved = baselineWeeklyDwell - past7DaysTotalDwell
                    val dailySavedSum = past7Days.sumOf { maxOf(0, realAvgMinutes - it.minutes) }
                    val finalSavedMinutes = if (dailySavedSum > 0) dailySavedSum else maxOf(30, rawSaved)

                    val savedFormatted = formatDuration(finalSavedMinutes)
                    val template = FocusNotificationMessages.getNextRandomMessage(
                        context,
                        "sunday_weekly",
                        FocusNotificationMessages.SUNDAY_WEEKLY_MESSAGES
                    )
                    messageText = template.replace("{saved}", savedFormatted)
                } else {
                    // Daily notification on Monday-Saturday comparing Today against 30-Day Daily Average
                    title = "Focus Lock • 9 PM Check-in"

                    val todayMinutes = history.lastOrNull()?.minutes
                        ?: (repository?.getDeviceScreenOnMinutesTodaySync() ?: 0)
                    val diffMinutes = Math.abs(todayMinutes - realAvgMinutes)

                    val todayFormatted = formatDuration(todayMinutes)
                    val diffFormatted = formatDuration(diffMinutes)

                    val template = if (todayMinutes <= realAvgMinutes) {
                        FocusNotificationMessages.getNextRandomMessage(
                            context,
                            "daily_lower",
                            FocusNotificationMessages.DAILY_LOWER_MESSAGES
                        )
                    } else {
                        FocusNotificationMessages.getNextRandomMessage(
                            context,
                            "daily_higher",
                            FocusNotificationMessages.DAILY_HIGHER_MESSAGES
                        )
                    }

                    messageText = template
                        .replace("{today}", todayFormatted)
                        .replace("{diff}", diffFormatted)
                }

                // Post the notification
                postNotification(context, title, messageText)

                // Mark today as notified
                memoryPrefs.edit().putString("last_notified_date", todayStr).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error generating 9 PM notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun formatDuration(totalMinutes: Int): String {
        val mins = maxOf(0, totalMinutes)
        val hours = mins / 60
        val remainderMinutes = mins % 60
        return when {
            hours > 0 && remainderMinutes > 0 -> "${hours}h ${remainderMinutes}m"
            hours > 0 -> "${hours}h"
            else -> "${remainderMinutes}m"
        }
    }

    private fun postNotification(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted, skipping notification")
                return
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FocusApplication.CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Posted notification ($title): $message")
    }

    companion object {
        private const val TAG = "FocusNotifReceiver"
        const val ACTION_9PM_CHECKIN = "com.example.action.FOCUS_9PM_CHECKIN"
        const val NOTIFICATION_ID = 9021
    }
}
