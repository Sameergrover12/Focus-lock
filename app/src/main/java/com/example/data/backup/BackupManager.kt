package com.example.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.data.local.FocusDatabase
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.data.local.entity.DailyUsageLog
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FocusRepository
import com.example.util.EmergencyBreakManager
import com.example.util.FirebaseInitializer
import com.example.util.FocusNotificationMessages
import com.example.util.InterceptTracker
import com.example.util.KillswitchManager
import com.example.util.TimeIntegrityManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class BackupManager(
    private val context: Context,
    private val database: FocusDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "BackupManager"
        const val BACKUP_VERSION = 2
        const val FIRESTORE_USERS_COLLECTION = "users"

        val ALL_PREF_FILES = listOf(
            UserPreferencesRepository.SYNC_PREFS_NAME,
            EmergencyBreakManager.PREFS_NAME,
            KillswitchManager.PREFS_NAME,
            "intercept_tracker_prefs",
            FocusNotificationMessages.PREFS_NAME,
            "focus_time_integrity_prefs",
            "historical_dwell_cache",
            "focus_lock_preferences"
        )

        fun isFirebaseAvailable(context: Context): Boolean {
            return FirebaseInitializer.ensureInitialized(context)
        }

        const val DEFAULT_WEB_CLIENT_ID = "582909401822-va6q6bhbubp23arcivuu7d2o2dt0ctoo.apps.googleusercontent.com"

        fun getDefaultWebClientId(context: Context): String {
            val dynamicId = try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else null
            } catch (e: Throwable) {
                null
            }
            return if (!dynamicId.isNullOrEmpty()) dynamicId else DEFAULT_WEB_CLIENT_ID
        }

        fun getSha1Fingerprint(context: Context): String {
            return try {
                val packageName = context.packageName
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = context.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = context.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                    )
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }

                val cert = signatures?.firstOrNull()?.toByteArray()
                    ?: return "Certificate not found"

                val md = MessageDigest.getInstance("SHA-1")
                val digest = md.digest(cert)
                digest.joinToString(":") { "%02X".format(it) }
            } catch (e: Exception) {
                "Error retrieving SHA-1: ${e.message}"
            }
        }
    }

    // Critical race-condition guard: blocks automatic uploads until initial cloud sync is checked & resolved
    val isInitialCloudSyncResolved = AtomicBoolean(false)

    // Critical guard: prevents fresh installs or restore operations from reverse overwriting cloud backup
    val isRestoringFromCloud = AtomicBoolean(false)

    // Global reactive restore version: incremented ONLY when external Cloud or JSON restore finishes
    private val _restoreVersion = MutableStateFlow(0)
    val restoreVersion: StateFlow<Int> = _restoreVersion.asStateFlow()

    // 1500ms Debounce scope on Dispatchers.IO for real-time automatic cloud sync
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoBackupJob: Job? = null

    // Strong references to preference change listeners to prevent premature garbage collection
    private val prefListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        registerPreferenceChangeListeners()
    }

    private fun registerPreferenceChangeListeners() {
        for (prefName in ALL_PREF_FILES) {
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "onboarding_complete" || key == "is_onboarding_completed" || key == "last_cloud_backup_time") {
                    return@OnSharedPreferenceChangeListener
                }
                if (isInitialCloudSyncResolved.get() && !isRestoringFromCloud.get()) {
                    scheduleDebouncedAutoBackup(1500L)
                }
            }
            prefListeners.add(listener)
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * Resolves initial cloud sync on startup:
     * If an account was previously connected, checks server first and restores to local SharedPreferences,
     * then sets isInitialCloudSyncResolved = true.
     */
    fun resolveInitialCloudSyncOnStartup() {
        backupScope.launch(Dispatchers.IO) {
            val email = preferencesRepository.getConnectedGmailAccountSync()
                ?: try { preferencesRepository.connectedGmailAccount.first() } catch (_: Exception) { null }

            if (!email.isNullOrEmpty()) {
                Log.d(TAG, "Resolving initial cloud sync on startup for $email...")
                restoreFromCloud(email, isInitialStartup = true)
            } else {
                isInitialCloudSyncResolved.set(true)
            }
        }
    }

    /**
     * Schedules an auto-backup to Firestore with a 1500ms debounce on Dispatchers.IO.
     * Blocks if initial sync has not resolved or if restore is in progress.
     */
    fun backupToCloudAsync(context: Context? = null) {
        scheduleDebouncedAutoBackup(1500L)
    }

    fun scheduleAutoBackup(context: Context? = null) {
        scheduleDebouncedAutoBackup(1500L)
    }

    fun scheduleDebouncedAutoBackup(delayMs: Long = 1500L) {
        if (!isInitialCloudSyncResolved.get() || isRestoringFromCloud.get()) {
            return
        }

        autoBackupJob?.cancel()
        autoBackupJob = backupScope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (!isInitialCloudSyncResolved.get() || isRestoringFromCloud.get()) return@launch

            val targetContext = this@BackupManager.context
            FirebaseInitializer.ensureInitialized(targetContext)

            val email = preferencesRepository.getConnectedGmailAccountSync()
                ?: try { preferencesRepository.connectedGmailAccount.first() } catch (_: Exception) { null }
            if (email.isNullOrEmpty()) return@launch

            try {
                syncToCloudDirect(email, isManual = false)
            } catch (e: Exception) {
                Log.w(TAG, "Silent auto-backup error: ${e.message}")
            }
        }
    }

    /**
     * Collects every single entry (prefs.all) from ALL SharedPreferences files used by the app,
     * serializing each value alongside its exact type ("BOOLEAN", "INT", "LONG", "FLOAT", "STRING", "STRING_SET").
     */
    fun collectAllSharedPreferencesMap(): Map<String, Map<String, Any>> {
        val rootMap = mutableMapOf<String, Map<String, Any>>()
        for (fileName in ALL_PREF_FILES) {
            val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            val fileMap = mutableMapOf<String, Any>()
            for ((key, value) in allEntries) {
                if (key == "onboarding_complete" || key == "is_onboarding_completed" || key == "last_cloud_backup_time") continue
                val entryMap: Map<String, Any>? = when (value) {
                    is Boolean -> mapOf("type" to "BOOLEAN", "value" to value)
                    is Int -> mapOf("type" to "INT", "value" to value)
                    is Long -> mapOf("type" to "LONG", "value" to value)
                    is Float -> mapOf("type" to "FLOAT", "value" to value.toDouble())
                    is String -> mapOf("type" to "STRING", "value" to value)
                    is Set<*> -> mapOf("type" to "STRING_SET", "value" to value.mapNotNull { it?.toString() })
                    else -> null
                }
                if (entryMap != null) {
                    fileMap[key] = entryMap
                }
            }
            if (fileMap.isNotEmpty()) {
                rootMap[fileName] = fileMap
            }
        }
        return rootMap
    }

    /**
     * Writes 100% of all typed SharedPreferences entries back into their respective SharedPreferences files
     * using .commit() so writes are immediately synchronous and on disk before in-memory reload.
     */
    fun restoreFromSharedPreferencesMap(map: Map<String, Any?>) {
        for ((fileName, fileData) in map) {
            val entries = fileData as? Map<*, *> ?: continue
            val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            for ((kObj, vObj) in entries) {
                val key = kObj?.toString() ?: continue
                if (key == "onboarding_complete" || key == "is_onboarding_completed" || key == "last_cloud_backup_time") continue
                val entry = vObj as? Map<*, *> ?: continue
                val type = entry["type"]?.toString() ?: continue
                val value = entry["value"]
                when (type) {
                    "BOOLEAN" -> (value as? Boolean)?.let { editor.putBoolean(key, it) }
                    "INT" -> (value as? Number)?.let { editor.putInt(key, it.toInt()) }
                    "LONG" -> (value as? Number)?.let { editor.putLong(key, it.toLong()) }
                    "FLOAT" -> (value as? Number)?.let { editor.putFloat(key, it.toFloat()) }
                    "STRING" -> editor.putString(key, value?.toString())
                    "STRING_SET" -> {
                        val list = (value as? List<*>)?.mapNotNull { it?.toString() }?.toSet()
                        if (list != null) editor.putStringSet(key, list)
                    }
                }
            }
            editor.commit()
        }
    }

    /**
     * Collects all entries from SharedPreferences into a dynamic JSON object for backwards compatibility.
     */
    fun collectAllPrefsJson(): String {
        val root = JSONObject()
        val syncPrefs = context.getSharedPreferences(UserPreferencesRepository.SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = syncPrefs.all
        for ((key, value) in allEntries) {
            if (key == "onboarding_complete" || key == "is_onboarding_completed" || key == "last_cloud_backup_time") continue
            when (value) {
                is Boolean -> root.put(key, value)
                is Int -> root.put(key, value)
                is Long -> root.put(key, value)
                is Float -> root.put(key, value.toDouble())
                is String -> root.put(key, value)
                is Set<*> -> {
                    val arr = JSONArray()
                    for (item in value) {
                        arr.put(item.toString())
                    }
                    root.put(key, arr)
                }
            }
        }
        return root.toString()
    }

    /**
     * Restores all entries from a preferences JSON string into SharedPreferences via .commit().
     */
    suspend fun restoreFromPrefsJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val syncPrefs = context.getSharedPreferences(UserPreferencesRepository.SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = syncPrefs.edit()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "onboarding_complete" || key == "is_onboarding_completed" || key == "last_cloud_backup_time") continue
                val value = root.get(key)
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                    is JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0 until value.length()) {
                            set.add(value.getString(i))
                        }
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.commit()
            preferencesRepository.restorePreferencesSync(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from prefs_json", e)
        }
    }

    /**
     * Builds a comprehensive map of all app entities (Room database + SharedPreferences)
     * stored under field "focus_lock_data".
     */
    suspend fun collectFullStateMap(sharedPrefsMap: Map<String, Map<String, Any>>): Map<String, Any> = withContext(Dispatchers.IO) {
        val dao = database.focusDao()
        val fullMap = mutableMapOf<String, Any>()
        val todayDate = FocusRepository.getTodayDateString()

        fullMap["shared_preferences"] = sharedPrefsMap

        // 1. Blocked Apps
        val blockedAppsList = dao.getAllBlockedAppsSync().map { app ->
            mapOf(
                "packageName" to app.packageName,
                "appName" to app.appName,
                "dateAdded" to app.dateAdded
            )
        }
        fullMap["blockedApps"] = blockedAppsList

        // 2. Screen Time Limits
        val limitsList = dao.getAllScreenTimeLimitsSync().map { limit ->
            mapOf(
                "packageName" to limit.packageName,
                "dailyLimitMinutes" to limit.dailyLimitMinutes,
                "sessionLimitSeconds" to limit.sessionLimitSeconds,
                "cooldownSeconds" to limit.cooldownSeconds,
                "usedMinutesToday" to limit.usedMinutesToday,
                "lastResetDate" to limit.lastResetDate,
                "currentSessionUsedMs" to limit.currentSessionUsedMs,
                "lastSessionExitTimestamp" to limit.lastSessionExitTimestamp,
                "cooldownEndTimestamp" to limit.cooldownEndTimestamp
            )
        }
        fullMap["screenTimeLimits"] = limitsList

        // 3. App Groups
        val groupsList = dao.getAllGroupsSync().map { group ->
            mapOf(
                "id" to group.id,
                "name" to group.name,
                "scheduleEnabled" to group.scheduleEnabled,
                "scheduleStart" to group.scheduleStart,
                "scheduleEnd" to group.scheduleEnd,
                "daysOfWeek" to group.daysOfWeek.toList(),
                "budgetEnabled" to group.budgetEnabled,
                "dailyBudgetMinutes" to group.dailyBudgetMinutes,
                "sessionLimitEnabled" to group.sessionLimitEnabled,
                "sessionLimitSeconds" to group.sessionLimitSeconds,
                "cooldownSeconds" to group.cooldownSeconds,
                "usedMinutesToday" to group.usedMinutesToday,
                "lastResetDate" to group.lastResetDate
            )
        }
        fullMap["appGroups"] = groupsList

        // 4. Group Apps
        val groupAppsList = dao.getAllGroupAppsSync().map { ga ->
            mapOf(
                "groupId" to ga.groupId,
                "packageName" to ga.packageName
            )
        }
        fullMap["groupApps"] = groupAppsList

        // 5. Blocked Websites
        val websitesList = dao.getAllBlockedWebsitesSync()
            .distinctBy { com.example.util.WebRuleHelper.normalizeDomain(it.domainOrUrl).lowercase() }
            .map { site ->
                mapOf(
                    "domainOrUrl" to com.example.util.WebRuleHelper.normalizeDomain(site.domainOrUrl),
                    "dateAdded" to site.dateAdded
                )
            }
        fullMap["blockedWebsites"] = websitesList

        // 6. Blocked Keywords
        val keywordsList = dao.getAllBlockedKeywordsSync()
            .distinctBy { com.example.util.WebRuleHelper.normalizeKeyword(it.keyword).lowercase() }
            .map { kw ->
                mapOf(
                    "keyword" to com.example.util.WebRuleHelper.normalizeKeyword(kw.keyword),
                    "caseSensitive" to kw.caseSensitive,
                    "dateAdded" to kw.dateAdded
                )
            }
        fullMap["blockedKeywords"] = keywordsList

        // 7. Today Usage Logs
        val todayLogs = dao.getUsageLogsForDateSync(todayDate).map { log ->
            mapOf(
                "packageName" to log.packageName,
                "appName" to log.appName,
                "minutesUsed" to log.minutesUsed,
                "iconBase64" to (log.iconBase64 ?: ""),
                "date" to log.date
            )
        }
        fullMap["usageLogs"] = todayLogs

        return@withContext fullMap
    }

    /**
     * Restores all app state from the structured focus_lock_data map.
     */
    suspend fun restoreFromFullStateMap(data: Map<String, Any?>): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val dao = database.focusDao()
            val todayDate = FocusRepository.getTodayDateString()

            // 1. Restore SharedPreferences
            @Suppress("UNCHECKED_CAST")
            val spMap = data["shared_preferences"] as? Map<String, Any?>
            if (spMap != null) {
                restoreFromSharedPreferencesMap(spMap)
            }

            // 2. Blocked Apps
            val blockedApps = data["blockedApps"] as? List<*>
            if (blockedApps != null) {
                for (item in blockedApps) {
                    val map = item as? Map<*, *> ?: continue
                    val pkg = map["packageName"]?.toString() ?: continue
                    val app = BlockedApp(
                        packageName = pkg,
                        appName = map["appName"]?.toString() ?: "App",
                        dateAdded = (map["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                    dao.insertBlockedApp(app)
                }
            }

            // 3. Screen Time Limits
            val limits = data["screenTimeLimits"] as? List<*>
            if (limits != null) {
                for (item in limits) {
                    val map = item as? Map<*, *> ?: continue
                    val pkg = map["packageName"]?.toString() ?: continue
                    val limit = ScreenTimeLimit(
                        packageName = pkg,
                        dailyLimitMinutes = (map["dailyLimitMinutes"] as? Number)?.toInt() ?: 0,
                        sessionLimitSeconds = (map["sessionLimitSeconds"] as? Number)?.toInt() ?: 0,
                        cooldownSeconds = (map["cooldownSeconds"] as? Number)?.toInt() ?: 0,
                        usedMinutesToday = (map["usedMinutesToday"] as? Number)?.toInt() ?: 0,
                        lastResetDate = map["lastResetDate"]?.toString() ?: todayDate,
                        currentSessionUsedMs = (map["currentSessionUsedMs"] as? Number)?.toLong() ?: 0L,
                        lastSessionExitTimestamp = (map["lastSessionExitTimestamp"] as? Number)?.toLong() ?: 0L,
                        cooldownEndTimestamp = (map["cooldownEndTimestamp"] as? Number)?.toLong() ?: 0L
                    )
                    dao.insertOrUpdateScreenTimeLimit(limit)
                }
            }

            // 4. App Groups
            val groups = data["appGroups"] as? List<*>
            if (groups != null) {
                for (item in groups) {
                    val map = item as? Map<*, *> ?: continue
                    val id = (map["id"] as? Number)?.toLong() ?: 0L
                    val daysList = (map["daysOfWeek"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                        ?: setOf(1, 2, 3, 4, 5)
                    val group = AppGroup(
                        id = id,
                        name = map["name"]?.toString() ?: "Group",
                        scheduleEnabled = map["scheduleEnabled"] as? Boolean ?: false,
                        scheduleStart = map["scheduleStart"]?.toString() ?: "09:00",
                        scheduleEnd = map["scheduleEnd"]?.toString() ?: "17:00",
                        daysOfWeek = daysList,
                        budgetEnabled = map["budgetEnabled"] as? Boolean ?: false,
                        dailyBudgetMinutes = (map["dailyBudgetMinutes"] as? Number)?.toInt() ?: 60,
                        sessionLimitEnabled = map["sessionLimitEnabled"] as? Boolean ?: false,
                        sessionLimitSeconds = (map["sessionLimitSeconds"] as? Number)?.toInt() ?: 0,
                        cooldownSeconds = (map["cooldownSeconds"] as? Number)?.toInt() ?: 0,
                        usedMinutesToday = (map["usedMinutesToday"] as? Number)?.toInt() ?: 0,
                        lastResetDate = map["lastResetDate"]?.toString() ?: todayDate
                    )
                    dao.insertGroup(group)
                }
            }

            // 5. Group Apps
            val groupApps = data["groupApps"] as? List<*>
            if (groupApps != null) {
                val gaList = mutableListOf<GroupApp>()
                for (item in groupApps) {
                    val map = item as? Map<*, *> ?: continue
                    val gId = (map["groupId"] as? Number)?.toLong() ?: continue
                    val pkg = map["packageName"]?.toString() ?: continue
                    gaList.add(GroupApp(groupId = gId, packageName = pkg))
                }
                if (gaList.isNotEmpty()) {
                    dao.insertGroupApps(gaList)
                }
            }

            // 6. Blocked Websites
            val websites = data["blockedWebsites"] as? List<*>
            if (websites != null) {
                val existing = dao.getAllBlockedWebsitesSync().map { com.example.util.WebRuleHelper.normalizeDomain(it.domainOrUrl).lowercase() }.toMutableSet()
                for (item in websites) {
                    val map = item as? Map<*, *> ?: continue
                    val raw = map["domainOrUrl"]?.toString() ?: continue
                    val norm = com.example.util.WebRuleHelper.normalizeDomain(raw)
                    if (norm.isNotBlank() && existing.add(norm.lowercase())) {
                        dao.insertBlockedWebsite(
                            BlockedWebsite(
                                domainOrUrl = norm,
                                dateAdded = (map["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            // 7. Blocked Keywords
            val keywords = data["blockedKeywords"] as? List<*>
            if (keywords != null) {
                val existing = dao.getAllBlockedKeywordsSync().map { com.example.util.WebRuleHelper.normalizeKeyword(it.keyword).lowercase() }.toMutableSet()
                for (item in keywords) {
                    val map = item as? Map<*, *> ?: continue
                    val raw = map["keyword"]?.toString() ?: continue
                    val norm = com.example.util.WebRuleHelper.normalizeKeyword(raw)
                    val caseSens = map["caseSensitive"] as? Boolean ?: false
                    if (norm.isNotBlank() && existing.add(norm.lowercase())) {
                        dao.insertBlockedKeyword(
                            BlockedKeyword(
                                keyword = norm,
                                caseSensitive = caseSens,
                                dateAdded = (map["dateAdded"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            // 8. Usage Logs
            val usageLogs = data["usageLogs"] as? List<*>
            if (usageLogs != null) {
                for (item in usageLogs) {
                    val map = item as? Map<*, *> ?: continue
                    val pkg = map["packageName"]?.toString() ?: continue
                    val log = DailyUsageLog(
                        packageName = pkg,
                        appName = map["appName"]?.toString() ?: "App",
                        minutesUsed = (map["minutesUsed"] as? Number)?.toInt() ?: 0,
                        iconBase64 = map["iconBase64"]?.toString()?.takeIf { it.isNotBlank() },
                        date = map["date"]?.toString() ?: todayDate
                    )
                    dao.insertOrUpdateDailyUsageLog(log)
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from focus_lock_data map", e)
            false
        }
    }

    /**
     * Serializes all user configuration rules into standard JSON string (for export and fallback).
     */
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        try {
            root.put("version", BACKUP_VERSION)
            root.put("timestamp", System.currentTimeMillis())

            val dao = database.focusDao()

            val blockedAppsArray = JSONArray()
            dao.getAllBlockedAppsSync().forEach { app ->
                val obj = JSONObject()
                obj.put("packageName", app.packageName)
                obj.put("appName", app.appName)
                obj.put("dateAdded", app.dateAdded)
                blockedAppsArray.put(obj)
            }
            root.put("blockedApps", blockedAppsArray)

            val limitsArray = JSONArray()
            dao.getAllScreenTimeLimitsSync().forEach { limit ->
                val obj = JSONObject()
                obj.put("packageName", limit.packageName)
                obj.put("dailyLimitMinutes", limit.dailyLimitMinutes)
                obj.put("sessionLimitSeconds", limit.sessionLimitSeconds)
                obj.put("cooldownSeconds", limit.cooldownSeconds)
                obj.put("usedMinutesToday", limit.usedMinutesToday)
                obj.put("lastResetDate", limit.lastResetDate)
                limitsArray.put(obj)
            }
            root.put("screenTimeLimits", limitsArray)

            val groupsArray = JSONArray()
            dao.getAllGroupsSync().forEach { group ->
                val obj = JSONObject()
                obj.put("id", group.id)
                obj.put("name", group.name)
                obj.put("scheduleEnabled", group.scheduleEnabled)
                obj.put("scheduleStart", group.scheduleStart)
                obj.put("scheduleEnd", group.scheduleEnd)
                val daysArray = JSONArray()
                group.daysOfWeek.forEach { daysArray.put(it) }
                obj.put("daysOfWeek", daysArray)
                obj.put("budgetEnabled", group.budgetEnabled)
                obj.put("dailyBudgetMinutes", group.dailyBudgetMinutes)
                obj.put("sessionLimitEnabled", group.sessionLimitEnabled)
                obj.put("sessionLimitSeconds", group.sessionLimitSeconds)
                obj.put("cooldownSeconds", group.cooldownSeconds)
                groupsArray.put(obj)
            }
            root.put("appGroups", groupsArray)

            val groupAppsArray = JSONArray()
            dao.getAllGroupAppsSync().forEach { ga ->
                val obj = JSONObject()
                obj.put("groupId", ga.groupId)
                obj.put("packageName", ga.packageName)
                groupAppsArray.put(obj)
            }
            root.put("groupApps", groupAppsArray)

            val websitesArray = JSONArray()
            dao.getAllBlockedWebsitesSync()
                .distinctBy { com.example.util.WebRuleHelper.normalizeDomain(it.domainOrUrl).lowercase() }
                .forEach { site ->
                    val obj = JSONObject()
                    obj.put("domainOrUrl", com.example.util.WebRuleHelper.normalizeDomain(site.domainOrUrl))
                    obj.put("dateAdded", site.dateAdded)
                    websitesArray.put(obj)
                }
            root.put("blockedWebsites", websitesArray)

            val keywordsArray = JSONArray()
            dao.getAllBlockedKeywordsSync()
                .distinctBy { com.example.util.WebRuleHelper.normalizeKeyword(it.keyword).lowercase() }
                .forEach { kw ->
                    val obj = JSONObject()
                    obj.put("keyword", com.example.util.WebRuleHelper.normalizeKeyword(kw.keyword))
                    obj.put("caseSensitive", kw.caseSensitive)
                    obj.put("dateAdded", kw.dateAdded)
                    keywordsArray.put(obj)
                }
            root.put("blockedKeywords", keywordsArray)

            val prefsObj = JSONObject()
            preferencesRepository.getPreferencesSnapshot().forEach { (k, v) ->
                if (v != null) {
                    prefsObj.put(k, v)
                }
            }
            root.put("preferences", prefsObj)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating backup JSON", e)
        }

        return@withContext root.toString(2)
    }

    suspend fun restoreFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val root = JSONObject(jsonString)
            val dao = database.focusDao()
            val todayDate = FocusRepository.getTodayDateString()

            if (root.has("blockedApps")) {
                val array = root.getJSONArray("blockedApps")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val app = BlockedApp(
                        packageName = obj.getString("packageName"),
                        appName = obj.optString("appName", "App"),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                    )
                    dao.insertBlockedApp(app)
                }
            }

            if (root.has("screenTimeLimits")) {
                val array = root.getJSONArray("screenTimeLimits")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val pkg = obj.getString("packageName")
                    val existing = dao.getScreenTimeLimit(pkg)
                    val limit = ScreenTimeLimit(
                        packageName = pkg,
                        dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 0),
                        sessionLimitSeconds = obj.optInt("sessionLimitSeconds", 0),
                        cooldownSeconds = obj.optInt("cooldownSeconds", 0),
                        usedMinutesToday = existing?.usedMinutesToday ?: 0,
                        lastResetDate = existing?.lastResetDate ?: todayDate,
                        currentSessionUsedMs = existing?.currentSessionUsedMs ?: 0L,
                        lastSessionExitTimestamp = existing?.lastSessionExitTimestamp ?: 0L,
                        cooldownEndTimestamp = existing?.cooldownEndTimestamp ?: 0L
                    )
                    dao.insertOrUpdateScreenTimeLimit(limit)
                }
            }

            if (root.has("appGroups")) {
                val array = root.getJSONArray("appGroups")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optLong("id", 0L)
                    val existing = if (id > 0) dao.getGroupById(id) else null

                    val daysSet = mutableSetOf<Int>()
                    val daysArray = obj.optJSONArray("daysOfWeek")
                    if (daysArray != null) {
                        for (d in 0 until daysArray.length()) {
                            daysSet.add(daysArray.getInt(d))
                        }
                    } else {
                        daysSet.addAll(setOf(1, 2, 3, 4, 5))
                    }

                    val group = AppGroup(
                        id = id,
                        name = obj.getString("name"),
                        scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
                        scheduleStart = obj.optString("scheduleStart", "09:00"),
                        scheduleEnd = obj.optString("scheduleEnd", "17:00"),
                        daysOfWeek = daysSet,
                        budgetEnabled = obj.optBoolean("budgetEnabled", false),
                        dailyBudgetMinutes = obj.optInt("dailyBudgetMinutes", 60),
                        sessionLimitEnabled = obj.optBoolean("sessionLimitEnabled", false),
                        sessionLimitSeconds = obj.optInt("sessionLimitSeconds", 0),
                        cooldownSeconds = obj.optInt("cooldownSeconds", 0),
                        usedMinutesToday = existing?.usedMinutesToday ?: 0,
                        lastResetDate = existing?.lastResetDate ?: todayDate
                    )
                    dao.insertGroup(group)
                }
            }

            if (root.has("groupApps")) {
                val array = root.getJSONArray("groupApps")
                val groupAppsList = mutableListOf<GroupApp>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    groupAppsList.add(
                        GroupApp(
                            groupId = obj.getLong("groupId"),
                            packageName = obj.getString("packageName")
                        )
                    )
                }
                if (groupAppsList.isNotEmpty()) {
                    dao.insertGroupApps(groupAppsList)
                }
            }

            if (root.has("blockedWebsites")) {
                val array = root.getJSONArray("blockedWebsites")
                val existing = dao.getAllBlockedWebsitesSync()
                val existingNormalized = existing.map { com.example.util.WebRuleHelper.normalizeDomain(it.domainOrUrl).lowercase() }.toMutableSet()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawDomain = obj.getString("domainOrUrl")
                    val normalized = com.example.util.WebRuleHelper.normalizeDomain(rawDomain)
                    if (normalized.isNotBlank() && existingNormalized.add(normalized.lowercase())) {
                        dao.insertBlockedWebsite(
                            BlockedWebsite(
                                domainOrUrl = normalized,
                                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            if (root.has("blockedKeywords")) {
                val array = root.getJSONArray("blockedKeywords")
                val existing = dao.getAllBlockedKeywordsSync()
                val existingNormalized = existing.map { com.example.util.WebRuleHelper.normalizeKeyword(it.keyword).lowercase() }.toMutableSet()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawKw = obj.getString("keyword")
                    val kw = com.example.util.WebRuleHelper.normalizeKeyword(rawKw)
                    val caseSensitive = obj.optBoolean("caseSensitive", false)
                    if (kw.isNotBlank() && existingNormalized.add(kw.lowercase())) {
                        dao.insertBlockedKeyword(
                            BlockedKeyword(
                                keyword = kw,
                                caseSensitive = caseSensitive,
                                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            if (root.has("preferences")) {
                val prefs = root.getJSONObject("preferences")
                preferencesRepository.restorePreferencesSync(prefs)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup from JSON", e)
            false
        }
    }

    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val json = createBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export backup to URI: $uri", e)
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        content.append(line).append("\n")
                        line = reader.readLine()
                    }
                }
            }
            val success = restoreFromJson(content.toString())
            if (success) {
                reloadAllInMemoryManagers()
                _restoreVersion.value++
                backupToCloudAsync(context)
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to parse backup content"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import backup from URI: $uri", e)
            Result.failure(e)
        }
    }

    fun reloadAllInMemoryManagers() {
        try {
            EmergencyBreakManager.init(context)
            KillswitchManager.init(context)
            InterceptTracker.init(context)
            TimeIntegrityManager.init(context)
            com.example.service.FocusAccessibilityService.instance?.reloadAllRules()
        } catch (e: Exception) {
            Log.w(TAG, "Error reloading in-memory managers", e)
        }
    }

    /**
     * Uploads backup data directly to Cloud Firestore.
     * Enforces Anti-Wipe Safety Guard: skips upload if local state has 0 rules and cloud has rules,
     * unless explicitly triggered manually (isManual = true).
     */
    suspend fun syncToCloudDirect(email: String, isManual: Boolean = false): Result<Long> = withContext(Dispatchers.IO) {
        if (isRestoringFromCloud.get()) {
            return@withContext Result.failure(Exception("Cannot upload while restore is in progress"))
        }
        if (!isInitialCloudSyncResolved.get() && !isManual) {
            Log.d(TAG, "syncToCloudDirect skipped: initial cloud sync not resolved yet")
            return@withContext Result.failure(Exception("Initial sync not resolved yet"))
        }

        FirebaseInitializer.ensureInitialized(context)

        return@withContext try {
            val normalizedEmail = email.trim().lowercase()
            val dao = database.focusDao()
            val firestore = FirebaseFirestore.getInstance()

            // Calculate total rules currently configured locally
            val localRuleCount = dao.getAllBlockedAppsSync().size +
                    dao.getAllScreenTimeLimitsSync().size +
                    dao.getAllGroupsSync().size +
                    dao.getAllBlockedWebsitesSync().size +
                    dao.getAllBlockedKeywordsSync().size

            // Anti-Wipe Safety Guard:
            // If local state is empty (0 rules) and this is an automatic background upload,
            // check whether cloud already has existing rules to protect against wiping user data on reinstall.
            if (localRuleCount == 0 && !isManual) {
                val cloudSnapshot = try {
                    firestore.collection(FIRESTORE_USERS_COLLECTION).document(normalizedEmail)
                        .get(Source.SERVER).await()
                } catch (_: Exception) {
                    try {
                        firestore.collection(FIRESTORE_USERS_COLLECTION).document(normalizedEmail).get().await()
                    } catch (_: Exception) { null }
                }

                val cloudRulesCount = cloudSnapshot?.getLong("rulesCount")?.toInt()
                    ?: if (cloudSnapshot?.getString("backupJson")?.contains("\"packageName\"") == true) 1 else 0

                if (cloudSnapshot != null && cloudSnapshot.exists() && cloudRulesCount > 0) {
                    Log.w(TAG, "Anti-wipe safety guard triggered: Local has 0 rules while cloud has $cloudRulesCount rules. Aborting upload to protect cloud backup.")
                    return@withContext Result.failure(Exception("Anti-wipe guard: local state is empty while cloud backup has existing rules"))
                }
            }

            val allPrefsMap = collectAllSharedPreferencesMap()
            val fullStateMap = collectFullStateMap(allPrefsMap)
            val prefsJson = collectAllPrefsJson()
            val backupJson = createBackupJson()
            val now = System.currentTimeMillis()

            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: normalizedEmail

            val docData = hashMapOf(
                "email" to normalizedEmail,
                "uid" to uid,
                "signed_in_google_email" to normalizedEmail,
                "focus_lock_data" to fullStateMap,
                "shared_preferences" to allPrefsMap,
                "prefs_json" to prefsJson,
                "backupJson" to backupJson,
                "rulesCount" to localRuleCount,
                "updatedAt" to now,
                "timestamp" to now,
                "version" to BACKUP_VERSION
            )

            // Primary location: users/{normalizedEmail}
            firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(normalizedEmail)
                .set(docData)
                .await()

            // Subcollection location: users/{uid}/backups/focus_lock_full_state
            try {
                firestore.collection(FIRESTORE_USERS_COLLECTION)
                    .document(uid)
                    .collection("backups")
                    .document("focus_lock_full_state")
                    .set(docData)
                    .await()
            } catch (e: Exception) {
                Log.d(TAG, "Subcollection backup write notice: ${e.message}")
            }

            preferencesRepository.setLastCloudBackupTime(now)
            Result.success(now)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync to Cloud Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun syncToCloud(uid: String, email: String?): Result<Long> {
        val targetEmail = email ?: preferencesRepository.getConnectedGmailAccountSync()
        return if (!targetEmail.isNullOrEmpty()) {
            syncToCloudDirect(targetEmail, isManual = true)
        } else {
            Result.failure(Exception("No connected Google account"))
        }
    }

    /**
     * Restores backup from Cloud Firestore.
     * Uses Source.SERVER first, checks both users/{normalizedEmail} and users/{uid}/backups/focus_lock_full_state,
     * restores all typed SharedPreferences via .commit(), restores Room entities, and reloads in-memory managers.
     */
    suspend fun restoreFromCloud(email: String, isInitialStartup: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        FirebaseInitializer.ensureInitialized(context)

        isRestoringFromCloud.set(true)
        try {
            val normalizedEmail = email.trim().lowercase()
            preferencesRepository.setConnectedGmailAccount(normalizedEmail)
            val firestore = FirebaseFirestore.getInstance()
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: normalizedEmail

            // 1. Fetch from Server first, fallback to default cache
            var snapshot: com.google.firebase.firestore.DocumentSnapshot? = try {
                firestore.collection(FIRESTORE_USERS_COLLECTION).document(normalizedEmail)
                    .get(Source.SERVER).await()
            } catch (_: Exception) {
                try {
                    firestore.collection(FIRESTORE_USERS_COLLECTION).document(normalizedEmail).get().await()
                } catch (_: Exception) { null }
            }

            if (snapshot == null || !snapshot.exists()) {
                snapshot = try {
                    firestore.collection(FIRESTORE_USERS_COLLECTION)
                        .document(uid)
                        .collection("backups")
                        .document("focus_lock_full_state")
                        .get(Source.SERVER).await()
                } catch (_: Exception) {
                    try {
                        firestore.collection(FIRESTORE_USERS_COLLECTION)
                            .document(uid)
                            .collection("backups")
                            .document("focus_lock_full_state")
                            .get().await()
                    } catch (_: Exception) { null }
                }
            }

            if (snapshot != null && snapshot.exists()) {
                val timestamp = snapshot.getLong("updatedAt") ?: snapshot.getLong("timestamp") ?: System.currentTimeMillis()
                var anyRestored = false

                // Method A: Structured focus_lock_data map
                @Suppress("UNCHECKED_CAST")
                val focusLockData = snapshot.get("focus_lock_data") as? Map<String, Any?>
                if (focusLockData != null && focusLockData.isNotEmpty()) {
                    val mapRestored = restoreFromFullStateMap(focusLockData)
                    if (mapRestored) anyRestored = true
                }

                // Method B: Typed shared_preferences map
                @Suppress("UNCHECKED_CAST")
                val sharedPrefsMap = snapshot.get("shared_preferences") as? Map<String, Any?>
                if (sharedPrefsMap != null && sharedPrefsMap.isNotEmpty()) {
                    restoreFromSharedPreferencesMap(sharedPrefsMap)
                    anyRestored = true
                }

                // Method C: prefs_json
                val prefsJson = snapshot.getString("prefs_json")
                if (!prefsJson.isNullOrEmpty()) {
                    restoreFromPrefsJson(prefsJson)
                    anyRestored = true
                }

                // Method D: backupJson
                val backupJson = snapshot.getString("backupJson")
                if (!backupJson.isNullOrEmpty()) {
                    val roomRestored = restoreFromJson(backupJson)
                    if (roomRestored) anyRestored = true
                }

                if (anyRestored) {
                    preferencesRepository.setLastCloudBackupTime(timestamp)
                    reloadAllInMemoryManagers()
                    _restoreVersion.value++
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Cloud backup restored!", Toast.LENGTH_SHORT).show()
                    }
                    isInitialCloudSyncResolved.set(true)
                    return@withContext Result.success(true)
                }
            }

            // Cloud document does not exist yet:
            isInitialCloudSyncResolved.set(true)

            if (!isInitialStartup) {
                // User explicitly connected account: upload current local settings as initial backup
                val syncResult = syncToCloudDirect(normalizedEmail, isManual = true)
                if (syncResult.isSuccess) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Cloud backup connected & synced!", Toast.LENGTH_SHORT).show()
                    }
                    Result.success(false)
                } else {
                    Result.success(false)
                }
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from Cloud Firestore", e)
            isInitialCloudSyncResolved.set(true)
            if (!isInitialStartup) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Cloud sync notice: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            Result.failure(e)
        } finally {
            isRestoringFromCloud.set(false)
        }
    }
}
