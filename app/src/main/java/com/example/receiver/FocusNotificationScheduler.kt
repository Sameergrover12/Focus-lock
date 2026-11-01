package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object FocusNotificationScheduler {
    private const val TAG = "FocusNotifScheduler"
    const val REQUEST_CODE_9PM = 9021

    /**
     * Schedules the next 9:00 PM local-time check-in alarm.
     * If the current time is past 21:00 today, schedules for tomorrow at 21:00.
     */
    fun scheduleNext9PmAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val triggerTime = calendar.timeInMillis
        val intent = Intent(context, FocusNotificationReceiver::class.java).apply {
            action = FocusNotificationReceiver.ACTION_9PM_CHECKIN
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_9PM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "Scheduled next 9 PM alarm for: ${calendar.time}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied, falling back to setAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule fallback 9 PM alarm", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error scheduling 9 PM alarm", e)
        }
    }
}
