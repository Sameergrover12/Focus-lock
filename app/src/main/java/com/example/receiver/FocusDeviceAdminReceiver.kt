package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.focusapp.util.MotivationLibrary

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val line = MotivationLibrary.getRandomQuickFeedback()
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, line, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
        return "$line (Disabling admin turns off Invincible Mode)"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}

