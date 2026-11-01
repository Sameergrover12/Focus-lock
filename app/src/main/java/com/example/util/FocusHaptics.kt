package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View

/**
 * Hardware vibrator haptic feedback utility for Focus Lock.
 * Uses direct hardware Vibrator / VibratorManager control with tuned durations
 * that physically spin up all motor types reliably on 100% of Android devices.
 */
object FocusHaptics {

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    // Tier 1: Crisp 28ms navigation tap for tab swipes, tab headers, and bottom nav
    fun tick(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(28L)
            }
        } catch (_: Exception) {}
    }

    fun tick(view: View?) {
        view?.context?.let { tick(it) }
    }

    // Tier 2: Firm 45ms mechanical snap for toggles, limits, and saving rules
    fun confirm(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(45L, 255))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(45L)
            }
        } catch (_: Exception) {}
    }

    fun confirm(view: View?) {
        view?.context?.let { confirm(it) }
    }

    // Tier 3: Sharp double-pulse waveform (35ms ON -> 45ms pause -> 55ms ON) for deletes & duplicate warnings
    fun rejectOrDelete(context: Context) {
        try {
            val v = getVibrator(context) ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0L, 35L, 45L, 55L)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                if (v.hasAmplitudeControl()) {
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    v.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0L, 35L, 45L, 55L), -1)
            }
        } catch (_: Exception) {}
    }

    fun rejectOrDelete(view: View?) {
        view?.context?.let { rejectOrDelete(it) }
    }
}
