package com.aistudio.focuslock.fclk

import android.content.Context
import android.view.View

/**
 * Re-export of FocusHaptics in applicationId package com.aistudio.focuslock.fclk
 */
object FocusHaptics {
    fun tick(context: Context) = com.example.util.FocusHaptics.tick(context)
    fun tick(view: View?) = com.example.util.FocusHaptics.tick(view)

    fun confirm(context: Context) = com.example.util.FocusHaptics.confirm(context)
    fun confirm(view: View?) = com.example.util.FocusHaptics.confirm(view)

    fun rejectOrDelete(context: Context) = com.example.util.FocusHaptics.rejectOrDelete(context)
    fun rejectOrDelete(view: View?) = com.example.util.FocusHaptics.rejectOrDelete(view)
}
