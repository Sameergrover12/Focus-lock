package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val CHEAT_PROTECTION_ENABLED = booleanPreferencesKey("cheat_protection_enabled")
        val CHEAT_PROTECTION_HASH = stringPreferencesKey("cheat_protection_hash")
        val CHEAT_PROTECTION_SALT = stringPreferencesKey("cheat_protection_salt")
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
