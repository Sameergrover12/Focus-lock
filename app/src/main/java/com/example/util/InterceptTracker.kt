package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Central engine for tracking, persisting, and deduplicating enforcement intercepts
 * (app blocks, screen time limits, group rules, killswitch blocks, and website/keyword blocks).
 *
 * Implements a 3.5s cooldown per item to drop rapid repeat accessibility events,
 * persists daily counts in SharedPreferences, and provides reactive StateFlows for UI binding.
 */
object InterceptTracker {

    private const val PREFS_NAME = "intercept_tracker_prefs"
    private const val COOLDOWN_MS = 3500L

    // In-memory cooldown tracking (target item -> last logged elapsedRealtime)
    private val cooldownMap = ConcurrentHashMap<String, Long>()

    // Reactive StateFlows
    private val _todayTotalIntercepts = MutableStateFlow(0)
    val todayTotalIntercepts: StateFlow<Int> = _todayTotalIntercepts.asStateFlow()

    private val _todayAppIntercepts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val todayAppIntercepts: StateFlow<Map<String, Int>> = _todayAppIntercepts.asStateFlow()

    private val _todayKeywordWebIntercepts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val todayKeywordWebIntercepts: StateFlow<Map<String, Int>> = _todayKeywordWebIntercepts.asStateFlow()

    private val _sevenDayTotalIntercepts = MutableStateFlow(0)
    val sevenDayTotalIntercepts: StateFlow<Int> = _sevenDayTotalIntercepts.asStateFlow()

    @Volatile
    private var lastLoadedDate: String = ""

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayDateString(): String {
        return try {
            TimeIntegrityManager.getTrueDateString()
        } catch (e: Exception) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.format(Date())
        }
    }

    /**
     * Initializes or refreshes in-memory state from SharedPreferences.
     */
    @Synchronized
    fun init(context: Context) {
        val today = getTodayDateString()
        lastLoadedDate = today
        refreshFromPrefs(context, today)
    }

    private fun checkDateRollover(context: Context): String {
        val today = getTodayDateString()
        if (today != lastLoadedDate) {
            synchronized(this) {
                if (today != lastLoadedDate) {
                    lastLoadedDate = today
                    refreshFromPrefs(context, today)
                }
            }
        }
        return today
    }

    private fun refreshFromPrefs(context: Context, today: String) {
        val prefs = getPrefs(context)

        // 1. App intercepts
        val appKeys = prefs.getStringSet("app_keys_$today", emptySet()) ?: emptySet()
        val appMap = mutableMapOf<String, Int>()
        var appTotal = 0
        for (pkg in appKeys) {
            val count = prefs.getInt("app_${today}_$pkg", 0)
            if (count > 0) {
                appMap[pkg] = count
                appTotal += count
            }
        }

        // 2. Content intercepts (keywords & websites)
        val contentKeys = prefs.getStringSet("content_keys_$today", emptySet()) ?: emptySet()
        val contentMap = mutableMapOf<String, Int>()
        var contentTotal = 0
        for (label in contentKeys) {
            val count = prefs.getInt("content_${today}_$label", 0)
            if (count > 0) {
                contentMap[label] = count
                contentTotal += count
            }
        }

        // Stored or computed total
        val storedTotal = prefs.getInt("total_$today", appTotal + contentTotal)
        val finalTotal = maxOf(storedTotal, appTotal + contentTotal)

        _todayAppIntercepts.value = appMap
        _todayKeywordWebIntercepts.value = contentMap
        _todayTotalIntercepts.value = finalTotal

        // 3. 7-Day Total
        _sevenDayTotalIntercepts.value = computeSevenDayTotal(prefs, today)
    }

    private fun computeSevenDayTotal(prefs: SharedPreferences, today: String): Int {
        var sum = 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        try {
            val parsedDate = sdf.parse(today)
            if (parsedDate != null) {
                cal.time = parsedDate
            }
        } catch (e: Exception) {
            // fallback to current system date
        }

        for (i in 0..6) {
            val tempCal = cal.clone() as Calendar
            tempCal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(tempCal.time)
            sum += prefs.getInt("total_$dateStr", 0)
        }
        return sum
    }

    /**
     * Records an app intercept (blocked app, screen limit, group rule, killswitch).
     * Deduplicates occurrences within 3.5 seconds.
     */
    fun recordAppIntercept(context: Context, packageName: String) {
        if (packageName.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        val lastLogged = cooldownMap[packageName] ?: 0L
        if (now - lastLogged < COOLDOWN_MS) {
            return // Dropped by 3.5s cooldown
        }
        cooldownMap[packageName] = now

        val today = checkDateRollover(context)
        val prefs = getPrefs(context)

        synchronized(this) {
            val currentAppKeys = HashSet(prefs.getStringSet("app_keys_$today", emptySet()) ?: emptySet())
            currentAppKeys.add(packageName)

            val currentCount = prefs.getInt("app_${today}_$packageName", 0) + 1
            val currentTotal = prefs.getInt("total_$today", 0) + 1

            prefs.edit()
                .putStringSet("app_keys_$today", currentAppKeys)
                .putInt("app_${today}_$packageName", currentCount)
                .putInt("total_$today", currentTotal)
                .apply()

            // Update in-memory flows
            val updatedMap = _todayAppIntercepts.value.toMutableMap()
            updatedMap[packageName] = currentCount
            _todayAppIntercepts.value = updatedMap

            _todayTotalIntercepts.value = currentTotal
            _sevenDayTotalIntercepts.value = computeSevenDayTotal(prefs, today)
        }
    }

    /**
     * Records a website or keyword intercept.
     * Deduplicates occurrences within 3.5 seconds.
     */
    fun recordKeywordOrWebIntercept(context: Context, label: String) {
        if (label.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        val cdKey = "content_$label"
        val lastLogged = cooldownMap[cdKey] ?: 0L
        if (now - lastLogged < COOLDOWN_MS) {
            return // Dropped by 3.5s cooldown
        }
        cooldownMap[cdKey] = now

        val today = checkDateRollover(context)
        val prefs = getPrefs(context)

        synchronized(this) {
            val currentContentKeys = HashSet(prefs.getStringSet("content_keys_$today", emptySet()) ?: emptySet())
            currentContentKeys.add(label)

            val currentCount = prefs.getInt("content_${today}_$label", 0) + 1
            val currentTotal = prefs.getInt("total_$today", 0) + 1

            prefs.edit()
                .putStringSet("content_keys_$today", currentContentKeys)
                .putInt("content_${today}_$label", currentCount)
                .putInt("total_$today", currentTotal)
                .apply()

            // Update in-memory flows
            val updatedMap = _todayKeywordWebIntercepts.value.toMutableMap()
            updatedMap[label] = currentCount
            _todayKeywordWebIntercepts.value = updatedMap

            _todayTotalIntercepts.value = currentTotal
            _sevenDayTotalIntercepts.value = computeSevenDayTotal(prefs, today)
        }
    }

    /**
     * Helper to clear or reset test data if needed.
     */
    fun getTodayTotalCount(): Int = _todayTotalIntercepts.value
    fun getSevenDayTotal(): Int = _sevenDayTotalIntercepts.value
}
