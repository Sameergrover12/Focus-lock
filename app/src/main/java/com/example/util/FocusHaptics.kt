package com.example.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Central 3-tier hardware haptic feedback utility for Focus Lock.
 * Provides safe fallback across Android versions and OEM ROMs.
 */
object FocusHaptics {

    /**
     * Tier 1: Light Mechanical Tick (5ms).
     * Used for tab switches, settling on pager pages, and navigation bar taps.
     */
    fun tick(view: View?) {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Throwable) {
            // Safe fallback if hardware haptics fail or disabled
        }
    }

    /**
     * Tier 2: Crisp Switch Snap.
     * Used for toggling switches, saving limit dialogs, creating groups,
     * and successfully adding blocked rules.
     */
    fun confirm(view: View?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    /**
     * Tier 3: Warning / Delete Pulse.
     * Used for duplicate attempts, empty input errors, removing rules/groups,
     * and activating emergency break.
     */
    fun rejectOrDelete(view: View?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Throwable) {
            // Safe fallback
        }
    }
}
