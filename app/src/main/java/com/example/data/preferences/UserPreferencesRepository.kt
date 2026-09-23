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
import java.security.MessageDigest
import java.util.UUID

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
    }

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val CHEAT_PROTECTION_ENABLED = booleanPreferencesKey("cheat_protection_enabled")
        val CHEAT_PROTECTION_HASH = stringPreferencesKey("cheat_protection_hash")
        val CHEAT_PROTECTION_SALT = stringPreferencesKey("cheat_protection_salt")
        val INVINCIBLE_MODE_ENABLED = booleanPreferencesKey("invincible_mode_enabled")
        
        // Psychological Onboarding commitment
        val RECLAIMED_COMMITMENT = stringPreferencesKey("reclaimed_commitment")
        
        // Cognitive Passphrase
        val COGNITIVE_PASSPHRASE = stringPreferencesKey("cognitive_passphrase")
        
        // Emergency Failsafe System
        val EMERGENCY_BREAKS_REMAINING = intPreferencesKey("emergency_breaks_remaining")
        val EMERGENCY_BREAK_WEEK_START = longPreferencesKey("emergency_break_week_start")
        val ACTIVE_EMERGENCY_BREAK_UNTIL = longPreferencesKey("active_emergency_break_until")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[PreferencesKeys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ONBOARDING_COMPLETE] ?: false
    }

    val isMasterEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MASTER_ENABLED] ?: true
    }

    val isCheatProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] ?: false
    }

    val isInvincibleModeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] ?: false
    }

    val reclaimedCommitment: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RECLAIMED_COMMITMENT] ?: "My Focus"
    }

    val cognitivePassphrase: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COGNITIVE_PASSPHRASE] ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    val emergencyBreaksRemaining: Flow<Int> = context.dataStore.data.map { preferences ->
        val now = System.currentTimeMillis()
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

    suspend fun setReclaimedCommitment(commitment: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECLAIMED_COMMITMENT] = commitment
        }
    }

    suspend fun setCognitivePassphrase(phrase: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COGNITIVE_PASSPHRASE] = phrase.trim()
        }
    }

    suspend fun getCognitivePassphrase(): String {
        val prefs = context.dataStore.data.first()
        return prefs[PreferencesKeys.COGNITIVE_PASSPHRASE] ?: DEFAULT_COGNITIVE_PASSPHRASE
    }

    suspend fun isEmergencyBreakActive(): Boolean {
        val prefs = context.dataStore.data.first()
        val until = prefs[PreferencesKeys.ACTIVE_EMERGENCY_BREAK_UNTIL] ?: 0L
        return System.currentTimeMillis() < until
    }

    suspend fun getEmergencyBreaksRemaining(): Int {
        val prefs = context.dataStore.data.first()
        val now = System.currentTimeMillis()
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

    /**
     * Attempts to trigger an emergency 10-minute break.
     * Verifies that breaks are available (>0) and that the typed reflective sentence matches exactly.
     * Decrements the weekly allowance and sets the 10-minute cutoff timestamp.
     */
    suspend fun triggerEmergencyBreak(typedPhrase: String): Boolean {
        val expectedPhrase = getCognitivePassphrase()
        if (typedPhrase.trim() != expectedPhrase.trim()) {
            return false
        }

        var success = false
        val now = System.currentTimeMillis()

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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
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
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = true
            preferences[PreferencesKeys.CHEAT_PROTECTION_HASH] = hash
            preferences[PreferencesKeys.CHEAT_PROTECTION_SALT] = salt
        }
        return true
    }

    suspend fun disableCheatProtection() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHEAT_PROTECTION_ENABLED] = false
            preferences.remove(PreferencesKeys.CHEAT_PROTECTION_HASH)
            preferences.remove(PreferencesKeys.CHEAT_PROTECTION_SALT)
        }
    }

    suspend fun setInvincibleModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INVINCIBLE_MODE_ENABLED] = enabled
        }
    }

    suspend fun verifyCheatPassphrase(input: String): Boolean {
        val prefs = context.dataStore.data.first()
        val isEnabled = prefs[PreferencesKeys.CHEAT_PROTECTION_ENABLED] ?: false
        if (!isEnabled) return true

        val storedHash = prefs[PreferencesKeys.CHEAT_PROTECTION_HASH] ?: return false
        val salt = prefs[PreferencesKeys.CHEAT_PROTECTION_SALT] ?: return false
        val computedHash = hashPassphrase(input, salt)
        return computedHash == storedHash
    }

    private fun hashPassphrase(passphrase: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + passphrase).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
