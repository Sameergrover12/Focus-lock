package com.example.data.backup

import android.content.Context
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
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FocusRepository
import com.example.util.FirebaseInitializer
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
        const val BACKUP_VERSION = 1
        const val FIRESTORE_USERS_COLLECTION = "users"

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

    // Critical race-condition guard: prevents fresh installs from reverse overwriting cloud backup
    val isRestoringFromCloud = AtomicBoolean(false)

    // Global reactive restore version: incremented ONLY when external Cloud or JSON restore finishes
    private val _restoreVersion = MutableStateFlow(0)
    val restoreVersion: StateFlow<Int> = _restoreVersion.asStateFlow()

    // 400ms Debounce scope on Dispatchers.IO
    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoBackupJob: Job? = null

    /**
     * Schedules an auto-backup to Firestore with a 400ms debounce purely on Dispatchers.IO.
     * Backs up automatically whenever a Google account is connected, independent of Focus Protection.
     */
    fun backupToCloudAsync(context: Context? = null) {
        if (isRestoringFromCloud.get()) {
            Log.d(TAG, "backupToCloudAsync skipped: restore in progress")
            return
        }

        autoBackupJob?.cancel()
        autoBackupJob = backupScope.launch(Dispatchers.IO) {
            delay(400) // 400ms debounce
            if (isRestoringFromCloud.get()) return@launch

            val targetContext = context ?: this@BackupManager.context
            FirebaseInitializer.ensureInitialized(targetContext)

            val email = preferencesRepository.getConnectedGmailAccountSync()
                ?: try { preferencesRepository.connectedGmailAccount.first() } catch (e: Exception) { null }
            if (email.isNullOrEmpty()) return@launch

            try {
                syncToCloudDirect(email)
            } catch (e: Exception) {
                Log.w(TAG, "Silent auto-backup error: ${e.message}")
            }
        }
    }

    fun scheduleAutoBackup(context: Context? = null) {
        backupToCloudAsync(context)
    }

    /**
     * Collects all entries from SharedPreferences into a dynamic JSON object.
     * Covers all keys dynamically without missing rules or settings.
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
     * Restores all entries from a preferences JSON string into SharedPreferences via .apply().
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
            editor.apply()
            preferencesRepository.restorePreferencesSync(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from prefs_json", e)
        }
    }

    /**
     * Serializes all user configuration rules (without active counters) into a standard JSON string.
     */
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        val dao = database.focusDao()

        // 1. Blocked Apps
        val blockedAppsArray = JSONArray()
        dao.getAllBlockedAppsSync().forEach { app ->
            val obj = JSONObject()
            obj.put("packageName", app.packageName)
            obj.put("appName", app.appName)
            obj.put("dateAdded", app.dateAdded)
            blockedAppsArray.put(obj)
        }
        root.put("blockedApps", blockedAppsArray)

        // 2. Screen Time Limits (Preserves user configuration, omits volatile counters)
        val limitsArray = JSONArray()
        dao.getAllScreenTimeLimitsSync().forEach { limit ->
            val obj = JSONObject()
            obj.put("packageName", limit.packageName)
            obj.put("dailyLimitMinutes", limit.dailyLimitMinutes)
            obj.put("sessionLimitSeconds", limit.sessionLimitSeconds)
            obj.put("cooldownSeconds", limit.cooldownSeconds)
            limitsArray.put(obj)
        }
        root.put("screenTimeLimits", limitsArray)

        // 3. App Groups
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

        // 4. Group Apps
        val groupAppsArray = JSONArray()
        dao.getAllGroupAppsSync().forEach { ga ->
            val obj = JSONObject()
            obj.put("groupId", ga.groupId)
            obj.put("packageName", ga.packageName)
            groupAppsArray.put(obj)
        }
        root.put("groupApps", groupAppsArray)

        // 5. Blocked Websites (De-duplicated & Normalized)
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

        // 6. Blocked Keywords (De-duplicated & Normalized)
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

        // 7. Preferences Snapshot
        val prefsObj = JSONObject()
        preferencesRepository.getPreferencesSnapshot().forEach { (k, v) ->
            if (v != null) {
                prefsObj.put(k, v)
            }
        }
        root.put("preferences", prefsObj)

        return@withContext root.toString(2)
    }

    /**
     * Restores rules, configurations, limits, and settings from a JSON payload.
     * CRITICAL: Preserves all active volatile counters.
     */
    suspend fun restoreFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val root = JSONObject(jsonString)
            val dao = database.focusDao()
            val todayDate = FocusRepository.getTodayDateString()

            // 1. Blocked Apps
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

            // 2. Screen Time Limits (Update configurations, keep active usage safe)
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

            // 3. App Groups
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
                        lastResetDate = existing?.lastResetDate ?: todayDate,
                        currentSessionUsedMs = existing?.currentSessionUsedMs ?: 0L,
                        lastSessionExitTimestamp = existing?.lastSessionExitTimestamp ?: 0L,
                        cooldownEndTimestamp = existing?.cooldownEndTimestamp ?: 0L
                    )
                    dao.insertGroup(group)
                }
            }

            // 4. Group Apps
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

            // 5. Blocked Websites (De-duplicate with smart domain normalization)
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

            // 6. Blocked Keywords (De-duplicate with case-insensitive matching)
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

            // 7. Preferences (Applied via .apply())
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

    /**
     * Exports backup data directly to an offline JSON file via Storage Access Framework URI.
     */
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

    /**
     * Imports and applies backup data from an offline JSON file via Storage Access Framework URI.
     */
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

    /**
     * Uploads backup data directly to Cloud Firestore keyed deterministically by the normalized email address.
     * Does NOT require or depend on FirebaseAuth.currentUser.
     */
    suspend fun syncToCloudDirect(email: String): Result<Long> = withContext(Dispatchers.IO) {
        if (isRestoringFromCloud.get()) {
            return@withContext Result.failure(Exception("Cannot upload while restoring from cloud"))
        }

        FirebaseInitializer.ensureInitialized(context)

        return@withContext try {
            val normalizedEmail = email.trim().lowercase()
            val prefsJson = collectAllPrefsJson()
            val backupJson = createBackupJson()
            val now = System.currentTimeMillis()
            val firestore = FirebaseFirestore.getInstance()

            val docData = hashMapOf(
                "email" to normalizedEmail,
                "signed_in_google_email" to normalizedEmail,
                "prefs_json" to prefsJson,
                "backupJson" to backupJson,
                "updatedAt" to now,
                "timestamp" to now,
                "version" to BACKUP_VERSION
            )

            firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(normalizedEmail)
                .set(docData)
                .await()

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
            syncToCloudDirect(targetEmail)
        } else {
            Result.failure(Exception("No connected Google account"))
        }
    }

    /**
     * Restores backup silently from Cloud Firestore users/{normalizedEmail}.
     * Uses Source.SERVER first to retrieve newest settings.
     * Prevents race-condition overwrites using isRestoringFromCloud.
     */
    suspend fun restoreFromCloud(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        FirebaseInitializer.ensureInitialized(context)

        isRestoringFromCloud.set(true)
        try {
            val normalizedEmail = email.trim().lowercase()
            preferencesRepository.setConnectedGmailAccount(normalizedEmail)
            val firestore = FirebaseFirestore.getInstance()

            // 1. Fetch users/{normalizedEmail} from Server first, fallback to default source
            var snapshot: com.google.firebase.firestore.DocumentSnapshot? = try {
                firestore.collection(FIRESTORE_USERS_COLLECTION).document(normalizedEmail)
                    .get(Source.SERVER).await()
            } catch (e: Exception) {
                try {
                    firestore.collection(FIRESTORE_USERS_COLLECTION).document(normalizedEmail).get().await()
                } catch (e2: Exception) {
                    null
                }
            }

            if (snapshot != null && snapshot.exists()) {
                val prefsJson = snapshot.getString("prefs_json")
                val backupJson = snapshot.getString("backupJson")
                val timestamp = snapshot.getLong("updatedAt") ?: snapshot.getLong("timestamp") ?: System.currentTimeMillis()

                var anyRestored = false
                if (!prefsJson.isNullOrEmpty()) {
                    restoreFromPrefsJson(prefsJson)
                    anyRestored = true
                }
                if (!backupJson.isNullOrEmpty()) {
                    val roomRestored = restoreFromJson(backupJson)
                    if (roomRestored) anyRestored = true
                }

                if (anyRestored) {
                    preferencesRepository.setLastCloudBackupTime(timestamp)
                    _restoreVersion.value++
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Cloud backup restored!", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext Result.success(true)
                }
            }

            // 2. Document does not exist yet: upload current local settings as initial backup
            val syncResult = syncToCloudDirect(normalizedEmail)
            if (syncResult.isSuccess) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Cloud backup connected & synced!", Toast.LENGTH_SHORT).show()
                }
                Result.success(false)
            } else {
                val errorMsg = syncResult.exceptionOrNull()?.message ?: "Unknown cloud sync error"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Cloud sync notice: $errorMsg", Toast.LENGTH_LONG).show()
                }
                Result.failure(syncResult.exceptionOrNull() ?: Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from Cloud Firestore", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Cloud sync error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            Result.failure(e)
        } finally {
            isRestoringFromCloud.set(false)
        }
    }
}
