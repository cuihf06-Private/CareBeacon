package com.carebeacon.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.roleDataStore by preferencesDataStore(name = "role_store")

/**
 * Persistent single-value storage for "which identity is this device configured as".
 * Stored in DataStore so it survives process death without Room migrations.
 */
class RoleStore(private val context: Context) {

    val role: Flow<String?> = context.roleDataStore.data.map { prefs: Preferences ->
        prefs[KEY_ROLE]
    }

    val pairingCode: Flow<String?> = context.roleDataStore.data.map { prefs: Preferences ->
        prefs[KEY_CODE]
    }

    /**
     * Demo-mode flag — true means the user has acknowledged that both roles share
     * the local DB so they can be exercised on the same device. Default false.
     */
    val demoMode: Flow<Boolean> = context.roleDataStore.data.map { prefs: Preferences ->
        prefs[KEY_DEMO] ?: false
    }

    suspend fun setRole(role: String) {
        context.roleDataStore.edit { it[KEY_ROLE] = role }
    }

    suspend fun setPairingCode(code: String?) {
        context.roleDataStore.edit { prefs ->
            if (code == null) prefs.remove(KEY_CODE) else prefs[KEY_CODE] = code
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.roleDataStore.edit { it[KEY_DEMO] = enabled }
    }

    suspend fun clear() {
        context.roleDataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_ROLE = stringPreferencesKey("role")
        private val KEY_CODE = stringPreferencesKey("pairing_code")
        private val KEY_DEMO = booleanPreferencesKey("demo_mode")
    }
}