package com.example.util

object AppForegroundTracker {
    @Volatile
    var isFocusLockInForeground: Boolean = false
}
