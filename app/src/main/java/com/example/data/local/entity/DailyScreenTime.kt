package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_screen_time")
data class DailyScreenTime(
    @PrimaryKey
    val date: String,
    val screenOnMinutes: Int
)
