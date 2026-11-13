package com.example.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.FocusApplication
import com.example.data.preferences.UserPreferencesRepository
import com.example.focusapp.util.MotivationLibrary
import com.example.ui.blocked.BlockedActivity
import com.example.ui.blocked.BlockedScreen
import com.example.ui.theme.BaseThemeMode
import com.example.ui.theme.FocusLockTheme
import com.example.ui.theme.SecondaryThemePalette
import com.example.util.EmergencyBreakManager

/**
 * OverlayManager manages drawing the full-screen intervention barrier via WindowManager
 * when SYSTEM_ALERT_WINDOW or AccessibilityService overlay is available.
 * Integrates the 4-second pattern interrupt, quote absorption, and auto-ejection to Home.
 */
object OverlayManager {
    private const val TAG = "OverlayManager"
    @Volatile
    private var overlayView: ComposeView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    }

    private var activeLifecycleOwner: OverlayLifecycleOwner? = null

    fun isShowingOverlay(): Boolean = overlayView != null

    fun showOverlay(
        context: Context,
        title: String,
        reason: String,
        type: String = BlockedActivity.TYPE_APP,
        quote: String = MotivationLibrary.getRandomFullScreenQuote(),
        nextWindow: String? = null,
        isAccessibilityOverlay: Boolean = false,
        cooldownEndTime: Long = 0L
    ) {
        val activeAccessibilityService = com.example.service.FocusAccessibilityService.instance
        val useAccessibilityOverlay = isAccessibilityOverlay ||
                context is android.accessibilityservice.AccessibilityService ||
                activeAccessibilityService != null

        if (!useAccessibilityOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            // Cannot draw overlay directly; launch BlockedActivity as fallback
            launchBlockedActivity(context, title, reason, type, quote, nextWindow, cooldownEndTime)
            return
        }

        val targetContext = if (context is android.accessibilityservice.AccessibilityService) {
            context
        } else {
            activeAccessibilityService ?: context
        }

        val attachOverlay = {
            try {
                // Remove any previous overlay synchronously BEFORE adding the new view
                removeOverlayImmediate(targetContext)

                val windowManager = targetContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (windowManager == null) {
                    launchBlockedActivity(context, title, reason, type, quote, nextWindow, cooldownEndTime)
                } else {
                    val layoutType = if (useAccessibilityOverlay) {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    }

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                        gravity = Gravity.CENTER
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }

                    val lifecycleOwner = OverlayLifecycleOwner()
                    activeLifecycleOwner = lifecycleOwner

                    val app = targetContext.applicationContext as? FocusApplication
                    val prefRepo = app?.preferencesRepository

                    // Read synchronously from SharedPreferences-backed caches (0ms, zero DataStore stall)
                    val breaks = EmergencyBreakManager.getBreaksRemaining(targetContext)
                    val passphrase = prefRepo?.getInitialCognitivePassphrase()
                        ?: UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE
                    val baseThemeMode = prefRepo?.getInitialBaseThemeMode() ?: BaseThemeMode.DARK
                    val secondaryPalette = prefRepo?.getInitialSecondaryPalette()
                        ?: SecondaryThemePalette.FOCUS_EMERALD

                    val composeView = ComposeView(targetContext).apply {
                        setViewTreeLifecycleOwner(lifecycleOwner)
                        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                        setContent {
                            FocusLockTheme(
                                baseThemeMode = baseThemeMode,
                                secondaryPalette = secondaryPalette
                            ) {
                                BlockedScreen(
                                    title = title,
                                    reason = reason,
                                    type = type,
                                    quote = quote,
                                    cooldownEndTime = cooldownEndTime,
                                    breaksRemaining = breaks,
                                    cognitivePassphrase = passphrase,
                                    nextWindow = nextWindow,
                                    onGoHome = {
                                        fireHomeIntent(targetContext)
                                        removeOverlayImmediate(targetContext)
                                    },
                                    onActivateEmergencyBreak = { phrase ->
                                        prefRepo?.triggerEmergencyBreak(phrase) ?: false
                                    },
                                    onEmergencyBreakActivated = {
                                        removeOverlayImmediate(targetContext)
                                    }
                                )
                            }
                        }
                    }

                    overlayView = composeView
                    windowManager.addView(composeView, params)
                    Log.d(TAG, "WindowManager overlay displayed synchronously at 0ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display WindowManager overlay; falling back to Activity", e)
                overlayView = null
                activeLifecycleOwner?.destroy()
                activeLifecycleOwner = null
                launchBlockedActivity(context, title, reason, type, quote, nextWindow, cooldownEndTime)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            attachOverlay()
        } else {
            mainHandler.post { attachOverlay() }
        }
    }

    private fun removeOverlayImmediate(context: Context? = null) {
        try {
            val view = overlayView
            if (view != null) {
                val wm = (context ?: view.context).getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeViewImmediate(view)
                overlayView = null
                activeLifecycleOwner?.destroy()
                activeLifecycleOwner = null
                Log.d(TAG, "WindowManager overlay removed")
            }
        } catch (e: Exception) {
            overlayView = null
            activeLifecycleOwner = null
            Log.w(TAG, "Error removing WindowManager overlay", e)
        }
    }

    fun removeOverlay(context: Context? = null) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeOverlayImmediate(context)
        } else {
            mainHandler.post { removeOverlayImmediate(context) }
        }
    }

    private fun fireHomeIntent(context: Context) {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire Home intent", e)
        }
    }

    private fun launchBlockedActivity(
        context: Context,
        title: String,
        reason: String,
        type: String,
        quote: String,
        nextWindow: String?,
        cooldownEndTime: Long = 0L
    ) {
        try {
            val intent = Intent(context, BlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(BlockedActivity.EXTRA_TITLE, title)
                putExtra(BlockedActivity.EXTRA_REASON, reason)
                putExtra(BlockedActivity.EXTRA_TYPE, type)
                putExtra(BlockedActivity.EXTRA_QUOTE, quote)
                putExtra(BlockedActivity.EXTRA_NEXT_WINDOW, nextWindow)
                if (cooldownEndTime > 0L) {
                    putExtra(BlockedActivity.EXTRA_COOLDOWN_END_TIME, cooldownEndTime)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity fallback", e)
        }
    }
}
