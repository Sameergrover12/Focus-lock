package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    @Volatile
    private var isInitialized = false

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        val hasDefaultApp = try {
            FirebaseApp.getInstance() != null
        } catch (_: IllegalStateException) {
            false
        }
        if (isInitialized && hasDefaultApp) {
            return true
        }

        synchronized(this) {
            return try {
                val defaultExists = try {
                    FirebaseApp.getInstance() != null
                } catch (_: IllegalStateException) {
                    false
                }
                if (!defaultExists) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:582909401822:android:f5c224c7be71cc455b804f")
                        .setApiKey("AIzaSyC0hlWXaudQ_ZWYvl0Rf187W9eRJYKClJM")
                        .setProjectId("focus-lock-a0d19")
                        .setStorageBucket("focus-lock-a0d19.firebasestorage.app")
                        .setGcmSenderId("582909401822")
                        .build()
                    FirebaseApp.initializeApp(appContext, options)
                    Log.d(TAG, "Explicitly initialized default FirebaseApp with FirebaseOptions")
                }
                isInitialized = true
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FirebaseApp: ${e.message}", e)
                false
            }
        }
    }
}
