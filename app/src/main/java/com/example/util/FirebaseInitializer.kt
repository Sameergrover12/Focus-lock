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
        if (isInitialized && FirebaseApp.getApps(context).isNotEmpty()) {
            return true
        }

        synchronized(this) {
            return try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setProjectId("focus-lock-a0d19")
                        .setApplicationId("1:582909401822:android:f5c224c7be71cc455b804f")
                        .setApiKey("AIzaSyC0hlWXaudQ_ZWYvl0Rf187W9eRJYKClJM")
                        .setStorageBucket("focus-lock-a0d19.firebasestorage.app")
                        .setGcmSenderId("582909401822")
                        .build()
                    FirebaseApp.initializeApp(context.applicationContext, options)
                    Log.d(TAG, "Explicitly initialized FirebaseApp with options successfully")
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
