package com.example.data.local.converter

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromIntSet(set: Set<Int>?): String {
        return set?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntSet(data: String?): Set<Int> {
        if (data.isNullOrBlank()) return emptySet()
        return data.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }
}
