package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.service.FocusForegroundService
import com.example.util.TimeIntegrityManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                TimeIntegrityManager.onBootCompleted(context)
                FocusForegroundService.startService(context)
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                TimeIntegrityManager.onTimeChanged()
            }
        }
    }
}
