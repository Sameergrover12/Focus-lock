package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "screen_time_limits")
data class ScreenTimeLimit(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int,
    val usedMinutesToday: Int = 0,
    val lastResetDate: String // YYYY-MM-DD
)

@Entity(tableName = "app_groups")
data class AppGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scheduleEnabled: Boolean = false,
    val scheduleStart: String = "09:00", // HH:mm
    val scheduleEnd: String = "17:00",   // HH:mm
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5), // Calendar days: 1=Sun, 2=Mon, etc.
    val budgetEnabled: Boolean = false,
    val dailyBudgetMinutes: Int = 60,
    val usedMinutesToday: Int = 0,
    val lastResetDate: String = "" // YYYY-MM-DD
)

@Entity(
    tableName = "group_apps",
    primaryKeys = ["groupId", "packageName"]
)
data class GroupApp(
    val groupId: Long,
    val packageName: String
)

@Entity(tableName = "blocked_websites")
data class BlockedWebsite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainOrUrl: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_keywords")
data class BlockedKeyword(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val caseSensitive: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_usage_logs",
    primaryKeys = ["packageName", "date"]
)
data class DailyUsageLog(
    val packageName: String,
    val date: String, // YYYY-MM-DD
    val minutesUsed: Int,
    val appName: String = "",
    val iconBase64: String? = null
)
