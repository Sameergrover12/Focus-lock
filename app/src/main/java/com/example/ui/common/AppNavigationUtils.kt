package com.example.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.MainActivity

fun restartMainActivityAfterRestore(context: Context) {
    runCatching {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        if (context is Activity) {
            context.finish()
        }
    }
}
