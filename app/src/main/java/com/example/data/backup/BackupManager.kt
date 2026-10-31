package com.example.data.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.local.FocusDatabase
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.ui.theme.BaseThemeMode
import com.example.ui.theme.SecondaryThemePalette
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.FocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest

class BackupManager(
    private val context: Context,
    private val database: FocusDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "BackupManager"
        const val BACKUP_VERSION = 1
        const val FIRESTORE_COLLECTION = "user_backups"

        fun isFirebaseAvailable(context: Context): Boolean {
            return try {
                com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
            } catch (e: Throwable) {
                false
            }
        }

        fun getDefaultWebClientId(context: Context): String? {
            return try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else null
            } catch (e: Throwable) {
                null
            }
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

        // 5. Blocked Websites
        val websitesArray = JSONArray()
        dao.getAllBlockedWebsitesSync().forEach { site ->
            val obj = JSONObject()
            obj.put("domainOrUrl", site.domainOrUrl)
            obj.put("dateAdded", site.dateAdded)
            websitesArray.put(obj)
        }
        root.put("blockedWebsites", websitesArray)

        // 6. Blocked Keywords
        val keywordsArray = JSONArray()
        dao.getAllBlockedKeywordsSync().forEach { kw ->
            val obj = JSONObject()
            obj.put("keyword", kw.keyword)
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
     * CRITICAL: Preserves all active volatile counters (usedMinutesToday, currentSessionUsedMs, cooldownEndTimestamp).
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
                        // Preserve active counters!
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
                        // Preserve active counters!
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

            // 5. Blocked Websites (De-duplicate)
            if (root.has("blockedWebsites")) {
                val array = root.getJSONArray("blockedWebsites")
                val existing = dao.getAllBlockedWebsitesSync()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val domain = obj.getString("domainOrUrl").trim()
                    if (existing.none { it.domainOrUrl.equals(domain, ignoreCase = true) }) {
                        dao.insertBlockedWebsite(
                            BlockedWebsite(
                                domainOrUrl = domain,
                                dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }

            // 6. Blocked Keywords (De-duplicate)
            if (root.has("blockedKeywords")) {
                val array = root.getJSONArray("blockedKeywords")
                val existing = dao.getAllBlockedKeywordsSync()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val kw = obj.getString("keyword").trim()
                    val caseSensitive = obj.optBoolean("caseSensitive", false)
                    if (existing.none { it.keyword.equals(kw, ignoreCase = !caseSensitive) }) {
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

            // 7. Preferences
            if (root.has("preferences")) {
                val prefs = root.getJSONObject("preferences")
                if (prefs.has("base_theme_mode")) {
                    val modeName = prefs.getString("base_theme_mode")
                    BaseThemeMode.entries.find { it.name == modeName }?.let {
                        preferencesRepository.setBaseThemeMode(it)
                    }
                }
                if (prefs.has("secondary_palette")) {
                    val paletteName = prefs.getString("secondary_palette")
                    SecondaryThemePalette.entries.find { it.name == paletteName }?.let {
                        preferencesRepository.setSecondaryPalette(it)
                    }
                }
                if (prefs.has("master_enabled")) {
                    preferencesRepository.setMasterEnabled(prefs.getBoolean("master_enabled"))
                }
                if (prefs.has("cheat_protection_enabled")) {
                    val enabled = prefs.getBoolean("cheat_protection_enabled")
                    val hash = prefs.optString("cheat_protection_hash", null)
                    val salt = prefs.optString("cheat_protection_salt", null)
                    preferencesRepository.restoreCheatProtection(enabled, hash, salt)
                }
                if (prefs.has("invincible_mode_enabled")) {
                    preferencesRepository.setInvincibleModeEnabled(prefs.getBoolean("invincible_mode_enabled"))
                }
                if (prefs.has("clock_tamper_protection_enabled")) {
                    preferencesRepository.setClockTamperProtectionEnabled(prefs.getBoolean("clock_tamper_protection_enabled"))
                }
                if (prefs.has("reclaimed_commitment")) {
                    preferencesRepository.setReclaimedCommitment(prefs.getString("reclaimed_commitment"))
                }
                if (prefs.has("cognitive_passphrase")) {
                    preferencesRepository.setCognitivePassphrase(prefs.getString("cognitive_passphrase"))
                }
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
            if (success) Result.success(true) else Result.failure(Exception("Failed to parse backup content"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import backup from URI: $uri", e)
            Result.failure(e)
        }
    }

    /**
     * Syncs backup data silently to Cloud Firestore.
     */
    suspend fun syncToCloud(uid: String, email: String?): Result<Long> = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) {
            return@withContext Result.failure(Exception("Firebase is not initialized. Please ensure google-services.json is configured."))
        }

        return@withContext try {
            val jsonString = createBackupJson()
            val now = System.currentTimeMillis()
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            val docData = hashMapOf(
                "userId" to uid,
                "email" to (email ?: ""),
                "timestamp" to now,
                "backupJson" to jsonString,
                "version" to BACKUP_VERSION
            )

            firestore.collection(FIRESTORE_COLLECTION).document(uid).set(docData).await()
            preferencesRepository.setLastCloudBackupTime(now)
            Result.success(now)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync to Cloud Firestore", e)
            Result.failure(e)
        }
    }

    /**
     * Restores backup silently from Cloud Firestore if a backup exists.
     */
    suspend fun restoreFromCloud(uid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable(context)) {
            return@withContext Result.failure(Exception("Firebase is not initialized."))
        }

        return@withContext try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val snapshot = firestore.collection(FIRESTORE_COLLECTION).document(uid).get().await()

            if (snapshot.exists()) {
                val jsonString = snapshot.getString("backupJson")
                val timestamp = snapshot.getLong("timestamp") ?: System.currentTimeMillis()
                if (!jsonString.isNullOrEmpty()) {
                    val restored = restoreFromJson(jsonString)
                    if (restored) {
                        preferencesRepository.setLastCloudBackupTime(timestamp)
                        return@withContext Result.success(true)
                    }
                }
            }
            Result.success(false) // No existing cloud backup found; nothing restored
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from Cloud Firestore", e)
            Result.failure(e)
        }
    }
}
