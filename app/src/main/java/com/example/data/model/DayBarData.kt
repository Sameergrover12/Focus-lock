package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class DayBarData(
    val label: String,
    val dayNumber: String,
    val fullDate: String,
    val date: String,
    val minutes: Int,
    val isToday: Boolean
)
