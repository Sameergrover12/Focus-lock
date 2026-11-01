package com.example.data.model

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)
