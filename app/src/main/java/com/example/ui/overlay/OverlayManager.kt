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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * OverlayManager manages drawing the full-screen intervention barrier via WindowManager
 * when SYSTEM_ALERT_WINDOW permission is granted.
 * Integrates the 4-second pattern interrupt, quote absorption, and auto-ejection to Home.
 */
object OverlayManager {
    private const val TAG = "OverlayManager"
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

    fun showOverlay(
        context: Context,
        title: String,
        reason: String,
        type: String = BlockedActivity.TYPE_APP,
        quote: String = MotivationLibrary.getRandomFullScreenQuote(),
        nextWindow: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            // Cannot draw overlay directly; launch BlockedActivity as fallback
            launchBlockedActivity(context, title, reason, type, quote, nextWindow)
            return
        }

        mainHandler.post {
            try {
                removeOverlay(context)

                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                val lifecycleOwner = OverlayLifecycleOwner()
                activeLifecycleOwner = lifecycleOwner

                val app = context.applicationContext as? FocusApplication
                val prefRepo = app?.preferencesRepository

                val breaks = runBlocking {
                    try {
                        prefRepo?.emergencyBreaksRemaining?.first() ?: UserPreferencesRepository.MAX_WEEKLY_EMERGENCY_BREAKS
                    } catch (e: Exception) {
                        UserPreferencesRepository.MAX_WEEKLY_EMERGENCY_BREAKS
                    }
                }

                val passphrase = runBlocking {
                    try {
                        prefRepo?.cognitivePassphrase?.first() ?: UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE
                    } catch (e: Exception) {
                        UserPreferencesRepository.DEFAULT_COGNITIVE_PASSPHRASE
                    }
                }

                val composeView = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        BlockedScreen(
                            title = title,
                            reason = reason,
                            type = type,
                            quote = quote,
                            breaksRemaining = breaks,
                            cognitivePassphrase = passphrase,
                            nextWindow = nextWindow,
                            onGoHome = {
                                fireHomeIntent(context)
                                removeOverlay(context)
                            },
                            onActivateEmergencyBreak = { phrase ->
                                prefRepo?.triggerEmergencyBreak(phrase) ?: false
                            },
                            onEmergencyBreakActivated = {
                                removeOverlay(context)
                            }
                        )
                    }
                }

                overlayView = composeView
                windowManager.addView(composeView, params)
                Log.d(TAG, "WindowManager overlay displayed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display WindowManager overlay; falling back to Activity", e)
                launchBlockedActivity(context, title, reason, type, quote, nextWindow)
            }
        }
    }

    fun removeOverlay(context: Context? = null) {
        mainHandler.post {
            try {
                if (overlayView != null) {
                    val wm = (context ?: overlayView?.context)?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    wm?.removeViewImmediate(overlayView)
                    overlayView = null
                    activeLifecycleOwner?.destroy()
                    activeLifecycleOwner = null
                    Log.d(TAG, "WindowManager overlay removed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing WindowManager overlay", e)
            }
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
        nextWindow: String?
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
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity fallback", e)
        }
    }
}
