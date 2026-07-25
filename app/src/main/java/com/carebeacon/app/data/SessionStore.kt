package com.carebeacon.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_store")

/**
 * Persists "which account is currently logged in on this device". Replaces the
 * old role-based DataStore for the account-aware navigation. The old
 * [RoleStore] keeps its fields until PR4 deletes them.
 *
 * The DataStore is constructor-injected so unit tests can supply a fake.
 */
class SessionStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.sessionDataStore)

    val currentAccountId: Flow<String?> = store.data.map { prefs: Preferences ->
        prefs[KEY_CURRENT_ACCOUNT_ID]
    }

    suspend fun setCurrent(accountId: String) {
        store.edit { it[KEY_CURRENT_ACCOUNT_ID] = accountId }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY_CURRENT_ACCOUNT_ID) }
    }

    companion object {
        private val KEY_CURRENT_ACCOUNT_ID = stringPreferencesKey("current_account_id")
    }
}