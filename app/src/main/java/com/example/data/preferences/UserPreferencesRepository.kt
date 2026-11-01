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

    val baseThemeMode: Flow<BaseThemeMode> = context.dataStore.data.map { preferences ->
        val saved = preferences[PreferencesKeys.BASE_THEME_MODE]
            ?: preferences[PreferencesKeys.THEME_MODE]
            ?: syncPrefs.getString("base_theme_mode", null)
        when (saved) {
            BaseThemeMode.LIGHT.name, "LIGHT" -> BaseThemeMode.LIGHT
            BaseThemeMode.DARK.name, "DARK" -> BaseThemeMode.DARK
            BaseThemeMode.AMOLED.name, "AMOLED" -> BaseThemeMode.AMOLED
            BaseThemeMode.SYSTEM_DEFAULT.name, "SYSTEM" -> getSystemBaseThemeMode()
            else -> getSystemBaseThemeMode()
        }
    }

    val secondaryPalette: Flow<SecondaryThemePalette> = context.dataStore.data.map { preferences ->
        val saved = preferences[PreferencesKeys.SECONDARY_PALETTE]
            ?: syncPrefs.getString("secondary_palette", null)
        SecondaryThemePalette.entries.find { it.name == saved } ?: SecondaryThemePalette.FOCUS_EMERALD
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[PreferencesKeys.THEME_MODE] ?: syncPrefs.getString("theme_mode", null)) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ONBOARDING_COMPLETE]
            ?: if (syncPrefs.contains("onboarding_complete")) syncPrefs.getBoolean("onboarding_complete", false) else false
    }

    val isMasterEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MASTER_ENABLED]
            ?: if (syncPrefs.contains("master_enabled")) syncPrefs.getBoolean("master_enabled", true) else true
    }

    val isCheatProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED]
            ?: if (syncPrefs.contains("cheat_protection_enabled")) syncPrefs.getBoolean("cheat_protection_enabled", false) else false
    }

    val isInvincibleModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED]
            ?: if (syncPrefs.contains("invincible_mode_enabled")) syncPrefs.getBoolean("invincible_mode_enabled", false) else false
    }

    val isClockTamperProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED]
            ?: if (syncPrefs.contains("clock_tamper_protection_enabled")) syncPrefs.getBoolean("clock_tamper_protection_enabled", true) else true
    }

    val reclaimedCommitment: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RECLAIMED_COMMITMENT]
            ?: syncPrefs.getString("reclaimed_commitment", "My Focus") ?: "My Focus"
    }

    val cognitivePassphrase: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COGNITIVE_PASSPHRASE]
            ?: syncPrefs.getString("cognitive_passphrase", DEFAULT_COGNITIVE_PASSPHRASE) ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    val emergencyBreaksRemaining: Flow<Int> = context.dataStore.data.map { preferences ->
        val now = TimeIntegrityManager.getTrueTimeMillis()
        val weekStart = preferences[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] ?: 0L
        if (weekStart == 0L || (now - weekStart) >= WEEK_IN_MILLIS) {
            MAX_WEEKLY_EMERGENCY_BREAKS
        } else {
            preferences[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] ?: MAX_WEEKLY_EMERGENCY_BREAKS
        }
    }

    val activeEmergencyBreakUntil: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] ?: 0L
    }

    val connectedGmailAccount: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT]
            ?: syncPrefs.getString("signed_in_google_email", null)
            ?: syncPrefs.getString("connected_gmail_account", null)
    }

    val lastCloudBackupTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_CLOUD_BACKUP_TIME]
            ?: syncPrefs.getLong("last_cloud_backup_time", 0L)
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
            apply()
        }
        context.dataStore.edit { preferences ->
            if (email != null) {
                preferences[PreferencesKeys.CONNECTED_GMAIL_ACCOUNT] = email.trim().lowercase()
            } else {
                preferences.remove(PreferencesKeys.CONNECTED_GMAIL_ACCOUNT)
            }
        }
    }

    suspend fun setLastCloudBackupTime(timestamp: Long) {
        syncPrefs.edit().putLong("last_cloud_backup_time", timestamp).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CLOUD_BACKUP_TIME] = timestamp
        }
    }

    suspend fun getPreferencesSnapshot(): Map<String, Any?> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "base_theme_mode" to (prefs[PreferencesKeys.BASE_THEME_MODE] ?: syncPrefs.getString("base_theme_mode", null)),
            "secondary_palette" to (prefs[PreferencesKeys.SECONDARY_PALETTE] ?: syncPrefs.getString("secondary_palette", null)),
            "theme_mode" to (prefs[PreferencesKeys.THEME_MODE] ?: syncPrefs.getString("theme_mode", null)),
            "master_enabled" to (prefs[PreferencesKeys.MASTER_ENABLED] ?: syncPrefs.getBoolean("master_enabled", true)),
            "cheat_protection_enabled" to (prefs[PreferencesKeys.CHEAT_PROTECTION_ENABLED] ?: syncPrefs.getBoolean("cheat_protection_enabled", false)),
            "cheat_protection_hash" to (prefs[PreferencesKeys.CHEAT_PROTECTION_HASH] ?: syncPrefs.getString("cheat_protection_hash", null)),
            "cheat_protection_salt" to (prefs[PreferencesKeys.CHEAT_PROTECTION_SALT] ?: syncPrefs.getString("cheat_protection_salt", null)),
            "invincible_mode_enabled" to (prefs[PreferencesKeys.INVINCIBLE_MODE_ENABLED] ?: syncPrefs.getBoolean("invincible_mode_enabled", false)),
            "clock_tamper_protection_enabled" to (prefs[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED] ?: syncPrefs.getBoolean("clock_tamper_protection_enabled", true)),
            "reclaimed_commitment" to (prefs[PreferencesKeys.RECLAIMED_COMMITMENT] ?: syncPrefs.getString("reclaimed_commitment", "My Focus")),
            "cognitive_passphrase" to (prefs[PreferencesKeys.COGNITIVE_PASSPHRASE] ?: syncPrefs.getString("cognitive_passphrase", DEFAULT_COGNITIVE_PASSPHRASE))
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
        if (prefs.has("invincible_mode_enabled")) {
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

        // Apply asynchronously without blocking Main thread
        editor.apply()

        // Also update DataStore
        context.dataStore.edit { preferences ->
            if (prefs.has("base_theme_mode")) {
                val modeName = prefs.getString("base_theme_mode")
                preferences[PreferencesKeys.BASE_THEME_MODE] = modeName
                BaseThemeMode.entries.find { it.name == modeName }?.let {
                    preferences[PreferencesKeys.THEME_MODE] = when (it) {
                        BaseThemeMode.LIGHT -> ThemeMode.LIGHT.name
                        BaseThemeMode.DARK -> ThemeMode.DARK.name
                        BaseThemeMode.AMOLED -> ThemeMode.DARK.name
                        BaseThemeMode.SYSTEM_DEFAULT -> ThemeMode.SYSTEM.name
                    }
                }
            }
            if (prefs.has("secondary_palette")) {
                preferences[PreferencesKeys.SECONDARY_PALETTE] = prefs.getString("secondary_palette")
            }
            if (prefs.has("master_enabled")) {
                preferences[PreferencesKeys.MASTER_ENABLED] = prefs.getBoolean("master_enabled")
            }
            if (prefs.has("cheat_protection_enabled")) {
                val enabled = prefs.getBoolean("cheat_protection_enabled")
                preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = enabled
                val hash = prefs.optString("cheat_protection_hash", null)
                val salt = prefs.optString("cheat_protection_salt", null)
                if (hash != null && salt != null) {
                    preferences[PreferencesKeys.CHEAT_PROTECTION_HASH] = hash
                    preferences[PreferencesKeys.CHEAT_PROTECTION_SALT] = salt
                } else {
                    preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
                    preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
                }
            }
            if (prefs.has("invincible_mode_enabled")) {
                preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] = prefs.getBoolean("invincible_mode_enabled")
            }
            if (prefs.has("clock_tamper_protection_enabled")) {
                preferences[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED] = prefs.getBoolean("clock_tamper_protection_enabled")
            }
            if (prefs.has("reclaimed_commitment")) {
                preferences[PreferencesKeys.RECLAIMED_COMMITMENT] = prefs.getString("reclaimed_commitment")
            }
            if (prefs.has("cognitive_passphrase")) {
                preferences[PreferencesKeys.COGNITIVE_PASSPHRASE] = prefs.getString("cognitive_passphrase")
            }
        }
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
            apply()
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
    }

    suspend fun setClockTamperProtectionEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("clock_tamper_protection_enabled", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOCK_TAMPER_PROTECTION_ENABLED] = enabled
        }
        TimeIntegrityManager.setProtectionEnabled(enabled)
    }

    suspend fun setReclaimedCommitment(commitment: String) {
        syncPrefs.edit().putString("reclaimed_commitment", commitment).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECLAIMED_COMMITMENT] = commitment
        }
    }

    suspend fun setCognitivePassphrase(phrase: String) {
        val trimmed = phrase.trim()
        syncPrefs.edit().putString("cognitive_passphrase", trimmed).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COGNITIVE_PASSPHRASE] = trimmed
        }
    }

    suspend fun getCognitivePassphrase(): String {
        val prefs = context.dataStore.data.first()
        return prefs[PreferencesKeys.COGNITIVE_PASSPHRASE]
            ?: syncPrefs.getString("cognitive_passphrase", DEFAULT_COGNITIVE_PASSPHRASE)
            ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    suspend fun isEmergencyBreakActive(): Boolean {
        val prefs = context.dataStore.data.first()
        val until = prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] ?: 0L
        return TimeIntegrityManager.getTrueTimeMillis() < until
    }

    suspend fun getEmergencyBreaksRemaining(): Int {
        val prefs = context.dataStore.data.first()
        val now = TimeIntegrityManager.getTrueTimeMillis()
        val weekStart = prefs[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] ?: 0L
        if (weekStart == 0L || (now - weekStart) >= WEEK_IN_MILLIS) {
            // Reset weekly allowance
            context.dataStore.edit { editPrefs ->
                editPrefs[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] = now
                editPrefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] = MAX_WEEKLY_EMERGENCY_BREAKS
            }
            return MAX_WEEKLY_EMERGENCY_BREAKS
        }
        return prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] ?: MAX_WEEKLY_EMERGENCY_BREAKS
    }

    suspend fun triggerEmergencyBreak(typedPhrase: String): Boolean {
        val expectedPhrase = getCognitivePassphrase()
        if (typedPhrase.trim() != expectedPhrase.trim()) {
            return false
        }

        var success = false
        val now = TimeIntegrityManager.getTrueTimeMillis()

        context.dataStore.edit { prefs ->
            val weekStart = prefs[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] ?: 0L
            val currentRemaining = if (weekStart == 0L || (now - weekStart) >= WEEK_IN_MILLIS) {
                prefs[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] = now
                MAX_WEEKLY_EMERGENCY_BREAKS
            } else {
                prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] ?: MAX_WEEKLY_EMERGENCY_BREAKS
            }

            if (currentRemaining > 0) {
                prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] = currentRemaining - 1
                prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] = now + EMERGENCY_BREAK_DURATION_MS
                success = true
            }
        }

        return success
    }

    suspend fun takeEmergencyBreak(): Boolean {
        var success = false
        val now = TimeIntegrityManager.getTrueTimeMillis()

        context.dataStore.edit { prefs ->
            val weekStart = prefs[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] ?: 0L
            val currentRemaining = if (weekStart == 0L || (now - weekStart) >= WEEK_IN_MILLIS) {
                prefs[PreferencesKeys.EMERGENCY_BREAK_WEEK_START] = now
                MAX_WEEKLY_EMERGENCY_BREAKS
            } else {
                prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] ?: MAX_WEEKLY_EMERGENCY_BREAKS
            }

            if (currentRemaining > 0) {
                prefs[PreferencesKeys.EMERGENCY_BREAKS_REMAINING] = currentRemaining - 1
                prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] = now + EMERGENCY_BREAK_DURATION_MS
                success = true
            }
        }

        return success
    }

    suspend fun endEmergencyBreakEarly() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] = 0L
        }
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
            apply()
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
    }

    suspend fun setSecondaryPalette(palette: SecondaryThemePalette) {
        syncPrefs.edit().putString("secondary_palette", palette.name).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SECONDARY_PALETTE] = palette.name
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        syncPrefs.edit().apply {
            putString("theme_mode", mode.name)
            putString("base_theme_mode", when (mode) {
                ThemeMode.LIGHT -> BaseThemeMode.LIGHT.name
                ThemeMode.DARK -> BaseThemeMode.DARK.name
                ThemeMode.SYSTEM -> BaseThemeMode.SYSTEM_DEFAULT.name
            })
            apply()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
            preferences[PreferencesKeys.BASE_THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> BaseThemeMode.LIGHT.name
                ThemeMode.DARK -> BaseThemeMode.DARK.name
                ThemeMode.SYSTEM -> BaseThemeMode.SYSTEM_DEFAULT.name
            }
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        syncPrefs.edit().putBoolean("onboarding_complete", complete).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("master_enabled", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MASTER_ENABLED] = enabled
        }
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
            apply()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = true
            preferences[PreferencesKeys.CHEAT_PROTECTION_HASH] = hash
            preferences[PreferencesKeys.CHEAT_PROTECTION_SALT] = salt
        }
        return true
    }

    suspend fun disableCheatProtection() {
        syncPrefs.edit().apply {
            putBoolean("cheat_protection_enabled", false)
            remove("cheat_protection_hash")
            remove("cheat_protection_salt")
            apply()
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = false
            preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
            preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
        }
    }

    suspend fun setInvincibleModeEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("invincible_mode_enabled", enabled).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] = enabled
        }
    }

    suspend fun verifyCheatPassphrase(input: String): Boolean {
        val prefs = context.dataStore.data.first()
        val isEnabled = prefs[PreferencesKeys.CHEAT_PROTECTION_ENABLED]
            ?: syncPrefs.getBoolean("cheat_protection_enabled", false)
        if (!isEnabled) return true

        val storedHash = prefs[PreferencesKeys.CHEAT_PROTECTION_HASH]
            ?: syncPrefs.getString("cheat_protection_hash", null) ?: return false
        val salt = prefs[PreferencesKeys.CHEAT_PROTECTION_SALT]
            ?: syncPrefs.getString("cheat_protection_salt", null) ?: return false
        val computedHash = hashPassphrase(input, salt)
        return computedHash == storedHash
    }

    private fun hashPassphrase(passphrase: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + passphrase).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
