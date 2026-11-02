package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.EmergencyBreakManager

class EmergencyBreakReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i("EmergencyBreakReceiver", "Received emergency break expiration broadcast")
        EmergencyBreakManager.onBreakExpired(context)
    }

    companion object {
        const val ACTION_BREAK_EXPIRED = "com.example.action.EMERGENCY_BREAK_EXPIRED"
    }
}
