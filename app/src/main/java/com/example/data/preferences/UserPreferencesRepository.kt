package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.util.TimeIntegrityManager
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

import com.example.ui.theme.BaseThemeMode
import com.example.ui.theme.SecondaryThemePalette

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        const val DEFAULT_COGNITIVE_PASSPHRASE = "I am choosing short-term dopamine over my long-term goals"
        const val EMERGENCY_BREAK_DURATION_MS = 10 * 60 * 1000L // Exactly 10 minutes
        const val MAX_WEEKLY_EMERGENCY_BREAKS = 3
        const val WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 1000L
        const val SYNC_PREFS_NAME = "user_preferences_sync"
    }

    val syncPrefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    private val refreshTrigger = MutableStateFlow(0)

    fun notifyPreferencesReloaded() {
        refreshTrigger.value += 1
    }

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BASE_THEME_MODE = stringPreferencesKey("base_theme_mode")
        val SECONDARY_PALETTE = stringPreferencesKey("secondary_palette")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val CHEAT_PROTECTION_ENABLED = booleanPreferencesKey("cheat_protection_enabled")
        val CHEAT_PROTECTION_HASH = stringPreferencesKey("cheat_protection_hash")
        val CHEAT_PROTECTION_SALT = stringPreferencesKey("cheat_protection_salt")
        val INVINCIBLE_MODE_ENABLED = booleanPreferencesKey("invincible_mode_enabled")
        val CLOCK_TAMPER_PROTECTION_ENABLED = booleanPreferencesKey("clock_tamper_protection_enabled")
        
        // Psychological Onboarding commitment
        val RECLAIMED_COMMITMENT = stringPreferencesKey("reclaimed_commitment")
        
        // Cognitive Passphrase
        val COGNITIVE_PASSPHRASE = stringPreferencesKey("cognitive_passphrase")
        
        // Emergency Failsafe System
        val EMERGENCY_BREAKS_REMAINING = intPreferencesKey("emergency_breaks_remaining")
        val EMERGENCY_BREAK_WEEK_START = longPreferencesKey("emergency_break_week_start")
        val ACTIVE_EMERGENCY_BREAK_UNTIL = longPreferencesKey("active_emergency_break_until")

        // Cloud Backup & Account
        val CONNECTED_GMAIL_ACCOUNT = stringPreferencesKey("connected_gmail_account")
        val LAST_CLOUD_BACKUP_TIME = longPreferencesKey("last_cloud_backup_time")
    }

    fun getSystemBaseThemeMode(): BaseThemeMode {
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            BaseThemeMode.DARK
        } else {
            BaseThemeMode.LIGHT
        }
    }

    fun getInitialBaseThemeMode(): BaseThemeMode {
        val saved = syncPrefs.getString("base_theme_mode", null) ?: syncPrefs.getString("theme_mode", null)
        return when (saved) {
            BaseThemeMode.LIGHT.name, "LIGHT" -> BaseThemeMode.LIGHT
            BaseThemeMode.DARK.name, "DARK" -> BaseThemeMode.DARK
            BaseThemeMode.AMOLED.name, "AMOLED" -> BaseThemeMode.AMOLED
            BaseThemeMode.SYSTEM_DEFAULT.name, "SYSTEM" -> getSystemBaseThemeMode()
            else -> getSystemBaseThemeMode()
        }
    }

    fun getInitialSecondaryPalette(): SecondaryThemePalette {
        val saved = syncPrefs.getString("secondary_palette", null)
        return SecondaryThemePalette.entries.find { it.name == saved } ?: SecondaryThemePalette.FOCUS_EMERALD
    }

    fun getInitialCognitivePassphrase(): String {
        return syncPrefs.getString("cognitive_passphrase", null) ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    val baseThemeMode: Flow<BaseThemeMode> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        val saved = syncPrefs.getString("base_theme_mode", null)
            ?: preferences[PreferencesKeys.BASE_THEME_MODE]
            ?: syncPrefs.getString("theme_mode", null)
            ?: preferences[PreferencesKeys.THEME_MODE]
        when (saved) {
            BaseThemeMode.LIGHT.name, "LIGHT" -> BaseThemeMode.LIGHT
            BaseThemeMode.DARK.name, "DARK" -> BaseThemeMode.DARK
            BaseThemeMode.AMOLED.name, "AMOLED" -> BaseThemeMode.AMOLED
            BaseThemeMode.SYSTEM_DEFAULT.name, "SYSTEM" -> getSystemBaseThemeMode()
            else -> getSystemBaseThemeMode()
        }
    }

    val secondaryPalette: Flow<SecondaryThemePalette> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        val saved = syncPrefs.getString("secondary_palette", null)
            ?: preferences[PreferencesKeys.SECONDARY_PALETTE]
        SecondaryThemePalette.entries.find { it.name == saved } ?: SecondaryThemePalette.FOCUS_EMERALD
    }

    val themeMode: Flow<ThemeMode> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        when (syncPrefs.getString("theme_mode", null) ?: preferences[PreferencesKeys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val isOnboardingComplete: Flow<Boolean> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        if (syncPrefs.contains("onboarding_complete")) {
            syncPrefs.getBoolean("onboarding_complete", false)
        } else {
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] ?: false
        }
    }

    val isMasterEnabled: Flow<Boolean> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        if (syncPrefs.contains("master_enabled")) {
            syncPrefs.getBoolean("master_enabled", true)
        } else {
            preferences[PreferencesKeys.MASTER_ENABLED] ?: true
        }
    }

    val isCheatProtectionEnabled: Flow<Boolean> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        if (syncPrefs.contains("cheat_protection_enabled")) {
            syncPrefs.getBoolean("cheat_protection_enabled", false)
        } else {
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] ?: false
        }
    }

    fun isDeviceAdminActiveOnOS(): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(context, com.example.receiver.FocusDeviceAdminReceiver::class.java)
            dpm?.isAdminActive(adminComponent) == true
        } catch (_: Exception) {
            false
        }
    }

    val isInvincibleModeEnabled: Flow<Boolean> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        val osAdminActive = isDeviceAdminActiveOnOS()
        if (!osAdminActive) {
            if (syncPrefs.getBoolean("invincible_mode_enabled", false)) {
                syncPrefs.edit().putBoolean("invincible_mode_enabled", false).apply()
            }
            false
        } else if (syncPrefs.contains("invincible_mode_enabled")) {
            syncPrefs.getBoolean("invincible_mode_enabled", false)
        } else {
            preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] ?: false
        }
    }

    val isClockTamperProtectionEnabled: Flow<Boolean> = combine(context.dataStore.data, refreshTrigger) { _, _ ->
        true
    }

    val reclaimedCommitment: Flow<String> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        syncPrefs.getString("reclaimed_commitment", null)
            ?: preferences[PreferencesKeys.RECLAIMED_COMMITMENT]
            ?: "My Focus"
    }

    val cognitivePassphrase: Flow<String> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        syncPrefs.getString("cognitive_passphrase", null)
            ?: preferences[PreferencesKeys.COGNITIVE_PASSPHRASE]
            ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    val emergencyBreaksRemaining: Flow<Int> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        val now = TimeIntegrityManager.getTrueTimeMillis()
        val weekStart = preferences[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] ?: 0L
        if (weekStart == 0L || (now - weekStart) >= WEEK_IN_MILLIS) {
            MAX_WEEKLY_EMERGENCY_BREAKS
        } else {
            preferences[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] ?: MAX_WEEKLY_EMERGENCY_BREAKS
        }
    }

    val activeEmergencyBreakUntil: Flow<Long> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        preferences[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] ?: 0L
    }

    val connectedGmailAccount: Flow<String?> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        syncPrefs.getString("signed_in_google_email", null)
            ?: syncPrefs.getString("connected_gmail_account", null)
            ?: preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT]
    }

    val lastCloudBackupTime: Flow<Long> = combine(context.dataStore.data, refreshTrigger) { preferences, _ ->
        val syncTime = syncPrefs.getLong("last_cloud_backup_time", 0L)
        if (syncTime > 0L) syncTime else (preferences[PreferencesKeys.LAST_CLOUD_BACKUP_TIME] ?: 0L)
    }

    fun getConnectedGmailAccountSync(): String? {
        return syncPrefs.getString("signed_in_google_email", null)
            ?: syncPrefs.getString("connected_gmail_account", null)
    }

    suspend fun setConnectedGmailAccount(email: String?) {
        syncPrefs.edit().apply {
            if (email != null) {
                putString("signed_in_google_email", email.trim().lowercase())
                putString("connected_gmail_account", email.trim().lowercase())
            } else {
                remove("signed_in_google_email")
                remove("connected_gmail_account")
            }
            commit()
        }
        context.dataStore.edit { preferences ->
            if (email != null) {
                preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT] = email.trim().lowercase()
            } else {
                preferences.remove(PreferencesKeys.CONNECTED_GMAIL_ACCOUNT)
            }
        }
        refreshTrigger.value += 1
    }

    suspend fun setLastCloudBackupTime(timestamp: Long) {
        syncPrefs.edit().putLong("last_cloud_backup_time", timestamp).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CLOUD_BACKUP_TIME] = timestamp
        }
        refreshTrigger.value += 1
    }

    /**
     * Synchronizes DataStore from the current contents of [syncPrefs] (`user_preferences_sync`)
     * after a cloud restore or account switch and emits a live refresh event so all UI StateFlows
     * (including theme mode, palette, strict mode, master switch, and commitment) update at 0ms.
     */
    suspend fun syncDataStoreFromSharedPrefs() {
        context.dataStore.edit { preferences ->
            val baseMode = syncPrefs.getString("base_theme_mode", null)
            if (baseMode != null) {
                preferences[PreferencesKeys.BASE_THEME_MODE] = baseMode
            } else {
                preferences.remove(PreferencesKeys.BASE_THEME_MODE)
            }

            val themeModeVal = syncPrefs.getString("theme_mode", null)
            if (themeModeVal != null) {
                preferences[PreferencesKeys.THEME_MODE] = themeModeVal
            } else if (baseMode != null) {
                BaseThemeMode.entries.find { it.name == baseMode }?.let {
                    preferences[PreferencesKeys.THEME_MODE] = when (it) {
                        BaseThemeMode.LIGHT -> ThemeMode.LIGHT.name
                        BaseThemeMode.DARK -> ThemeMode.DARK.name
                        BaseThemeMode.AMOLED -> ThemeMode.DARK.name
                        BaseThemeMode.SYSTEM_DEFAULT -> ThemeMode.SYSTEM.name
                    }
                }
            } else {
                preferences.remove(PreferencesKeys.THEME_MODE)
            }

            val palette = syncPrefs.getString("secondary_palette", null)
            if (palette != null) {
                preferences[PreferencesKeys.SECONDARY_PALETTE] = palette
            } else {
                preferences.remove(PreferencesKeys.SECONDARY_PALETTE)
            }

            if (syncPrefs.contains("onboarding_complete")) {
                preferences[PreferencesKeys.ONBOARDING_COMPLETE] = syncPrefs.getBoolean("onboarding_complete", false)
            }

            if (syncPrefs.contains("master_enabled")) {
                preferences[PreferencesKeys.MASTER_ENABLED] = syncPrefs.getBoolean("master_enabled", true)
            } else {
                preferences.remove(PreferencesKeys.MASTER_ENABLED)
            }

            if (syncPrefs.contains("cheat_protection_enabled")) {
                val enabled = syncPrefs.getBoolean("cheat_protection_enabled", false)
                preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = enabled
                val hash = syncPrefs.getString("cheat_protection_hash", null)
                val salt = syncPrefs.getString("cheat_protection_salt", null)
                if (hash != null && salt != null) {
                    preferences[PreferencesKeys.CHEAT_PROTECTION_HASH] = hash
                    preferences[PreferencesKeys.CHEAT_PROTECTION_SALT] = salt
                } else {
                    preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
                    preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
                }
            } else {
                preferences.remove(PreferencesKeys.CHEAT_PROTECTION_ENABLED)
                preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
                preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
            }

            val osAdminActive = isDeviceAdminActiveOnOS()
            if (!osAdminActive) {
                syncPrefs.edit().putBoolean("invincible_mode_enabled", false).commit()
                preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] = false
            } else if (syncPrefs.contains("invincible_mode_enabled")) {
                preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] = syncPrefs.getBoolean("invincible_mode_enabled", false)
            } else {
                preferences.remove(PreferencesKeys.INVINCIBLE_MODE_ENABLED)
            }

            if (syncPrefs.contains("clock_tamper_protection_enabled")) {
                val clockEnabled = syncPrefs.getBoolean("clock_tamper_protection_enabled", true)
                preferences[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED] = clockEnabled
                TimeIntegrityManager.setProtectionEnabled(clockEnabled)
            } else {
                preferences.remove(PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED)
                TimeIntegrityManager.setProtectionEnabled(true)
            }

            val commitment = syncPrefs.getString("reclaimed_commitment", null)
            if (commitment != null) {
                preferences[PreferencesKeys.RECLAIMED_COMMITMENT] = commitment
            } else {
                preferences.remove(PreferencesKeys.RECLAIMED_COMMITMENT)
            }

            val passphrase = syncPrefs.getString("cognitive_passphrase", null)
            if (passphrase != null) {
                preferences[PreferencesKeys.COGNITIVE_PASSPHRASE] = passphrase
            } else {
                preferences.remove(PreferencesKeys.COGNITIVE_PASSPHRASE)
            }

            val email = syncPrefs.getString("signed_in_google_email", null)
                ?: syncPrefs.getString("connected_gmail_account", null)
            if (email != null) {
                preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT] = email
            } else {
                preferences.remove(PreferencesKeys.CONNECTED_GMAIL_ACCOUNT)
            }

            if (syncPrefs.contains("last_cloud_backup_time")) {
                preferences[PreferencesKeys.LAST_CLOUD_BACKUP_TIME] = syncPrefs.getLong("last_cloud_backup_time", 0L)
            } else {
                preferences.remove(PreferencesKeys.LAST_CLOUD_BACKUP_TIME)
            }
        }
        refreshTrigger.value += 1
    }

    suspend fun getPreferencesSnapshot(): Map<String, Any?> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "base_theme_mode" to (syncPrefs.getString("base_theme_mode", null) ?: prefs[PreferencesKeys.BASE_THEME_MODE]),
            "secondary_palette" to (syncPrefs.getString("secondary_palette", null) ?: prefs[PreferencesKeys.SECONDARY_PALETTE]),
            "theme_mode" to (syncPrefs.getString("theme_mode", null) ?: prefs[PreferencesKeys.THEME_MODE]),
            "master_enabled" to (if (syncPrefs.contains("master_enabled")) syncPrefs.getBoolean("master_enabled", true) else (prefs[PreferencesKeys.MASTER_ENABLED] ?: true)),
            "cheat_protection_enabled" to (if (syncPrefs.contains("cheat_protection_enabled")) syncPrefs.getBoolean("cheat_protection_enabled", false) else (prefs[PreferencesKeys.CHEAT_PROTECTION_ENABLED] ?: false)),
            "cheat_protection_hash" to (syncPrefs.getString("cheat_protection_hash", null) ?: prefs[PreferencesKeys.CHEAT_PROTECTION_HASH]),
            "cheat_protection_salt" to (syncPrefs.getString("cheat_protection_salt", null) ?: prefs[PreferencesKeys.CHEAT_PROTECTION_SALT]),
            "invincible_mode_enabled" to (if (syncPrefs.contains("invincible_mode_enabled")) syncPrefs.getBoolean("invincible_mode_enabled", false) else (prefs[PreferencesKeys.INVINCIBLE_MODE_ENABLED] ?: false)),
            "clock_tamper_protection_enabled" to (if (syncPrefs.contains("clock_tamper_protection_enabled")) syncPrefs.getBoolean("clock_tamper_protection_enabled", true) else (prefs[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED] ?: true)),
            "reclaimed_commitment" to (syncPrefs.getString("reclaimed_commitment", null) ?: prefs[PreferencesKeys.RECLAIMED_COMMITMENT] ?: "My Focus"),
            "cognitive_passphrase" to (syncPrefs.getString("cognitive_passphrase", null) ?: prefs[PreferencesKeys.COGNITIVE_PASSPHRASE] ?: DEFAULT_COGNITIVE_PASSPHRASE)
        )
    }

    suspend fun restorePreferencesSync(prefs: JSONObject) {
        val editor = syncPrefs.edit()

        if (prefs.has("base_theme_mode")) {
            val modeName = prefs.getString("base_theme_mode")
            editor.putString("base_theme_mode", modeName)
            BaseThemeMode.entries.find { it.name == modeName }?.let {
                val themeModeName = when (it) {
                    BaseThemeMode.LIGHT -> ThemeMode.LIGHT.name
                    BaseThemeMode.DARK -> ThemeMode.DARK.name
                    BaseThemeMode.AMOLED -> ThemeMode.DARK.name
                    BaseThemeMode.SYSTEM_DEFAULT -> ThemeMode.SYSTEM.name
                }
                editor.putString("theme_mode", themeModeName)
            }
        }
        if (prefs.has("secondary_palette")) {
            editor.putString("secondary_palette", prefs.getString("secondary_palette"))
        }
        if (prefs.has("master_enabled")) {
            editor.putBoolean("master_enabled", prefs.getBoolean("master_enabled"))
        }
        if (prefs.has("cheat_protection_enabled")) {
            editor.putBoolean("cheat_protection_enabled", prefs.getBoolean("cheat_protection_enabled"))
            if (prefs.has("cheat_protection_hash")) {
                editor.putString("cheat_protection_hash", prefs.getString("cheat_protection_hash"))
            } else {
                editor.remove("cheat_protection_hash")
            }
            if (prefs.has("cheat_protection_salt")) {
                editor.putString("cheat_protection_salt", prefs.getString("cheat_protection_salt"))
            } else {
                editor.remove("cheat_protection_salt")
            }
        }
        val osAdminActive = isDeviceAdminActiveOnOS()
        if (!osAdminActive) {
            editor.putBoolean("invincible_mode_enabled", false)
        } else if (prefs.has("invincible_mode_enabled")) {
            editor.putBoolean("invincible_mode_enabled", prefs.getBoolean("invincible_mode_enabled"))
        }
        if (prefs.has("clock_tamper_protection_enabled")) {
            editor.putBoolean("clock_tamper_protection_enabled", prefs.getBoolean("clock_tamper_protection_enabled"))
        }
        if (prefs.has("reclaimed_commitment")) {
            editor.putString("reclaimed_commitment", prefs.getString("reclaimed_commitment"))
        }
        if (prefs.has("cognitive_passphrase")) {
            editor.putString("cognitive_passphrase", prefs.getString("cognitive_passphrase"))
        }

        // Commit synchronously so disk state is immediately consistent
        editor.commit()

        // Also update DataStore and notify flows
        syncDataStoreFromSharedPrefs()
    }

    suspend fun restoreCheatProtection(enabled: Boolean, hash: String?, salt: String?) {
        syncPrefs.edit().apply {
            putBoolean("cheat_protection_enabled", enabled)
            if (hash != null && salt != null) {
                putString("cheat_protection_hash", hash)
                putString("cheat_protection_salt", salt)
            } else {
                remove("cheat_protection_hash")
                remove("cheat_protection_salt")
            }
            commit()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = enabled
            if (hash != null && salt != null) {
                preferences[PreferencesKeys.CHEAT_PROTECTION_HASH] = hash
                preferences[PreferencesKeys.CHEAT_PROTECTION_SALT] = salt
            } else {
                preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
                preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
            }
        }
        refreshTrigger.value += 1
    }

    suspend fun setClockTamperProtectionEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        syncPrefs.edit().putBoolean("clock_tamper_protection_enabled", true).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED] = true
        }
        TimeIntegrityManager.setProtectionEnabled(true)
        refreshTrigger.value += 1
    }

    suspend fun setReclaimedCommitment(commitment: String) {
        syncPrefs.edit().putString("reclaimed_commitment", commitment).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECLAIMED_COMMITMENT] = commitment
        }
        refreshTrigger.value += 1
    }

    suspend fun setCognitivePassphrase(phrase: String) {
        val trimmed = phrase.trim()
        syncPrefs.edit().putString("cognitive_passphrase", trimmed).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COGNITIVE_PASSPHRASE] = trimmed
        }
        refreshTrigger.value += 1
    }

    suspend fun getCognitivePassphrase(): String {
        val prefs = context.dataStore.data.first()
        return syncPrefs.getString("cognitive_passphrase", null)
            ?: prefs[PreferencesKeys.COGNITIVE_PASSPHRASE]
            ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    suspend fun isEmergencyBreakActive(): Boolean {
        return com.example.util.EmergencyBreakManager.isBreakActive(context)
    }

    suspend fun getEmergencyBreaksRemaining(): Int {
        return com.example.util.EmergencyBreakManager.getBreaksRemaining(context)
    }

    suspend fun triggerEmergencyBreak(typedPhrase: String): Boolean {
        val expectedPhrase = getCognitivePassphrase()
        if (typedPhrase.trim() != expectedPhrase.trim()) {
            return false
        }

        val started = com.example.util.EmergencyBreakManager.startTenMinuteBreak(context)
        if (started) {
            val trueNow = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
            context.dataStore.edit { prefs ->
                prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] = trueNow + 10 * 60 * 1000L
                prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] = com.example.util.EmergencyBreakManager.getBreaksRemaining(context)
            }
            refreshTrigger.value += 1
        }
        return started
    }

    suspend fun takeEmergencyBreak(): Boolean {
        val started = com.example.util.EmergencyBreakManager.startTenMinuteBreak(context)
        if (started) {
            val trueNow = TimeIntegrityManager.getTrustedCurrentTimeMillis(context)
            context.dataStore.edit { prefs ->
                prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] = trueNow + 10 * 60 * 1000L
                prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] = com.example.util.EmergencyBreakManager.getBreaksRemaining(context)
            }
            refreshTrigger.value += 1
        }
        return started
    }

    suspend fun endEmergencyBreakEarly() {
        com.example.util.EmergencyBreakManager.onBreakExpired(context)
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] = 0L
        }
        refreshTrigger.value += 1
    }

    suspend fun setBaseThemeMode(mode: BaseThemeMode) {
        syncPrefs.edit().apply {
            putString("base_theme_mode", mode.name)
            putString("theme_mode", when (mode) {
                BaseThemeMode.LIGHT -> ThemeMode.LIGHT.name
                BaseThemeMode.DARK -> ThemeMode.DARK.name
                BaseThemeMode.AMOLED -> ThemeMode.DARK.name
                BaseThemeMode.SYSTEM_DEFAULT -> ThemeMode.SYSTEM.name
            })
            commit()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_THEME_MODE] = mode.name
            preferences[PreferencesKeys.THEME_MODE] = when (mode) {
                BaseThemeMode.LIGHT -> ThemeMode.LIGHT.name
                BaseThemeMode.DARK -> ThemeMode.DARK.name
                BaseThemeMode.AMOLED -> ThemeMode.DARK.name
                BaseThemeMode.SYSTEM_DEFAULT -> ThemeMode.SYSTEM.name
            }
        }
        refreshTrigger.value += 1
    }

    suspend fun setSecondaryPalette(palette: SecondaryThemePalette) {
        syncPrefs.edit().putString("secondary_palette", palette.name).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SECONDARY_PALETTE] = palette.name
        }
        refreshTrigger.value += 1
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        syncPrefs.edit().apply {
            putString("theme_mode", mode.name)
            putString("base_theme_mode", when (mode) {
                ThemeMode.LIGHT -> BaseThemeMode.LIGHT.name
                ThemeMode.DARK -> BaseThemeMode.DARK.name
                ThemeMode.SYSTEM -> BaseThemeMode.SYSTEM_DEFAULT.name
            })
            commit()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
            preferences[PreferencesKeys.BASE_THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> BaseThemeMode.LIGHT.name
                ThemeMode.DARK -> BaseThemeMode.DARK.name
                ThemeMode.SYSTEM -> BaseThemeMode.SYSTEM_DEFAULT.name
            }
        }
        refreshTrigger.value += 1
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        syncPrefs.edit().putBoolean("onboarding_complete", complete).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] = complete
        }
        refreshTrigger.value += 1
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("master_enabled", enabled).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MASTER_ENABLED] = enabled
        }
        refreshTrigger.value += 1
    }

    suspend fun enableCheatProtection(passphrase: String): Boolean {
        if (passphrase.length !in 600..1000) {
            return false
        }
        val salt = UUID.randomUUID().toString()
        val hash = hashPassphrase(passphrase, salt)
        syncPrefs.edit().apply {
            putBoolean("cheat_protection_enabled", true)
            putString("cheat_protection_hash", hash)
            putString("cheat_protection_salt", salt)
            commit()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = true
            preferences[PreferencesKeys.CHEAT_PROTECTION_HASH] = hash
            preferences[PreferencesKeys.CHEAT_PROTECTION_SALT] = salt
        }
        refreshTrigger.value += 1
        return true
    }

    suspend fun disableCheatProtection() {
        syncPrefs.edit().apply {
            putBoolean("cheat_protection_enabled", false)
            remove("cheat_protection_hash")
            remove("cheat_protection_salt")
            commit()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = false
            preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
            preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
        }
        refreshTrigger.value += 1
    }

    suspend fun setInvincibleModeEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("invincible_mode_enabled", enabled).commit()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] = enabled
        }
        refreshTrigger.value += 1
    }

    suspend fun verifyCheatPassphrase(input: String): Boolean {
        val prefs = context.dataStore.data.first()
        val isEnabled = if (syncPrefs.contains("cheat_protection_enabled")) {
            syncPrefs.getBoolean("cheat_protection_enabled", false)
        } else {
            prefs[PreferencesKeys.CHEAT_PROTECTION_ENABLED] ?: false
        }
        if (!isEnabled) return true

        val storedHash = syncPrefs.getString("cheat_protection_hash", null)
            ?: prefs[PreferencesKeys.CHEAT_PROTECTION_HASH] ?: return false
        val salt = syncPrefs.getString("cheat_protection_salt", null)
            ?: prefs[PreferencesKeys.CHEAT_PROTECTION_SALT] ?: return false
        val computedHash = hashPassphrase(input, salt)
        return computedHash == storedHash
    }

    suspend fun clearAllLocalPreferences() {
        syncPrefs.edit().clear().commit()
        context.dataStore.edit { it.clear() }
        refreshTrigger.value += 1
    }

    private fun hashPassphrase(passphrase: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + passphrase).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
