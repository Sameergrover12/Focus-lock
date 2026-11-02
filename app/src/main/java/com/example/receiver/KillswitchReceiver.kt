package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.KillswitchManager

class KillswitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        Log.i("KillswitchReceiver", "Broadcast received: ${intent.action}")
        when (intent.action) {
            ACTION_BREAK_EXPIRED -> {
                KillswitchManager.onBreakExpired(context)
            }
            ACTION_SESSION_EXPIRED -> {
                KillswitchManager.onKillswitchExpired(context)
            }
        }
    }

    companion object {
        const val ACTION_BREAK_EXPIRED = "com.example.action.KILLSWITCH_BREAK_EXPIRED"
        const val ACTION_SESSION_EXPIRED = "com.example.action.KILLSWITCH_SESSION_EXPIRED"
    }
}
