package com.example.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.room.withTransaction
import com.example.data.local.FocusDatabase
import com.example.data.local.entity.AppGroup
import com.example.data.local.entity.BlockedApp
import com.example.data.local.entity.BlockedKeyword
import com.example.data.local.entity.BlockedWebsite
import com.example.data.local.entity.DailyScreenTime
import com.example.data.local.entity.DailyUsageLog
import com.example.data.local.entity.GroupApp
import com.example.data.local.entity.ScreenTimeLimit
import com.example.data.preferences.UserPreferencesRepository
import com.example.util.FirebaseInitializer
import com.example.util.TimeIntegrityManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

class BackupManager(
    private val context: Context,
    private val database: FocusDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val dao = database.focusDao()
    private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoSyncObserverJob: Job? = null
    private var debouncedSyncJob: Job? = null

    /**
     * Strict Restore-First Gate:
     * Defaults to false on every cold start, sign-in, and account switch.
     * Auto-backup and cloud uploads are strictly blocked while this is false.
     * Set to true ONLY after cloud restore has completed (or verified new account).
     */
    @Volatile
    var isRestoreCompletedForCurrentSession: Boolean = false
        set(value) {
            field = value
            isCloudSessionReady = value
        }

    @Volatile
    var isCloudSessionReady: Boolean = false
        private set

    /**
     * Tracks whether the user explicitly deleted their last rule during the active session
     * AFTER cloud restore completed. Prevents an empty local state from ever overwriting
     * a non-empty cloud backup unless the user intentionally deleted their last rule.
     */
    @Volatile
    var hasUserExplicitlyDeletedLastRuleAfterRestore: Boolean = false

    @Volatile
    private var lastUploadedJsonHash: Int = 0

    private val isRestoringFromCloud = AtomicBoolean(false)

    private val _restoreVersion = MutableStateFlow(0L)
    val restoreVersion: StateFlow<Long> = _restoreVersion.asStateFlow()

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = _isSyncingFlow.asStateFlow()

    private val _lastSyncErrorFlow = MutableStateFlow<String?>(null)
    val lastSyncErrorFlow: StateFlow<String?> = _lastSyncErrorFlow.asStateFlow()

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_VERSION = 7
        const val FIRESTORE_USER_BACKUPS_COLLECTION = "user_backups"
        const val FIRESTORE_MODULES_SUBCOLLECTION = "backup_modules"
        const val DOC_METADATA = "metadata"
        const val DOC_BLOCKED_APPS = "blocked_apps"
        const val DOC_BLOCKED_SITES = "blocked_sites"
        const val DOC_APP_GROUPS = "app_groups"
        const val DOC_SCREEN_TIME_LIMITS = "screen_time_limits"
        const val DOC_PREFERENCES = "preferences"
        const val DOC_USAGE_HISTORY = "usage_history"

        const val FIRESTORE_CLOUD_BACKUPS_COLLECTION = "focus_lock_cloud_backups"
        const val FIRESTORE_USERS_COLLECTION = "users"
        const val FIRESTORE_BACKUP_COLLECTION = "focus_lock_backup"
        const val FIRESTORE_MASTER_VAULT_DOC = "master_vault"
        private const val FIRESTORE_LEGACY_SUBCOLLECTION = "backups"
        private const val FIRESTORE_LEGACY_DOC_ID = "focus_lock_full_state"
        private const val CLOUD_TIMEOUT_MS = 15_000L

        const val AUTH_SESSION_META_PREFS = "auth_session_meta"
        const val KEY_LAST_UID = "last_uid"
        const val KEY_LAST_ACTIVE_GMAIL_UID = "last_active_gmail_uid"
        const val KEY_ACTIVE_GMAIL_EMAIL = "active_gmail_email"
        const val KEY_ACTIVE_DISPLAY_NAME = "active_display_name"
        const val KEY_IS_CLOUD_RESTORED_FOR_SESSION = "is_cloud_restored_for_session"
        const val KEY_MODULAR_SCHEMA_SYNCED = "modular_user_backups_v7_synced"

        private val TYPED_BOOL_PREFIX = "__type_bool__:"
        private val TYPED_INT_PREFIX = "__type_int__:"
        private val TYPED_LONG_PREFIX = "__type_long__:"
        private val TYPED_FLOAT_PREFIX = "__type_float__:"
        private val TYPED_STRING_PREFIX = "__type_str__:"
        private val TYPED_STRSET_PREFIX = "__type_set__:"

        private val EXPLICIT_APP_PREF_FILES = listOf(
            "user_preferences_sync",
            "focus_emergency_breaks",
            "focus_killswitch_prefs",
            "intercept_tracker_prefs",
            "focus_notification_messages",
            "time_integrity_prefs",
            "focus_lock_prefs",
            "historical_dwell_cache"
        )

        private val WATCHED_USER_PREF_FILES = listOf(
            "user_preferences_sync",
            "focus_emergency_breaks",
            "focus_killswitch_prefs",
            "intercept_tracker_prefs",
            "focus_lock_prefs"
        )

        private val sharedPrefsListenerHolder = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        fun shouldIncludePrefFile(fileName: String): Boolean {
            if (fileName.isBlank()) return false
            if (fileName == AUTH_SESSION_META_PREFS) return false
            if (fileName.startsWith("com.google.")) return false
            if (fileName.contains("firebase", ignoreCase = true)) return false
            if (fileName.startsWith("androidx.")) return false
            if (fileName.contains("WebView", ignoreCase = true)) return false
            return true
        }

        fun getDefaultWebClientId(context: Context): String {
            return runCatching {
                context.getString(com.example.R.string.default_web_client_id)
            }.getOrDefault("")
        }

        fun parseScheduleHourMinute(timeStr: String, defaultHour: Int, defaultMinute: Int): Pair<Int, Int> {
            return try {
                val parts = timeStr.trim().split(":")
                if (parts.size >= 2) {
                    val h = parts[0].trim().toIntOrNull()?.coerceIn(0, 23) ?: defaultHour
                    val m = parts[1].trim().toIntOrNull()?.coerceIn(0, 59) ?: defaultMinute
                    h to m
                } else {
                    defaultHour to defaultMinute
                }
            } catch (_: Exception) {
                defaultHour to defaultMinute
            }
        }

        fun formatHourMinute(hour: Int, minute: Int): String {
            return "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }

        /**
         * Reconstructs a unified state map from modular subcollection documents under
         * `/user_backups/{userId}/backup_modules/` (`blocked_apps`, `blocked_sites`,
         * `app_groups`, `screen_time_limits`, `preferences`, `usage_history`, `metadata`).
         */
        fun reconstructStateMapFromModularDocs(modularDocs: Map<String, Map<String, Any?>>): Map<String, Any?> {
            val reconstructed = mutableMapOf<String, Any?>()
            val focusLockData = mutableMapOf<String, Any?>()
            for ((_, docData) in modularDocs) {
                for ((key, value) in docData) {
                    if (value != null) {
                        reconstructed[key] = value
                        focusLockData[key] = value
                    }
                }
            }
            reconstructed["focus_lock_data"] = focusLockData
            return reconstructed
        }

        /**
         * Pure cross-version JSON parser helper for unit testing and resilient validation.
         * Parses each section in its own independent `runCatching` block.
         */
        fun parseCrossVersionBackupSummary(jsonString: String): CrossVersionBackupSummary {
            val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: JSONObject()
            val payload = root.optJSONObject("focus_lock_data") ?: root

            val blockedPackages = mutableListOf<String>()
            runCatching {
                val rawPkgs = payload.optJSONArray("blockedPackageNames")
                if (rawPkgs != null) {
                    for (i in 0 until rawPkgs.length()) {
                        val pkg = rawPkgs.optString(i, "").trim()
                        if (pkg.isNotEmpty() && pkg !in blockedPackages) {
                            blockedPackages.add(pkg)
                        }
                    }
                }
                val appsArr = payload.optJSONArray("blockedApps")
                if (appsArr != null) {
                    for (i in 0 until appsArr.length()) {
                        val item = appsArr.opt(i)
                        val pkg = when (item) {
                            is JSONObject -> item.optString("packageName", "").trim()
                            is String -> item.trim()
                            else -> ""
                        }
                        if (pkg.isNotEmpty() && pkg !in blockedPackages) {
                            blockedPackages.add(pkg)
                        }
                    }
                }
            }

            var limitsCount = 0
            runCatching {
                val limitsArr = payload.optJSONArray("screenTimeLimits") ?: payload.optJSONArray("appLimits")
                if (limitsArr != null) {
                    limitsCount = limitsArr.length()
                }
            }

            var groupsCount = 0
            runCatching {
                val groupsArr = payload.optJSONArray("appGroups") ?: payload.optJSONArray("groups")
                if (groupsArr != null) {
                    groupsCount = groupsArr.length()
                }
            }

            var websitesCount = 0
            runCatching {
                val webArr = payload.optJSONArray("blockedWebsites") ?: payload.optJSONArray("websites")
                if (webArr != null) {
                    websitesCount = webArr.length()
                }
            }

            var keywordsCount = 0
            runCatching {
                val kwArr = payload.optJSONArray("blockedKeywords") ?: payload.optJSONArray("keywords")
                if (kwArr != null) {
                    keywordsCount = kwArr.length()
                }
            }

            var themeBaseMode: String? = null
            var themePalette: String? = null
            runCatching {
                val prefs = payload.optJSONObject("preferences") ?: payload.optJSONObject("theme")
                if (prefs != null) {
                    themeBaseMode = prefs.optString("base_theme_mode", "").ifEmpty {
                        prefs.optString("baseThemeMode", "").ifEmpty { null }
                    }
                    themePalette = prefs.optString("secondary_palette", "").ifEmpty {
                        prefs.optString("secondaryPalette", "").ifEmpty { null }
                    }
                }
            }

            return CrossVersionBackupSummary(
                blockedPackageNames = blockedPackages,
                limitsCount = limitsCount,
                groupsCount = groupsCount,
                websitesCount = websitesCount,
                keywordsCount = keywordsCount,
                baseThemeMode = themeBaseMode,
                secondaryPalette = themePalette
            )
        }
    }

    data class CrossVersionBackupSummary(
        val blockedPackageNames: List<String>,
        val limitsCount: Int,
        val groupsCount: Int,
        val websitesCount: Int,
        val keywordsCount: Int,
        val baseThemeMode: String?,
        val secondaryPalette: String?
    )

    init {
        startAutoCloudSyncObservers()
    }

    /**
     * Watches Room database tables and SharedPreferences files and triggers an immediate (0ms)
     * non-cancellable cloud backup whenever any rule or setting changes after restore completes.
     */
    private fun startAutoCloudSyncObservers() {
        autoSyncObserverJob?.cancel()
        autoSyncObserverJob = backupScope.launch {
            launch {
                val roomFlow = combine(
                    dao.getAllBlockedApps(),
                    dao.getAllScreenTimeLimits(),
                    dao.getAllGroups(),
                    dao.getAllGroupApps(),
                    dao.getAllBlockedWebsites()
                ) { a, b, c, d, e ->
                    "${a.hashCode()}_${b.hashCode()}_${c.hashCode()}_${d.hashCode()}_${e.hashCode()}"
                }

                val prefFlow = combine(
                    preferencesRepository.baseThemeMode,
                    preferencesRepository.secondaryPalette,
                    preferencesRepository.isMasterEnabled,
                    preferencesRepository.isCheatProtectionEnabled,
                    preferencesRepository.isInvincibleModeEnabled
                ) { t, p, m, c, i ->
                    "${t.name}_${p.name}_${m}_${c}_$i"
                }

                combine(
                    roomFlow,
                    dao.getAllBlockedKeywords(),
                    prefFlow,
                    preferencesRepository.reclaimedCommitment,
                    preferencesRepository.cognitivePassphrase
                ) { r, k, p, rc, cp ->
                    "${r}_${k.hashCode()}_${p}_${rc.hashCode()}_${cp.hashCode()}"
                }
                    .drop(1)
                    .collect {
                        if (!isRestoreCompletedForCurrentSession || isRestoringFromCloud.get()) return@collect
                        uploadToCloudImmediate()
                    }
            }

            launch {
                registerSharedPreferencesListeners()
            }
        }
    }

    private fun computeDeterministicPayloadHash(vaultJson: String): Int {
        return try {
            val root = JSONObject(vaultJson)
            root.remove("timestamp")
            root.remove("lastUpdated")
            root.remove("updatedAt")
            root.optJSONObject("preferences")?.let { prefs ->
                prefs.remove("last_cloud_backup_time")
            }
            root.optJSONObject("preferencesVault")?.let { vault ->
                vault.remove("time_integrity_prefs")
                vault.remove("focus_notification_messages")
                vault.remove("historical_dwell_cache")
                vault.optJSONObject("user_preferences_sync")?.remove("last_cloud_backup_time")
            }
            root.toString().hashCode()
        } catch (_: Exception) {
            vaultJson.hashCode()
        }
    }

    private fun registerSharedPreferencesListeners() {
        val filesToWatch = (WATCHED_USER_PREF_FILES + "${context.packageName}_preferences").distinct()
        synchronized(sharedPrefsListenerHolder) {
            sharedPrefsListenerHolder.clear()
            for (fileName in filesToWatch) {
                if (!shouldIncludePrefFile(fileName)) continue
                val sp = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == null) return@OnSharedPreferenceChangeListener
                    val lowerKey = key.lowercase()
                    if (lowerKey.contains("anchor_") ||
                        lowerKey.contains("boot_count") ||
                        lowerKey.contains("clock_drift") ||
                        lowerKey.contains("last_tick") ||
                        lowerKey.contains("session_start") ||
                        lowerKey.contains("last_sync") ||
                        lowerKey.contains("sync_") ||
                        lowerKey == "last_cloud_backup_time" ||
                        lowerKey == "signed_in_google_email" ||
                        lowerKey == "connected_gmail_account" ||
                        lowerKey == "last_notified_date"
                    ) {
                        return@OnSharedPreferenceChangeListener
                    }
                    if (!isRestoreCompletedForCurrentSession || isRestoringFromCloud.get()) {
                        return@OnSharedPreferenceChangeListener
                    }
                    uploadToCloudImmediate()
                }
                sp.registerOnSharedPreferenceChangeListener(listener)
                sharedPrefsListenerHolder.add(listener)
            }
        }
    }

    /**
     * Startup gatekeeper:
     * Checks `"auth_session_meta"` first. If the user is already signed in and
     * `is_cloud_restored_for_session == true`, sets `isRestoreCompletedForCurrentSession = true`
     * and returns immediately (0ms delay) without calling Firestore on startup.
     * Only if `is_cloud_restored_for_session == false` does it pull once from Firestore.
     */
    fun resolveInitialCloudSyncOnStartup() {
        val metaPrefs = context.getSharedPreferences(AUTH_SESSION_META_PREFS, Context.MODE_PRIVATE)
        val metaRestored = metaPrefs.getBoolean(KEY_IS_CLOUD_RESTORED_FOR_SESSION, false)
        val metaEmail = metaPrefs.getString(KEY_ACTIVE_GMAIL_EMAIL, null)?.trim()?.lowercase()
        val savedEmail = preferencesRepository.getConnectedGmailAccountSync()?.trim()?.lowercase()
        val knownEmail = metaEmail ?: savedEmail

        if (metaRestored && !knownEmail.isNullOrBlank()) {
            // Instant 0ms startup recognition: already restored for this session!
            isRestoreCompletedForCurrentSession = true
            isCloudSessionReady = true
            val isModularSynced = metaPrefs.getBoolean(KEY_MODULAR_SCHEMA_SYNCED, false)
            if (!savedEmail.equals(knownEmail, ignoreCase = true) || !isModularSynced) {
                backupScope.launch {
                    if (!savedEmail.equals(knownEmail, ignoreCase = true)) {
                        preferencesRepository.setConnectedGmailAccount(knownEmail)
                    }
                    if (!isModularSynced) {
                        if (hasAnyLocalUserData()) {
                            syncToCloudDirect(knownEmail, isManual = true)
                        } else {
                            restoreFromCloud(knownEmail, isInitialStartup = true)
                        }
                    }
                }
            }
            return
        }

        backupScope.launch {
            isRestoreCompletedForCurrentSession = false
            FirebaseInitializer.ensureInitialized(context)

            val firebaseUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val activeEmail = knownEmail ?: firebaseUser?.email?.trim()?.lowercase()

            if (!activeEmail.isNullOrBlank()) {
                if (!savedEmail.equals(activeEmail, ignoreCase = true)) {
                    preferencesRepository.setConnectedGmailAccount(activeEmail)
                }
                restoreFromCloud(activeEmail, isInitialStartup = true)
            } else {
                // No user signed in: keep isRestoreCompletedForCurrentSession = false until sign-in completes
                isRestoreCompletedForCurrentSession = false
            }
        }
    }

    /**
     * Immediate (0ms delay) non-cancellable cloud upload.
     * Runs inside `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch(NonCancellable)`
     * so closing the screen, swiping the app away, or navigating away immediately after adding
     * a rule never cancels the Firestore upload.
     */
    fun uploadToCloudImmediate(isExplicitUserRuleChange: Boolean = false) {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = backupScope.launch(NonCancellable) {
            if (!isRestoreCompletedForCurrentSession || isRestoringFromCloud.get()) {
                Log.d(TAG, "uploadToCloudImmediate blocked: restore not completed yet or restore in progress")
                return@launch
            }
            if (isExplicitUserRuleChange) {
                val currentRuleCount = getLocalUserRuleCountSync()
                hasUserExplicitlyDeletedLastRuleAfterRestore = (currentRuleCount == 0)
            }
            val email = preferencesRepository.getConnectedGmailAccountSync()
                ?: try { FirebaseAuth.getInstance().currentUser?.email } catch (_: Exception) { null }
            if (!email.isNullOrBlank()) {
                syncToCloudDirect(email, isManual = false)
            }
        }
    }

    /**
     * Backwards-compatible entry points that now execute with 0ms delay and NonCancellable safety.
     */
    fun scheduleDebouncedAutoBackup(delayMs: Long = 0L) {
        uploadToCloudImmediate()
    }

    fun scheduleAutoBackup(context: Context? = null) {
        uploadToCloudImmediate()
    }

    fun backupToCloudAsync(context: Context? = null, isExplicitUserRuleChange: Boolean = false) {
        uploadToCloudImmediate(isExplicitUserRuleChange = isExplicitUserRuleChange)
    }

    /**
     * Reloads 100% of live in-memory application state from disk (`SharedPreferences`, DataStore,
     * Room Database, EmergencyBreakManager, KillswitchManager, InterceptTracker, TimeIntegrityManager,
     * and FocusAccessibilityService) so the UI and blocking engine update at 0ms without requiring
     * an app restart.
     */
    suspend fun reloadAllAppStateFromDisk(targetContext: Context = context) {
        withContext(Dispatchers.IO) {
            runCatching { preferencesRepository.syncDataStoreFromSharedPrefs() }
            runCatching { com.example.util.EmergencyBreakManager.init(targetContext.applicationContext) }
            runCatching { com.example.util.KillswitchManager.init(targetContext.applicationContext) }
            runCatching { com.example.util.InterceptTracker.init(targetContext.applicationContext) }
            runCatching { com.example.util.TimeIntegrityManager.init(targetContext.applicationContext) }
            runCatching {
                val app = targetContext.applicationContext as? com.example.FocusApplication
                app?.repository?.reloadAllFromDisk(targetContext.applicationContext)
            }
            runCatching {
                com.example.service.FocusAccessibilityService.instance?.reloadRulesImmediately()
            }
            _restoreVersion.value += 1
        }
    }

    /**
     * Dynamically discovers and serializes all app-owned SharedPreferences files in `shared_prefs`
     * plus explicitly tracked preference files, preserving exact primitive types.
     */
    fun collectAllSharedPreferencesMap(targetContext: Context = context): Map<String, Map<String, Any>> {
        val result = mutableMapOf<String, Map<String, Any>>()
        val sharedPrefsDir = File(targetContext.applicationInfo.dataDir, "shared_prefs")

        val discoveredNames = mutableSetOf<String>()
        discoveredNames.addAll(EXPLICIT_APP_PREF_FILES)
        discoveredNames.add("${targetContext.packageName}_preferences")

        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
            sharedPrefsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".xml")) {
                    val prefName = file.name.removeSuffix(".xml")
                    if (shouldIncludePrefFile(prefName)) {
                        discoveredNames.add(prefName)
                    }
                }
            }
        }

        for (prefName in discoveredNames) {
            if (!shouldIncludePrefFile(prefName)) continue
            try {
                val sp = targetContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val allEntries = sp.all
                if (allEntries.isEmpty()) continue

                val fileMap = mutableMapOf<String, Any>()
                for ((key, value) in allEntries) {
                    if (key == null || value == null) continue
                    when (value) {
                        is Boolean -> fileMap[key] = "$TYPED_BOOL_PREFIX$value"
                        is Int -> fileMap[key] = "$TYPED_INT_PREFIX$value"
                        is Long -> fileMap[key] = "$TYPED_LONG_PREFIX$value"
                        is Float -> fileMap[key] = "$TYPED_FLOAT_PREFIX$value"
                        is String -> fileMap[key] = "$TYPED_STRING_PREFIX$value"
                        is Set<*> -> {
                            val strList = value.filterIsInstance<String>()
                            val jsonArr = JSONArray(strList).toString()
                            fileMap[key] = "$TYPED_STRSET_PREFIX$jsonArr"
                        }
                    }
                }
                if (fileMap.isNotEmpty()) {
                    result[prefName] = fileMap
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to collect SharedPreferences for $prefName", e)
            }
        }
        return result
    }

    /**
     * Restores all serialized SharedPreferences files synchronously via `.commit()`
     * and immediately refreshes all in-memory managers and UI flows.
     */
    suspend fun restoreFromSharedPreferencesMap(
        targetContext: Context = context,
        vaultData: Map<String, Any?>,
        wipeExistingBeforeRestore: Boolean = false
    ) {
        if (wipeExistingBeforeRestore) {
            wipeAllLocalAppSharedPreferences(targetContext)
        }

        for ((prefName, rawFileObj) in vaultData) {
            if (!shouldIncludePrefFile(prefName)) continue
            val entriesMap: Map<*, *> = when (rawFileObj) {
                is Map<*, *> -> rawFileObj
                is JSONObject -> {
                    val m = mutableMapOf<String, Any?>()
                    val keys = rawFileObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        m[k] = rawFileObj.opt(k)
                    }
                    m
                }
                else -> continue
            }

            try {
                val sp = targetContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val editor = sp.edit()
                if (wipeExistingBeforeRestore) {
                    editor.clear()
                }

                val isDwellCacheFile = prefName == "historical_dwell_cache"
                val maxValidDwellMinutes = 18 * 60

                for ((rawKey, rawVal) in entriesMap) {
                    val key = rawKey as? String ?: continue
                    if (rawVal == null) continue
                    val isDwellKey = isDwellCacheFile ||
                        key.startsWith("daily_dwell_") ||
                        key.startsWith("dwell_day_")
                    val shouldMaxMergeNumeric = !wipeExistingBeforeRestore && isDwellKey

                    when (rawVal) {
                        is String -> {
                            when {
                                rawVal.startsWith(TYPED_BOOL_PREFIX) -> {
                                    val v = rawVal.removePrefix(TYPED_BOOL_PREFIX).toBooleanStrictOrNull() ?: false
                                    editor.putBoolean(key, v)
                                }
                                rawVal.startsWith(TYPED_INT_PREFIX) -> {
                                    val rawInt = rawVal.removePrefix(TYPED_INT_PREFIX).toIntOrNull() ?: 0
                                    val v = if (isDwellKey && rawInt >= maxValidDwellMinutes) 0 else rawInt
                                    val localInt = sp.getInt(key, 0).let { if (isDwellKey && it >= maxValidDwellMinutes) 0 else it }
                                    val finalInt = if (shouldMaxMergeNumeric) {
                                        maxOf(localInt, v)
                                    } else {
                                        v
                                    }
                                    editor.putInt(key, finalInt)
                                }
                                rawVal.startsWith(TYPED_LONG_PREFIX) -> {
                                    val v = rawVal.removePrefix(TYPED_LONG_PREFIX).toLongOrNull() ?: 0L
                                    val finalLong = if (shouldMaxMergeNumeric) {
                                        maxOf(sp.getLong(key, 0L), v)
                                    } else {
                                        v
                                    }
                                    editor.putLong(key, finalLong)
                                }
                                rawVal.startsWith(TYPED_FLOAT_PREFIX) -> {
                                    val v = rawVal.removePrefix(TYPED_FLOAT_PREFIX).toFloatOrNull() ?: 0f
                                    editor.putFloat(key, v)
                                }
                                rawVal.startsWith(TYPED_STRING_PREFIX) -> {
                                    val v = rawVal.removePrefix(TYPED_STRING_PREFIX)
                                    editor.putString(key, v)
                                }
                                rawVal.startsWith(TYPED_STRSET_PREFIX) -> {
                                    val jsonStr = rawVal.removePrefix(TYPED_STRSET_PREFIX)
                                    val arr = runCatching { JSONArray(jsonStr) }.getOrNull()
                                    val set = mutableSetOf<String>()
                                    if (arr != null) {
                                        for (i in 0 until arr.length()) {
                                            set.add(arr.optString(i))
                                        }
                                    }
                                    editor.putStringSet(key, set)
                                }
                                else -> editor.putString(key, rawVal)
                            }
                        }
                        is Boolean -> editor.putBoolean(key, rawVal)
                        is Int -> {
                            val sanitizedVal = if (isDwellKey && rawVal >= maxValidDwellMinutes) 0 else rawVal
                            val localInt = sp.getInt(key, 0).let { if (isDwellKey && it >= maxValidDwellMinutes) 0 else it }
                            val finalInt = if (shouldMaxMergeNumeric) maxOf(localInt, sanitizedVal) else sanitizedVal
                            editor.putInt(key, finalInt)
                        }
                        is Long -> {
                            val finalLong = if (shouldMaxMergeNumeric) maxOf(sp.getLong(key, 0L), rawVal) else rawVal
                            editor.putLong(key, finalLong)
                        }
                        is Double -> editor.putFloat(key, rawVal.toFloat())
                        is Float -> editor.putFloat(key, rawVal)
                        is Collection<*> -> {
                            val set = rawVal.filterIsInstance<String>().toSet()
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.commit()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore SharedPreferences file $prefName", e)
            }
        }
    }

    /**
     * Wipes all local app-owned SharedPreferences synchronously via `.clear().commit()`,
     * preserving only `auth_session_meta` and internal Firebase/Google SDK files.
     */
    fun wipeAllLocalAppSharedPreferences(targetContext: Context = context) {
        val sharedPrefsDir = File(targetContext.applicationInfo.dataDir, "shared_prefs")
        val namesToWipe = mutableSetOf<String>()
        namesToWipe.addAll(EXPLICIT_APP_PREF_FILES)
        namesToWipe.add("${targetContext.packageName}_preferences")

        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
            sharedPrefsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".xml")) {
                    val prefName = file.name.removeSuffix(".xml")
                    if (shouldIncludePrefFile(prefName)) {
                        namesToWipe.add(prefName)
                    }
                }
            }
        }

        for (prefName in namesToWipe) {
            if (!shouldIncludePrefFile(prefName)) continue
            try {
                targetContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to wipe SharedPreferences $prefName", e)
            }
        }
    }

    /**
     * Builds the complete JSON snapshot of 100% of Room tables + all SharedPreferences files.
     * Includes both legacy and modern field names so any version of Focus Lock across 5+ years
     * can parse it cleanly.
     */
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        val now = TimeIntegrityManager.getTrueTimeMillis()
        root.put("timestamp", now)
        root.put("lastUpdated", now)

        // 1. Blocked Apps & raw package names list (never filtered by currently installed apps)
        val blockedApps = dao.getAllBlockedAppsSync()
        val blockedAppsArray = JSONArray()
        val blockedPackageNamesArray = JSONArray()
        blockedApps.forEach { app ->
            blockedPackageNamesArray.put(app.packageName)
            val obj = JSONObject().apply {
                put("packageName", app.packageName)
                put("appName", app.appName)
                put("dateAdded", app.dateAdded)
            }
            blockedAppsArray.put(obj)
        }
        root.put("blockedApps", blockedAppsArray)
        root.put("blockedPackageNames", blockedPackageNamesArray)

        // 2. Screen Time Limits & Per-Session Limits (seconds + minutes for cross-version compatibility)
        val limits = dao.getAllScreenTimeLimitsSync()
        val limitsArray = JSONArray()
        val sessionsArray = JSONArray()
        limits.forEach { limit ->
            val limitMins = limit.dailyLimitMinutes
            val sessionMins = limit.sessionLimitSeconds / 60
            val cooldownMins = limit.cooldownSeconds / 60
            val obj = JSONObject().apply {
                put("packageName", limit.packageName)
                put("dailyLimitMinutes", limit.dailyLimitMinutes)
                put("limitMinutes", limitMins)
                put("dailyLimitSeconds", limit.dailyLimitMinutes * 60)
                put("usedMinutesToday", limit.usedMinutesToday)
                put("lastResetDate", limit.lastResetDate)
                put("sessionLimitSeconds", limit.sessionLimitSeconds)
                put("cooldownSeconds", limit.cooldownSeconds)
                put("sessionCooldownSeconds", limit.cooldownSeconds)
                put("sessionMinutes", sessionMins)
                put("cooldownMinutes", cooldownMins)
                put("currentSessionUsedMs", limit.currentSessionUsedMs)
                put("lastSessionExitTimestamp", limit.lastSessionExitTimestamp)
                put("cooldownEndTimestamp", limit.cooldownEndTimestamp)
            }
            limitsArray.put(obj)
            if (limit.sessionLimitSeconds > 0 || limit.cooldownSeconds > 0) {
                sessionsArray.put(JSONObject(obj.toString()))
            }
        }
        root.put("screenTimeLimits", limitsArray)
        root.put("appLimits", limitsArray)
        root.put("sessions", sessionsArray)

        // 3. App Groups & Group Apps (scheduleStart/scheduleEnd + parsed startHour/startMinute/endHour/endMinute + memberPackages)
        val groups = dao.getAllGroupsSync()
        val groupApps = dao.getAllGroupAppsSync()
        val membersByGroupId = groupApps.groupBy({ it.groupId }, { it.packageName })

        val groupsArray = JSONArray()
        groups.forEach { group ->
            val (startH, startM) = parseScheduleHourMinute(group.scheduleStart, 9, 0)
            val (endH, endM) = parseScheduleHourMinute(group.scheduleEnd, 17, 0)
            val memberPkgs = membersByGroupId[group.id] ?: emptyList()

            val obj = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("budgetEnabled", group.budgetEnabled)
                put("dailyBudgetMinutes", group.dailyBudgetMinutes)
                put("dailyBudgetSeconds", group.dailyBudgetMinutes * 60)
                put("usedMinutesToday", group.usedMinutesToday)
                put("lastResetDate", group.lastResetDate)
                put("scheduleEnabled", group.scheduleEnabled)
                put("scheduleStart", group.scheduleStart)
                put("scheduleEnd", group.scheduleEnd)
                put("startHour", startH)
                put("startMinute", startM)
                put("endHour", endH)
                put("endMinute", endM)
                put("daysOfWeek", JSONArray(group.daysOfWeek.toList()))
                put("sessionLimitEnabled", group.sessionLimitEnabled)
                put("sessionLimitSeconds", group.sessionLimitSeconds)
                put("cooldownSeconds", group.cooldownSeconds)
                put("sessionCooldownSeconds", group.cooldownSeconds)
                put("currentSessionUsedMs", group.currentSessionUsedMs)
                put("lastSessionExitTimestamp", group.lastSessionExitTimestamp)
                put("cooldownEndTimestamp", group.cooldownEndTimestamp)
                put("memberPackages", JSONArray(memberPkgs))
            }
            groupsArray.put(obj)
        }
        root.put("appGroups", groupsArray)
        root.put("groups", groupsArray)

        val groupAppsArray = JSONArray()
        groupApps.forEach { ga ->
            val obj = JSONObject().apply {
                put("groupId", ga.groupId)
                put("packageName", ga.packageName)
            }
            groupAppsArray.put(obj)
        }
        root.put("groupApps", groupAppsArray)

        // 4. Blocked Websites
        val websites = dao.getAllBlockedWebsitesSync()
        val websitesArray = JSONArray()
        websites.forEach { site ->
            val obj = JSONObject().apply {
                put("id", site.id)
                put("domainOrUrl", site.domainOrUrl)
                put("url", site.domainOrUrl)
                put("dateAdded", site.dateAdded)
            }
            websitesArray.put(obj)
        }
        root.put("blockedWebsites", websitesArray)
        root.put("websites", websitesArray)

        // 5. Blocked Keywords
        val keywords = dao.getAllBlockedKeywordsSync()
        val keywordsArray = JSONArray()
        keywords.forEach { kw ->
            val obj = JSONObject().apply {
                put("id", kw.id)
                put("keyword", kw.keyword)
                put("caseSensitive", kw.caseSensitive)
                put("dateAdded", kw.dateAdded)
            }
            keywordsArray.put(obj)
        }
        root.put("blockedKeywords", keywordsArray)
        root.put("keywords", keywordsArray)

        // 6. All DailyUsageLog rows (30-Day Screen Time & App History)
        val usageLogs = dao.getAllDailyUsageLogsSync()
        val usageLogsArray = JSONArray()
        usageLogs.forEach { log ->
            val obj = JSONObject().apply {
                put("packageName", log.packageName)
                put("date", log.date)
                put("minutesUsed", log.minutesUsed)
                put("appName", log.appName)
                if (!log.iconBase64.isNullOrEmpty()) {
                    put("iconBase64", log.iconBase64)
                }
            }
            usageLogsArray.put(obj)
        }
        root.put("dailyUsageLogs", usageLogsArray)

        // 7. All DailyScreenTime rows (30-Day Physical Screen-On History)
        val screenTimes = dao.getAllDailyScreenTimesSync()
        val screenTimesArray = JSONArray()
        screenTimes.forEach { item ->
            val obj = JSONObject().apply {
                put("date", item.date)
                put("screenOnMinutes", item.screenOnMinutes)
            }
            screenTimesArray.put(obj)
        }
        root.put("dailyScreenTimes", screenTimesArray)

        // 8. Preferences & Theme Snapshot
        val prefsSnapshot = preferencesRepository.getPreferencesSnapshot()
        val prefsObj = JSONObject()
        prefsSnapshot.forEach { (k, v) ->
            if (v != null) prefsObj.put(k, v)
        }
        root.put("preferences", prefsObj)
        root.put("theme", JSONObject().apply {
            prefsSnapshot["base_theme_mode"]?.let {
                put("base_theme_mode", it)
                put("baseThemeMode", it)
            }
            prefsSnapshot["secondary_palette"]?.let {
                put("secondary_palette", it)
                put("secondaryPalette", it)
            }
            prefsSnapshot["theme_mode"]?.let {
                put("theme_mode", it)
                put("themeMode", it)
            }
        })

        // 9. Complete SharedPreferences Vault (every app XML file in shared_prefs)
        val allSharedPrefsMap = collectAllSharedPreferencesMap(context)
        val sharedPrefsJson = JSONObject()
        for ((fileName, entries) in allSharedPrefsMap) {
            val fileJson = JSONObject()
            for ((k, v) in entries) {
                fileJson.put(k, v)
            }
            sharedPrefsJson.put(fileName, fileJson)
        }
        root.put("sharedPreferencesVault", sharedPrefsJson)
        root.put("sharedPreferences", sharedPrefsJson)

        root.toString(2)
    }

    /**
     * Collects the complete structured Map for Firestore storage.
     */
    private suspend fun collectFullStateMap(): Map<String, Any?> = withContext(Dispatchers.IO) {
        val blockedApps = dao.getAllBlockedAppsSync().map { app ->
            mapOf(
                "packageName" to app.packageName,
                "appName" to app.appName,
                "dateAdded" to app.dateAdded
            )
        }
        val blockedPackageNames = dao.getAllBlockedPackageNamesSync()

        val limits = dao.getAllScreenTimeLimitsSync().map { limit ->
            mapOf(
                "packageName" to limit.packageName,
                "dailyLimitMinutes" to limit.dailyLimitMinutes,
                "limitMinutes" to limit.dailyLimitMinutes,
                "dailyLimitSeconds" to (limit.dailyLimitMinutes * 60),
                "usedMinutesToday" to limit.usedMinutesToday,
                "lastResetDate" to limit.lastResetDate,
                "sessionLimitSeconds" to limit.sessionLimitSeconds,
                "cooldownSeconds" to limit.cooldownSeconds,
                "sessionCooldownSeconds" to limit.cooldownSeconds,
                "sessionMinutes" to (limit.sessionLimitSeconds / 60),
                "cooldownMinutes" to (limit.cooldownSeconds / 60),
                "currentSessionUsedMs" to limit.currentSessionUsedMs,
                "lastSessionExitTimestamp" to limit.lastSessionExitTimestamp,
                "cooldownEndTimestamp" to limit.cooldownEndTimestamp
            )
        }

        val groupApps = dao.getAllGroupAppsSync()
        val membersByGroupId = groupApps.groupBy({ it.groupId }, { it.packageName })

        val groups = dao.getAllGroupsSync().map { group ->
            val (startH, startM) = parseScheduleHourMinute(group.scheduleStart, 9, 0)
            val (endH, endM) = parseScheduleHourMinute(group.scheduleEnd, 17, 0)
            mapOf(
                "id" to group.id,
                "name" to group.name,
                "budgetEnabled" to group.budgetEnabled,
                "dailyBudgetMinutes" to group.dailyBudgetMinutes,
                "dailyBudgetSeconds" to (group.dailyBudgetMinutes * 60),
                "usedMinutesToday" to group.usedMinutesToday,
                "lastResetDate" to group.lastResetDate,
                "scheduleEnabled" to group.scheduleEnabled,
                "scheduleStart" to group.scheduleStart,
                "scheduleEnd" to group.scheduleEnd,
                "startHour" to startH,
                "startMinute" to startM,
                "endHour" to endH,
                "endMinute" to endM,
                "daysOfWeek" to group.daysOfWeek.toList(),
                "sessionLimitEnabled" to group.sessionLimitEnabled,
                "sessionLimitSeconds" to group.sessionLimitSeconds,
                "cooldownSeconds" to group.cooldownSeconds,
                "sessionCooldownSeconds" to group.cooldownSeconds,
                "currentSessionUsedMs" to group.currentSessionUsedMs,
                "lastSessionExitTimestamp" to group.lastSessionExitTimestamp,
                "cooldownEndTimestamp" to group.cooldownEndTimestamp,
                "memberPackages" to (membersByGroupId[group.id] ?: emptyList())
            )
        }

        val groupAppsMaps = groupApps.map { ga ->
            mapOf(
                "groupId" to ga.groupId,
                "packageName" to ga.packageName
            )
        }

        val websites = dao.getAllBlockedWebsitesSync().map { site ->
            mapOf(
                "id" to site.id,
                "domainOrUrl" to site.domainOrUrl,
                "url" to site.domainOrUrl,
                "dateAdded" to site.dateAdded
            )
        }

        val keywords = dao.getAllBlockedKeywordsSync().map { kw ->
            mapOf(
                "id" to kw.id,
                "keyword" to kw.keyword,
                "caseSensitive" to kw.caseSensitive,
                "dateAdded" to kw.dateAdded
            )
        }

        val usageLogs = dao.getAllDailyUsageLogsSync().map { log ->
            mapOf(
                "packageName" to log.packageName,
                "date" to log.date,
                "minutesUsed" to log.minutesUsed,
                "appName" to log.appName
            )
        }

        val dailyScreenTimes = dao.getAllDailyScreenTimesSync().map { st ->
            mapOf(
                "date" to st.date,
                "screenOnMinutes" to st.screenOnMinutes
            )
        }

        val prefsSnapshot = preferencesRepository.getPreferencesSnapshot()
        val allSharedPrefsMap = collectAllSharedPreferencesMap(context)
        val now = TimeIntegrityManager.getTrueTimeMillis()

        mapOf(
            "version" to BACKUP_VERSION,
            "timestamp" to now,
            "lastUpdated" to now,
            "blockedApps" to blockedApps,
            "blockedPackageNames" to blockedPackageNames,
            "screenTimeLimits" to limits,
            "appLimits" to limits,
            "appGroups" to groups,
            "groups" to groups,
            "groupApps" to groupAppsMaps,
            "blockedWebsites" to websites,
            "websites" to websites,
            "blockedKeywords" to keywords,
            "keywords" to keywords,
            "dailyUsageLogs" to usageLogs,
            "dailyScreenTimes" to dailyScreenTimes,
            "preferences" to prefsSnapshot,
            "theme" to mapOf(
                "base_theme_mode" to prefsSnapshot["base_theme_mode"],
                "secondary_palette" to prefsSnapshot["secondary_palette"],
                "theme_mode" to prefsSnapshot["theme_mode"]
            ),
            "sharedPreferencesVault" to allSharedPrefsMap,
            "sharedPreferences" to allSharedPrefsMap
        )
    }

    /**
     * Restores 100% of app state from a JSON string with independent `runCatching` blocks per section
     * so schema additions/removals across 5+ years never cause restore failures.
     */
    suspend fun restoreFromBackupJson(
        jsonString: String,
        clearExisting: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val payload = root.optJSONObject("focus_lock_data") ?: root

            // 1. Restore all SharedPreferences XML files first via .commit()
            runCatching {
                val vaultJson = payload.optJSONObject("sharedPreferencesVault")
                    ?: payload.optJSONObject("sharedPreferences")
                    ?: root.optJSONObject("preferencesVault")
                if (vaultJson != null) {
                    val map = mutableMapOf<String, Any?>()
                    val keys = vaultJson.keys()
                    while (keys.hasNext()) {
                        val fileName = keys.next()
                        map[fileName] = vaultJson.optJSONObject(fileName)
                    }
                    restoreFromSharedPreferencesMap(
                        targetContext = context,
                        vaultData = map,
                        wipeExistingBeforeRestore = clearExisting
                    )
                }
            }

            // 2. Restore explicit preferences / theme object
            runCatching {
                val prefsObj = payload.optJSONObject("preferences") ?: payload.optJSONObject("theme")
                if (prefsObj != null) {
                    val normalizedPrefs = JSONObject(prefsObj.toString())
                    if (!normalizedPrefs.has("base_theme_mode") && normalizedPrefs.has("baseThemeMode")) {
                        normalizedPrefs.put("base_theme_mode", normalizedPrefs.optString("baseThemeMode"))
                    }
                    if (!normalizedPrefs.has("secondary_palette") && normalizedPrefs.has("secondaryPalette")) {
                        normalizedPrefs.put("secondary_palette", normalizedPrefs.optString("secondaryPalette"))
                    }
                    preferencesRepository.restorePreferencesSync(normalizedPrefs)
                }
            }

            // 3. Restore Room Database tables inside a transaction, with independent runCatching per table
            database.withTransaction {
                if (clearExisting) {
                    database.clearAllTables()
                }

                // 3a. Blocked Apps (never filter out apps that aren't currently installed!)
                runCatching {
                    val insertedPkgs = mutableSetOf<String>()
                    val blockedAppsArray = payload.optJSONArray("blockedApps")
                    if (blockedAppsArray != null) {
                        for (i in 0 until blockedAppsArray.length()) {
                            val item = blockedAppsArray.opt(i)
                            when (item) {
                                is JSONObject -> {
                                    val pkg = item.optString("packageName", "").trim()
                                    if (pkg.isNotEmpty()) {
                                        val name = item.optString("appName", pkg).ifBlank { pkg }
                                        val dateAdded = item.optLong("dateAdded", System.currentTimeMillis())
                                        dao.insertBlockedApp(
                                            BlockedApp(
                                                packageName = pkg,
                                                appName = name,
                                                dateAdded = dateAdded
                                            )
                                        )
                                        insertedPkgs.add(pkg)
                                    }
                                }
                                is String -> {
                                    val pkg = item.trim()
                                    if (pkg.isNotEmpty()) {
                                        dao.insertBlockedApp(
                                            BlockedApp(
                                                packageName = pkg,
                                                appName = resolveAppLabelFallback(pkg),
                                                dateAdded = System.currentTimeMillis()
                                            )
                                        )
                                        insertedPkgs.add(pkg)
                                    }
                                }
                            }
                        }
                    }

                    val rawPackagesArray = payload.optJSONArray("blockedPackageNames")
                    if (rawPackagesArray != null) {
                        for (i in 0 until rawPackagesArray.length()) {
                            val pkg = rawPackagesArray.optString(i, "").trim()
                            if (pkg.isNotEmpty() && pkg !in insertedPkgs) {
                                dao.insertBlockedApp(
                                    BlockedApp(
                                        packageName = pkg,
                                        appName = resolveAppLabelFallback(pkg),
                                        dateAdded = System.currentTimeMillis()
                                    )
                                )
                                insertedPkgs.add(pkg)
                            }
                        }
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring blockedApps section", e) }

                // 3b. Screen Time Limits & Per-Session Limits
                runCatching {
                    val limitsArray = payload.optJSONArray("screenTimeLimits")
                        ?: payload.optJSONArray("appLimits")
                    val sessionsArray = payload.optJSONArray("sessions")

                    val mergedByPkg = linkedMapOf<String, JSONObject>()
                    if (limitsArray != null) {
                        for (i in 0 until limitsArray.length()) {
                            val obj = limitsArray.optJSONObject(i) ?: continue
                            val pkg = obj.optString("packageName", "").trim()
                            if (pkg.isNotEmpty()) {
                                mergedByPkg[pkg] = JSONObject(obj.toString())
                            }
                        }
                    }
                    if (sessionsArray != null) {
                        for (i in 0 until sessionsArray.length()) {
                            val obj = sessionsArray.optJSONObject(i) ?: continue
                            val pkg = obj.optString("packageName", "").trim()
                            if (pkg.isNotEmpty()) {
                                val existing = mergedByPkg[pkg]
                                if (existing != null) {
                                    val keys = obj.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        existing.put(k, obj.opt(k))
                                    }
                                } else {
                                    mergedByPkg[pkg] = JSONObject(obj.toString())
                                }
                            }
                        }
                    }

                    val todayStr = TimeIntegrityManager.getTrueDateString()
                    for ((pkg, obj) in mergedByPkg) {
                        val dailyMins = obj.optInt("dailyLimitMinutes", obj.optInt("limitMinutes", 0))
                        val dailySecs = obj.optInt("dailyLimitSeconds", dailyMins * 60)
                        val sessionMins = obj.optInt("sessionMinutes", 0)
                        val sessionSecs = obj.optInt("sessionLimitSeconds", sessionMins * 60)
                        val cooldownMins = obj.optInt("cooldownMinutes", 0)
                        val cooldownSecs = obj.optInt(
                            "cooldownSeconds",
                            obj.optInt("sessionCooldownSeconds", cooldownMins * 60)
                        )
                        val resetDate = obj.optString("lastResetDate", todayStr)
                        val usedToday = if (resetDate == todayStr) obj.optInt("usedMinutesToday", 0) else 0

                        dao.insertOrUpdateScreenTimeLimit(
                            ScreenTimeLimit(
                                packageName = pkg,
                                dailyLimitMinutes = if (dailyMins > 0) dailyMins else (dailySecs / 60),
                                usedMinutesToday = usedToday,
                                lastResetDate = todayStr,
                                sessionLimitSeconds = sessionSecs,
                                cooldownSeconds = cooldownSecs,
                                currentSessionUsedMs = obj.optLong("currentSessionUsedMs", 0L),
                                lastSessionExitTimestamp = obj.optLong("lastSessionExitTimestamp", 0L),
                                cooldownEndTimestamp = obj.optLong("cooldownEndTimestamp", 0L)
                            )
                        )
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring screenTimeLimits section", e) }

                // 3c. App Groups & Group Apps
                runCatching {
                    val groupsArray = payload.optJSONArray("appGroups") ?: payload.optJSONArray("groups")
                    val explicitGroupApps = payload.optJSONArray("groupApps")
                    val todayStr = TimeIntegrityManager.getTrueDateString()

                    if (groupsArray != null) {
                        for (i in 0 until groupsArray.length()) {
                            val obj = groupsArray.optJSONObject(i) ?: continue
                            val daysArray = obj.optJSONArray("daysOfWeek")
                            val daysSet = mutableSetOf<Int>()
                            if (daysArray != null) {
                                for (j in 0 until daysArray.length()) {
                                    daysSet.add(daysArray.optInt(j, 1))
                                }
                            } else {
                                daysSet.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
                            }

                            val startHour = obj.optInt("startHour", 9)
                            val startMinute = obj.optInt("startMinute", 0)
                            val endHour = obj.optInt("endHour", 17)
                            val endMinute = obj.optInt("endMinute", 0)

                            val scheduleStart = obj.optString("scheduleStart", "").ifBlank {
                                formatHourMinute(startHour, startMinute)
                            }
                            val scheduleEnd = obj.optString("scheduleEnd", "").ifBlank {
                                formatHourMinute(endHour, endMinute)
                            }

                            val dailyBudgetMins = obj.optInt("dailyBudgetMinutes", 60)
                            val resetDate = obj.optString("lastResetDate", todayStr)
                            val usedToday = if (resetDate == todayStr) obj.optInt("usedMinutesToday", 0) else 0
                            val groupId = obj.optLong("id", 0L)
                            val sessionSecs = obj.optInt("sessionLimitSeconds", 0)
                            val cooldownSecs = obj.optInt(
                                "cooldownSeconds",
                                obj.optInt("sessionCooldownSeconds", 0)
                            )
                            val sessionEnabled = obj.optBoolean("sessionLimitEnabled", sessionSecs > 0)

                            val insertedGroupId = dao.insertGroup(
                                AppGroup(
                                    id = groupId,
                                    name = obj.optString("name", "Group"),
                                    budgetEnabled = obj.optBoolean("budgetEnabled", false),
                                    dailyBudgetMinutes = dailyBudgetMins,
                                    usedMinutesToday = usedToday,
                                    lastResetDate = todayStr,
                                    scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
                                    scheduleStart = scheduleStart,
                                    scheduleEnd = scheduleEnd,
                                    daysOfWeek = daysSet,
                                    sessionLimitEnabled = sessionEnabled,
                                    sessionLimitSeconds = sessionSecs,
                                    cooldownSeconds = cooldownSecs,
                                    currentSessionUsedMs = obj.optLong("currentSessionUsedMs", 0L),
                                    lastSessionExitTimestamp = obj.optLong("lastSessionExitTimestamp", 0L),
                                    cooldownEndTimestamp = obj.optLong("cooldownEndTimestamp", 0L)
                                )
                            )

                            val targetGroupId = if (groupId != 0L) groupId else insertedGroupId
                            val memberPkgsArray = obj.optJSONArray("memberPackages")
                            if (memberPkgsArray != null && memberPkgsArray.length() > 0) {
                                val memberEntities = mutableListOf<GroupApp>()
                                for (m in 0 until memberPkgsArray.length()) {
                                    val pkg = memberPkgsArray.optString(m, "").trim()
                                    if (pkg.isNotEmpty()) {
                                        memberEntities.add(GroupApp(groupId = targetGroupId, packageName = pkg))
                                    }
                                }
                                if (memberEntities.isNotEmpty()) {
                                    dao.insertGroupApps(memberEntities)
                                }
                            }
                        }
                    }

                    if (explicitGroupApps != null) {
                        val list = mutableListOf<GroupApp>()
                        for (i in 0 until explicitGroupApps.length()) {
                            val obj = explicitGroupApps.optJSONObject(i) ?: continue
                            val gId = obj.optLong("groupId", 0L)
                            val pkg = obj.optString("packageName", "").trim()
                            if (gId != 0L && pkg.isNotEmpty()) {
                                list.add(GroupApp(groupId = gId, packageName = pkg))
                            }
                        }
                        if (list.isNotEmpty()) {
                            dao.insertGroupApps(list)
                        }
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring appGroups section", e) }

                // 3d. Blocked Websites
                runCatching {
                    val websitesArray = payload.optJSONArray("blockedWebsites")
                        ?: payload.optJSONArray("websites")
                    if (websitesArray != null) {
                        for (i in 0 until websitesArray.length()) {
                            val item = websitesArray.opt(i)
                            when (item) {
                                is JSONObject -> {
                                    val domain = item.optString("domainOrUrl", item.optString("url", "")).trim()
                                    if (domain.isNotEmpty()) {
                                        dao.insertBlockedWebsite(
                                            BlockedWebsite(
                                                id = item.optLong("id", 0L),
                                                domainOrUrl = domain,
                                                dateAdded = item.optLong("dateAdded", System.currentTimeMillis())
                                            )
                                        )
                                    }
                                }
                                is String -> {
                                    val domain = item.trim()
                                    if (domain.isNotEmpty()) {
                                        dao.insertBlockedWebsite(BlockedWebsite(domainOrUrl = domain))
                                    }
                                }
                            }
                        }
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring blockedWebsites section", e) }

                // 3e. Blocked Keywords
                runCatching {
                    val keywordsArray = payload.optJSONArray("blockedKeywords")
                        ?: payload.optJSONArray("keywords")
                    if (keywordsArray != null) {
                        for (i in 0 until keywordsArray.length()) {
                            val item = keywordsArray.opt(i)
                            when (item) {
                                is JSONObject -> {
                                    val kw = item.optString("keyword", "").trim()
                                    if (kw.isNotEmpty()) {
                                        dao.insertBlockedKeyword(
                                            BlockedKeyword(
                                                id = item.optLong("id", 0L),
                                                keyword = kw,
                                                caseSensitive = item.optBoolean("caseSensitive", false),
                                                dateAdded = item.optLong("dateAdded", System.currentTimeMillis())
                                            )
                                        )
                                    }
                                }
                                is String -> {
                                    val kw = item.trim()
                                    if (kw.isNotEmpty()) {
                                        dao.insertBlockedKeyword(BlockedKeyword(keyword = kw))
                                    }
                                }
                            }
                        }
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring blockedKeywords section", e) }

                // 3f. Daily Usage Logs (merge with maxOf(local, cloud) so valid dwell time is never lost)
                runCatching {
                    val usageLogsArray = payload.optJSONArray("dailyUsageLogs")
                    if (usageLogsArray != null) {
                        for (i in 0 until usageLogsArray.length()) {
                            val obj = usageLogsArray.optJSONObject(i) ?: continue
                            val pkg = obj.optString("packageName", "").trim()
                            val date = obj.optString("date", "").trim()
                            val cloudMinutes = obj.optInt("minutesUsed", 0)
                            if (pkg.isNotEmpty() && date.isNotEmpty() && cloudMinutes >= 0) {
                                val existing = dao.getUsageLog(pkg, date)
                                val mergedMinutes = maxOf(existing?.minutesUsed ?: 0, cloudMinutes)
                                val appName = obj.optString("appName", existing?.appName ?: pkg).ifBlank { pkg }
                                val iconBase64 = obj.optString("iconBase64", "").ifEmpty { existing?.iconBase64 }
                                dao.insertOrUpdateDailyUsageLog(
                                    DailyUsageLog(
                                        packageName = pkg,
                                        date = date,
                                        minutesUsed = mergedMinutes,
                                        appName = appName,
                                        iconBase64 = iconBase64
                                    )
                                )
                            }
                        }
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring dailyUsageLogs section", e) }

                // 3g. Daily Screen-On Time (merge with maxOf(local, cloud), rejecting >= 18h spikes)
                runCatching {
                    val screenTimesArray = payload.optJSONArray("dailyScreenTimes")
                    val maxValidDailyMins = 18 * 60
                    if (screenTimesArray != null) {
                        for (i in 0 until screenTimesArray.length()) {
                            val obj = screenTimesArray.optJSONObject(i) ?: continue
                            val date = obj.optString("date", "").trim()
                            val rawCloudScreenOn = obj.optInt("screenOnMinutes", 0)
                            val cloudScreenOn = if (rawCloudScreenOn >= maxValidDailyMins) 0 else rawCloudScreenOn
                            if (date.isNotEmpty() && cloudScreenOn >= 0) {
                                val rawLocalScreenOn = dao.getScreenOnMinutesForDateSync(date) ?: 0
                                val localScreenOn = if (rawLocalScreenOn >= maxValidDailyMins) 0 else rawLocalScreenOn
                                val merged = maxOf(localScreenOn, cloudScreenOn)
                                dao.insertOrUpdateScreenOnTime(
                                    DailyScreenTime(date = date, screenOnMinutes = merged)
                                )
                            }
                        }
                    }
                }.onFailure { e -> Log.w(TAG, "Non-fatal error restoring dailyScreenTimes section", e) }
            }

            // 4. Immediately reload all in-memory state from disk
            reloadAllAppStateFromDisk(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from backup JSON", e)
            false
        }
    }

    private fun resolveAppLabelFallback(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    /**
     * Restores from a Firestore document map.
     * Prefers the lossless `vaultJson` string if present, and also applies `preferencesVault`
     * and `focus_lock_data` maps for complete forward/backward compatibility.
     */
    internal suspend fun restoreFromStateMap(
        data: Map<String, Any?>,
        clearExisting: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. If preferencesVault is stored at top level of Firestore doc, apply it first
            val topLevelPrefsVault = data["preferencesVault"] as? Map<*, *>
            if (topLevelPrefsVault != null) {
                @Suppress("UNCHECKED_CAST")
                restoreFromSharedPreferencesMap(
                    targetContext = context,
                    vaultData = topLevelPrefsVault as Map<String, Any?>,
                    wipeExistingBeforeRestore = clearExisting
                )
            }

            // 2. If lossless vaultJson string is present, restore from it directly
            val vaultJsonStr = data["vaultJson"] as? String
            if (!vaultJsonStr.isNullOrBlank()) {
                val restored = restoreFromBackupJson(
                    jsonString = vaultJsonStr,
                    clearExisting = clearExisting
                )
                if (restored) return@withContext true
            }

            // 3. Fallback: convert structured Firestore map to JSONObject and restore via resilient parser
            val payload = (data["focus_lock_data"] as? Map<*, *>) ?: data
            val jsonObject = mapToJsonObject(payload)
            restoreFromBackupJson(jsonObject.toString(), clearExisting = clearExisting)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from Firestore state map", e)
            false
        }
    }

    private fun mapToJsonObject(map: Map<*, *>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) {
            val key = k as? String ?: continue
            obj.put(key, anyToJsonValue(v))
        }
        return obj
    }

    private fun anyToJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> mapToJsonObject(value)
            is Iterable<*> -> {
                val arr = JSONArray()
                for (item in value) {
                    arr.put(anyToJsonValue(item))
                }
                arr
            }
            is Array<*> -> {
                val arr = JSONArray()
                for (item in value) {
                    arr.put(anyToJsonValue(item))
                }
                arr
            }
            else -> value
        }
    }

    suspend fun exportToUri(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = createBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open output stream"))
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open input stream"))

            val ok = restoreFromBackupJson(stringBuilder.toString(), clearExisting = false)
            if (ok && isRestoreCompletedForCurrentSession) {
                uploadToCloudImmediate()
            }
            if (ok) Result.success(true) else Result.failure(IllegalStateException("Invalid backup format"))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            Result.failure(e)
        }
    }

    private suspend fun getLocalUserRuleCountSync(): Int {
        return dao.getAllBlockedAppsSync().size +
                dao.getAllScreenTimeLimitsSync().size +
                dao.getAllGroupsSync().size +
                dao.getAllBlockedWebsitesSync().size +
                dao.getAllBlockedKeywordsSync().size
    }

    private suspend fun hasAnyLocalUserData(): Boolean {
        if (getLocalUserRuleCountSync() > 0) return true
        val prefs = preferencesRepository.getPreferencesSnapshot()
        val hasCustomTheme = prefs["base_theme_mode"] != null || prefs["secondary_palette"] != null
        val hasCheatProtection = (prefs["cheat_protection_enabled"] as? Boolean) == true
        val hasInvincible = (prefs["invincible_mode_enabled"] as? Boolean) == true
        return hasCustomTheme || hasCheatProtection || hasInvincible
    }

    private fun extractCloudRuleCount(data: Map<String, Any?>?): Int {
        if (data == null) return 0
        val vaultJson = data["vaultJson"] as? String
        if (!vaultJson.isNullOrBlank()) {
            val summary = parseCrossVersionBackupSummary(vaultJson)
            return summary.blockedPackageNames.size +
                    summary.limitsCount +
                    summary.groupsCount +
                    summary.websitesCount +
                    summary.keywordsCount
        }
        val payload = (data["focus_lock_data"] as? Map<*, *>) ?: data
        val blockedCount = (payload["blockedApps"] as? List<*>)?.size
            ?: (payload["blockedPackageNames"] as? List<*>)?.size ?: 0
        val limitsCount = (payload["screenTimeLimits"] as? List<*>)?.size
            ?: (payload["appLimits"] as? List<*>)?.size ?: 0
        val groupsCount = (payload["appGroups"] as? List<*>)?.size
            ?: (payload["groups"] as? List<*>)?.size ?: 0
        val websitesCount = (payload["blockedWebsites"] as? List<*>)?.size
            ?: (payload["websites"] as? List<*>)?.size ?: 0
        val keywordsCount = (payload["blockedKeywords"] as? List<*>)?.size
            ?: (payload["keywords"] as? List<*>)?.size ?: 0
        return blockedCount + limitsCount + groupsCount + websitesCount + keywordsCount
    }

    private fun hasCloudUserData(data: Map<String, Any?>?): Boolean {
        if (data == null) return false
        if (extractCloudRuleCount(data) > 0) return true
        val prefsVault = data["preferencesVault"] as? Map<*, *>
        if (!prefsVault.isNullOrEmpty()) return true
        val payload = (data["focus_lock_data"] as? Map<*, *>) ?: data
        val usageCount = (payload["dailyUsageLogs"] as? List<*>)?.size ?: 0
        val screenTimeCount = (payload["dailyScreenTimes"] as? List<*>)?.size ?: 0
        val prefs = payload["preferences"] as? Map<*, *>
        val hasPrefs = prefs != null && (
                prefs["base_theme_mode"] != null ||
                prefs["secondary_palette"] != null ||
                prefs["cheat_protection_enabled"] == true ||
                prefs["invincible_mode_enabled"] == true
            )
        return usageCount > 0 || screenTimeCount > 0 || hasPrefs
    }

    private fun reportFirestoreFailure(operation: String, path: String, e: Throwable) {
        val details = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message ?: "Unknown Firestore error"}"
        Log.e(TAG, "Firestore $operation failed at [$path] -> $details", e)
        val userMessage = "$operation failed: ${e.localizedMessage ?: e.message ?: e.javaClass.simpleName}"
        _lastSyncErrorFlow.value = userMessage
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                Toast.makeText(context.applicationContext, userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Strictly verifies and returns `FirebaseAuth.getInstance().currentUser?.uid`.
     * Returns null if unauthenticated or blank.
     */
    private fun getVerifiedFirebaseUid(): String? {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) uid else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read FirebaseAuth.getInstance().currentUser?.uid", e)
            null
        }
    }

    /**
     * Ensures that `FirebaseAuth.getInstance().currentUser` is authenticated using a Google ID Token
     * before any Firestore read or write operation is attempted.
     */
    private suspend fun ensureFirebaseAuthSessionIfPossible(idToken: String? = null) {
        // 1. If a fresh Google ID Token was explicitly provided, authenticate with FirebaseAuth
        if (!idToken.isNullOrBlank()) {
            try {
                withTimeoutOrNull(6_000L) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance()
                        .signInWithCredential(credential)
                        .addOnFailureListener { e ->
                            reportFirestoreFailure("Firebase Auth Sign-In", "GoogleAuthProvider.signInWithCredential", e)
                        }
                        .await()
                }
                if (getVerifiedFirebaseUid() != null) return
            } catch (e: Exception) {
                Log.e(TAG, "FirebaseAuth signInWithCredential failed for provided idToken: ${e.message}", e)
            }
        }

        // 2. If already authenticated with a non-null UID, return immediately
        if (getVerifiedFirebaseUid() != null) return

        // 3. Otherwise, attempt to retrieve idToken from GoogleSignIn last account or silentSignIn
        try {
            val lastAcctToken = runCatching { GoogleSignIn.getLastSignedInAccount(context)?.idToken }.getOrNull()
            val tokenToUse = if (!lastAcctToken.isNullOrBlank()) {
                lastAcctToken
            } else {
                val webClientId = getDefaultWebClientId(context)
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile()
                    .requestIdToken(webClientId)
                    .build()
                val silentAccount = withTimeoutOrNull(5_000L) {
                    GoogleSignIn.getClient(context, gso)
                        .silentSignIn()
                        .addOnFailureListener { e ->
                            Log.w(TAG, "GoogleSignIn silentSignIn failed: ${e.message}", e)
                        }
                        .await()
                }
                silentAccount?.idToken
            }

            if (!tokenToUse.isNullOrBlank()) {
                withTimeoutOrNull(6_000L) {
                    val credential = GoogleAuthProvider.getCredential(tokenToUse, null)
                    FirebaseAuth.getInstance()
                        .signInWithCredential(credential)
                        .addOnFailureListener { e ->
                            reportFirestoreFailure("Firebase Auth Sign-In", "GoogleAuthProvider.silentSignIn", e)
                        }
                        .await()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not auto-refresh FirebaseAuth session: ${e.message}", e)
        }
    }

    /**
     * Uploads 100% of user data to `/user_backups/{userId}/backup_modules/{moduleId}` using a
     * Firestore `WriteBatch` so no single document ever approaches the 1MB Firestore limit.
     *
     * Modular documents written inside `/user_backups/{userId}/backup_modules/`:
     * - `metadata`
     * - `blocked_apps`
     * - `blocked_sites`
     * - `app_groups`
     * - `screen_time_limits`
     * - `preferences`
     * - `usage_history`
     */
    suspend fun syncToCloudDirect(email: String, isManual: Boolean = false): Result<Long> = withContext(Dispatchers.IO) {
        if (!isRestoreCompletedForCurrentSession) {
            Log.d(TAG, "syncToCloudDirect blocked: isRestoreCompletedForCurrentSession is false")
            return@withContext Result.failure(IllegalStateException("Cloud restore not completed for current session"))
        }
        if (isRestoringFromCloud.get()) {
            Log.d(TAG, "syncToCloudDirect blocked: restore is currently in progress")
            return@withContext Result.failure(IllegalStateException("Restore in progress"))
        }

        try {
            val normalizedEmail = email.trim().lowercase()
            if (normalizedEmail.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Email cannot be empty"))
            }

            val now = TimeIntegrityManager.getTrueTimeMillis()
            val vaultJson = createBackupJson()
            val currentHash = computeDeterministicPayloadHash(vaultJson)

            // Deduplicate identical payloads to prevent infinite upload loops
            if (!isManual && currentHash == lastUploadedJsonHash && lastUploadedJsonHash != 0) {
                return@withContext Result.success(now)
            }

            val localRuleCount = getLocalUserRuleCountSync()
            if (localRuleCount == 0 && !hasUserExplicitlyDeletedLastRuleAfterRestore && !isManual) {
                // Do not overwrite cloud with an empty uninitialized rule set during background sync
                lastUploadedJsonHash = currentHash
                return@withContext Result.success(now)
            }

            FirebaseInitializer.ensureInitialized(context)
            ensureFirebaseAuthSessionIfPossible(null)

            // Requirement 2: Strictly verify FirebaseAuth.getInstance().currentUser?.uid is non-null before writing
            val uid = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
            if (uid == null) {
                val authEx = IllegalStateException("FirebaseAuth.getInstance().currentUser?.uid is null. Please sign in with Google again.")
                reportFirestoreFailure("Cloud Backup", "/$FIRESTORE_USER_BACKUPS_COLLECTION/{null}", authEx)
                return@withContext Result.failure(authEx)
            }

            val stateMap = collectFullStateMap()
            val allSharedPrefsVault = collectAllSharedPreferencesMap(context)

            withTimeout(CLOUD_TIMEOUT_MS) {
                val firestore = FirebaseFirestore.getInstance()
                val userBackupDocRef = firestore.collection(FIRESTORE_USER_BACKUPS_COLLECTION).document(uid)
                val modulesColRef = userBackupDocRef.collection(FIRESTORE_MODULES_SUBCOLLECTION)
                val batchPath = "$FIRESTORE_USER_BACKUPS_COLLECTION/$uid/$FIRESTORE_MODULES_SUBCOLLECTION"

                val metadataDoc = mapOf(
                    "userId" to uid,
                    "uid" to uid,
                    "email" to normalizedEmail,
                    "version" to BACKUP_VERSION,
                    "backupSchemaVersion" to BACKUP_VERSION,
                    "timestamp" to now,
                    "lastUpdated" to now,
                    "updatedAt" to now,
                    "modules" to listOf(
                        DOC_BLOCKED_APPS,
                        DOC_BLOCKED_SITES,
                        DOC_APP_GROUPS,
                        DOC_SCREEN_TIME_LIMITS,
                        DOC_PREFERENCES,
                        DOC_USAGE_HISTORY
                    )
                )

                val blockedAppsDoc = mapOf(
                    "userId" to uid,
                    "updatedAt" to now,
                    "blockedApps" to (stateMap["blockedApps"] ?: emptyList<Any>()),
                    "blockedPackageNames" to (stateMap["blockedPackageNames"] ?: emptyList<String>())
                )

                val blockedSitesDoc = mapOf(
                    "userId" to uid,
                    "updatedAt" to now,
                    "blockedWebsites" to (stateMap["blockedWebsites"] ?: emptyList<Any>()),
                    "websites" to (stateMap["websites"] ?: emptyList<Any>()),
                    "blockedKeywords" to (stateMap["blockedKeywords"] ?: emptyList<Any>()),
                    "keywords" to (stateMap["keywords"] ?: emptyList<Any>())
                )

                val appGroupsDoc = mapOf(
                    "userId" to uid,
                    "updatedAt" to now,
                    "appGroups" to (stateMap["appGroups"] ?: emptyList<Any>()),
                    "groups" to (stateMap["groups"] ?: emptyList<Any>()),
                    "groupApps" to (stateMap["groupApps"] ?: emptyList<Any>())
                )

                val screenTimeLimitsDoc = mapOf(
                    "userId" to uid,
                    "updatedAt" to now,
                    "screenTimeLimits" to (stateMap["screenTimeLimits"] ?: emptyList<Any>()),
                    "appLimits" to (stateMap["appLimits"] ?: emptyList<Any>())
                )

                val preferencesDoc = mapOf(
                    "userId" to uid,
                    "updatedAt" to now,
                    "preferences" to (stateMap["preferences"] ?: emptyMap<String, Any?>()),
                    "theme" to (stateMap["theme"] ?: emptyMap<String, Any?>()),
                    "preferencesVault" to allSharedPrefsVault,
                    "sharedPreferencesVault" to allSharedPrefsVault
                )

                val usageHistoryDoc = mapOf(
                    "userId" to uid,
                    "updatedAt" to now,
                    "dailyScreenTimes" to (stateMap["dailyScreenTimes"] ?: emptyList<Any>()),
                    "dailyUsageLogs" to (stateMap["dailyUsageLogs"] ?: emptyList<Any>())
                )

                val batch = firestore.batch()
                batch.set(modulesColRef.document(DOC_METADATA), metadataDoc, SetOptions.merge())
                batch.set(modulesColRef.document(DOC_BLOCKED_APPS), blockedAppsDoc)
                batch.set(modulesColRef.document(DOC_BLOCKED_SITES), blockedSitesDoc)
                batch.set(modulesColRef.document(DOC_APP_GROUPS), appGroupsDoc)
                batch.set(modulesColRef.document(DOC_SCREEN_TIME_LIMITS), screenTimeLimitsDoc)
                batch.set(modulesColRef.document(DOC_PREFERENCES), preferencesDoc)
                batch.set(modulesColRef.document(DOC_USAGE_HISTORY), usageHistoryDoc)

                batch.commit()
                    .addOnFailureListener { e ->
                        reportFirestoreFailure("Cloud Backup WriteBatch", batchPath, e)
                    }
                    .await()
            }

            lastUploadedJsonHash = currentHash
            _lastSyncErrorFlow.value = null
            context.getSharedPreferences(AUTH_SESSION_META_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MODULAR_SCHEMA_SYNCED, true)
                .commit()
            Log.d(TAG, "Modular WriteBatch cloud backup completed for $normalizedEmail (uid=$uid) at $now")
            Result.success(now)
        } catch (e: Exception) {
            lastUploadedJsonHash = 0
            Log.e(TAG, "Cloud backup failed for $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncToCloud(uid: String, email: String?): Result<Long> {
        val targetEmail = email ?: preferencesRepository.getConnectedGmailAccountSync() ?: uid
        return syncToCloudDirect(targetEmail, isManual = true)
    }

    /**
     * Reads all modular backup documents from `/user_backups/{uid}/backup_modules/`
     * (`blocked_apps`, `blocked_sites`, `app_groups`, `screen_time_limits`, `preferences`, `usage_history`)
     * and reconstructs the unified state map for local restoration.
     * Falls back to reading flat `/user_backups/{uid}` if no subcollection documents exist yet.
     */
    private suspend fun fetchModularUserBackup(
        firestore: FirebaseFirestore,
        uid: String
    ): Map<String, Any?>? {
        val userDocRef = firestore.collection(FIRESTORE_USER_BACKUPS_COLLECTION).document(uid)
        val modulesColRef = userDocRef.collection(FIRESTORE_MODULES_SUBCOLLECTION)
        val modulesPath = "$FIRESTORE_USER_BACKUPS_COLLECTION/$uid/$FIRESTORE_MODULES_SUBCOLLECTION"

        val modulesSnapshot = try {
            withTimeoutOrNull(6_000L) {
                modulesColRef.get(Source.SERVER)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Server read failed for [$modulesPath], trying cache/default: ${e.message}", e)
                    }
                    .await()
            } ?: withTimeoutOrNull(4_000L) {
                modulesColRef.get(Source.DEFAULT)
                    .addOnFailureListener { e ->
                        reportFirestoreFailure("Cloud Restore Read", modulesPath, e)
                    }
                    .await()
            }
        } catch (e: Exception) {
            try {
                withTimeoutOrNull(4_000L) {
                    modulesColRef.get(Source.DEFAULT)
                        .addOnFailureListener { err ->
                            reportFirestoreFailure("Cloud Restore Read", modulesPath, err)
                        }
                        .await()
                }
            } catch (inner: Exception) {
                reportFirestoreFailure("Cloud Restore Read", modulesPath, inner)
                null
            }
        }

        if (modulesSnapshot != null && !modulesSnapshot.isEmpty) {
            val docsMap = mutableMapOf<String, Map<String, Any?>>()
            for (doc in modulesSnapshot.documents) {
                val docData = doc.data ?: continue
                docsMap[doc.id] = docData
            }
            val reconstructed = reconstructStateMapFromModularDocs(docsMap)
            if (hasCloudUserData(reconstructed)) {
                return reconstructed
            }
        }

        // Fallback: Check if a flat document exists directly at /user_backups/{uid}
        val flatPath = "$FIRESTORE_USER_BACKUPS_COLLECTION/$uid"
        val flatSnap = try {
            withTimeoutOrNull(3_000L) {
                userDocRef.get(Source.SERVER)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Fallback flat read failed at [$flatPath]: ${e.message}")
                    }
                    .await()
            } ?: withTimeoutOrNull(2_000L) {
                userDocRef.get(Source.DEFAULT)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Fallback default read failed at [$flatPath]: ${e.message}")
                    }
                    .await()
            }
        } catch (_: Exception) {
            null
        }

        if (flatSnap != null && flatSnap.exists()) {
            val flatData = flatSnap.data
            if (flatData != null && hasCloudUserData(flatData)) {
                return flatData
            }
        }

        return null
    }

    /**
     * Pulls the authoritative modular backup from `/user_backups/{uid}/backup_modules` ONCE per sign-in session,
     * writes all `SharedPreferences` via `.commit()`, inserts all Room entities, calls
     * `reloadAllAppStateFromDisk(context)` once, and marks `is_cloud_restored_for_session = true`.
     */
    suspend fun restoreFromCloud(
        email: String,
        isInitialStartup: Boolean = false,
        wipeBeforeRestore: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        isRestoringFromCloud.set(true)
        try {
            val normalizedEmail = email.trim().lowercase()
            if (normalizedEmail.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Email cannot be empty"))
            }

            FirebaseInitializer.ensureInitialized(context)
            ensureFirebaseAuthSessionIfPossible(null)

            // Requirement 2: Strictly verify FirebaseAuth.getInstance().currentUser?.uid is non-null before reading
            val uid = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
            if (uid == null) {
                val authEx = IllegalStateException("FirebaseAuth.getInstance().currentUser?.uid is null. Please sign in with Google again.")
                reportFirestoreFailure("Cloud Restore", "/$FIRESTORE_USER_BACKUPS_COLLECTION/{null}", authEx)
                return@withContext Result.failure(authEx)
            }

            val firestore = FirebaseFirestore.getInstance()
            val cloudData = fetchModularUserBackup(firestore, uid)

            val metaPrefs = context.getSharedPreferences(AUTH_SESSION_META_PREFS, Context.MODE_PRIVATE)

            if (cloudData != null && hasCloudUserData(cloudData)) {
                val restored = restoreFromStateMap(cloudData, clearExisting = wipeBeforeRestore)
                preferencesRepository.setConnectedGmailAccount(normalizedEmail)
                hasUserExplicitlyDeletedLastRuleAfterRestore = false
                metaPrefs.edit()
                    .putString(KEY_ACTIVE_GMAIL_EMAIL, normalizedEmail)
                    .putBoolean(KEY_IS_CLOUD_RESTORED_FOR_SESSION, true)
                    .putBoolean(KEY_MODULAR_SCHEMA_SYNCED, true)
                    .commit()
                lastUploadedJsonHash = computeDeterministicPayloadHash(createBackupJson())
                isRestoreCompletedForCurrentSession = true
                isCloudSessionReady = true
                _lastSyncErrorFlow.value = null
                reloadAllAppStateFromDisk(context)
                Result.success(restored)
            } else {
                // Cloud has no user data yet for this account: mark session ready and upload current state once
                preferencesRepository.setConnectedGmailAccount(normalizedEmail)
                hasUserExplicitlyDeletedLastRuleAfterRestore = false
                metaPrefs.edit()
                    .putString(KEY_ACTIVE_GMAIL_EMAIL, normalizedEmail)
                    .putBoolean(KEY_IS_CLOUD_RESTORED_FOR_SESSION, true)
                    .commit()
                isRestoreCompletedForCurrentSession = true
                isCloudSessionReady = true
                isRestoringFromCloud.set(false)
                syncToCloudDirect(normalizedEmail, isManual = true)
                Result.success(false)
            }
        } catch (e: Exception) {
            reportFirestoreFailure("Cloud Restore", "/$FIRESTORE_USER_BACKUPS_COLLECTION", e)
            hasUserExplicitlyDeletedLastRuleAfterRestore = false
            context.getSharedPreferences(AUTH_SESSION_META_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_CLOUD_RESTORED_FOR_SESSION, true)
                .commit()
            isRestoreCompletedForCurrentSession = true
            isCloudSessionReady = true
            Result.failure(e)
        } finally {
            isRestoringFromCloud.set(false)
            _isSyncingFlow.value = false
        }
    }

    /**
     * Connects a Gmail account, enforces strict per-Gmail account isolation (`auth_session_meta`),
     * wipes previous account's local state if switching accounts (`Account A -> Account B`),
     * restores the target account's backup ONCE from Firestore, and marks the session restored.
     */
    suspend fun connectGmailAccount(
        email: String,
        idToken: String? = null,
        displayName: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        // Step 1: Lock the upload gate immediately so no stale data can leak during sign-in / switch
        isRestoreCompletedForCurrentSession = false
        hasUserExplicitlyDeletedLastRuleAfterRestore = false

        val normalizedEmail = email.trim().lowercase()
        val previousEmail = preferencesRepository.getConnectedGmailAccountSync()?.trim()?.lowercase()

        val metaPrefs = context.getSharedPreferences(AUTH_SESSION_META_PREFS, Context.MODE_PRIVATE)
        val lastUid = metaPrefs.getString(KEY_LAST_UID, null)
            ?: metaPrefs.getString(KEY_LAST_ACTIVE_GMAIL_UID, null)

        val canonicalAccountUid = normalizedEmail

        val isSwitchingAccounts = (!lastUid.isNullOrBlank() && !lastUid.equals(canonicalAccountUid, ignoreCase = true)) ||
                (!previousEmail.isNullOrBlank() && !previousEmail.equals(normalizedEmail, ignoreCase = true))

        // Step 2: Record active Gmail and display name immediately in auth_session_meta
        metaPrefs.edit()
            .putString(KEY_LAST_UID, canonicalAccountUid)
            .putString(KEY_LAST_ACTIVE_GMAIL_UID, canonicalAccountUid)
            .putString(KEY_ACTIVE_GMAIL_EMAIL, normalizedEmail)
            .putBoolean(KEY_IS_CLOUD_RESTORED_FOR_SESSION, false)
            .apply {
                if (!displayName.isNullOrBlank()) {
                    putString(KEY_ACTIVE_DISPLAY_NAME, displayName)
                }
            }
            .commit()

        // Step 3: If switching from Account A to Account B, completely wipe Account A's local data first
        if (isSwitchingAccounts) {
            Log.i(TAG, "Account switch detected ($lastUid / $previousEmail -> $canonicalAccountUid). Wiping local state for isolation.")
            isRestoringFromCloud.set(true)
            try {
                wipeAllLocalAppSharedPreferences(context)
                preferencesRepository.clearAllLocalPreferences()
                database.clearAllTables()
                preferencesRepository.setConnectedGmailAccount(normalizedEmail)
                reloadAllAppStateFromDisk(context)
            } finally {
                isRestoringFromCloud.set(false)
            }
        } else {
            preferencesRepository.setConnectedGmailAccount(normalizedEmail)
        }

        FirebaseInitializer.ensureInitialized(context)
        ensureFirebaseAuthSessionIfPossible(idToken)

        // Step 4: Pull authoritative cloud backup ONCE for this session
        restoreFromCloud(
            email = normalizedEmail,
            isInitialStartup = false,
            wipeBeforeRestore = isSwitchingAccounts
        )
    }

    /**
     * Signs out of the current Gmail account and resets local device state to clean factory defaults
     * so Account A's private rules, theme, and history never remain on the device after sign-out
     * or leak into a subsequently connected Account B.
     */
    suspend fun disconnectGmailAccount() = withContext(Dispatchers.IO) {
        val email = preferencesRepository.getConnectedGmailAccountSync()
        if (!email.isNullOrBlank() && isRestoreCompletedForCurrentSession) {
            try {
                syncToCloudDirect(email, isManual = true)
            } catch (e: Exception) {
                Log.w(TAG, "Final sync before sign-out failed: ${e.message}")
            }
        }

        // Immediately lock upload gate and reset session state
        isRestoreCompletedForCurrentSession = false
        isCloudSessionReady = false
        hasUserExplicitlyDeletedLastRuleAfterRestore = false
        lastUploadedJsonHash = 0

        try {
            FirebaseInitializer.ensureInitialized(context)
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}

        // Clear auth_session_meta keys
        context.getSharedPreferences(AUTH_SESSION_META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_UID)
            .remove(KEY_LAST_ACTIVE_GMAIL_UID)
            .remove(KEY_ACTIVE_GMAIL_EMAIL)
            .remove(KEY_ACTIVE_DISPLAY_NAME)
            .remove(KEY_IS_CLOUD_RESTORED_FOR_SESSION)
            .remove(KEY_MODULAR_SCHEMA_SYNCED)
            .commit()

        // Wipe all local SharedPreferences, DataStore, and Room tables to factory defaults
        isRestoringFromCloud.set(true)
        try {
            wipeAllLocalAppSharedPreferences(context)
            preferencesRepository.clearAllLocalPreferences()
            database.clearAllTables()
            preferencesRepository.setOnboardingComplete(true)
            reloadAllAppStateFromDisk(context)
        } finally {
            isRestoringFromCloud.set(false)
        }
    }

    suspend fun handleAccountSignIn(email: String, uid: String? = null, idToken: String? = null): Result<Boolean> {
        return connectGmailAccount(email = email, idToken = idToken)
    }

    suspend fun handleAccountSignOut() {
        disconnectGmailAccount()
    }
}
