package com.carebeacon.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory fake for unit tests. Backed by a [MutableStateFlow] so observers see
 * consistent snapshots, and a [Mutex] so [updateData] doesn't race with itself.
 */
class FakePreferencesDataStore(
    initial: MutablePreferences = mutablePreferencesOf(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow<Preferences>(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

/** Snapshot helper used by tests that just want the current value once. */
suspend fun FakePreferencesDataStore.snapshot(): Preferences = data.first()

/* ------------------------------------------------------------------ */
/* In-memory DAO fakes                                                 */
/* ------------------------------------------------------------------ */

class FakeAccountDao : AccountDao {

    private val byId = linkedMapOf<String, Account>()
    private val byUsername = linkedMapOf<String, String>() // username -> id

    override suspend fun findByUsername(username: String): Account? =
        byUsername[username]?.let { byId[it] }

    override suspend fun getById(id: String): Account? = byId[id]

    override fun observeById(id: String): Flow<Account?> =
        MutableStateFlow(byId[id]).asStateFlow()

    override suspend fun insert(account: Account) {
        if (byId.containsKey(account.id)) error("duplicate id: ${account.id}")
        if (byUsername.containsKey(account.username)) {
            throw android.database.sqlite.SQLiteConstraintException(
                "UNIQUE constraint failed: username=${account.username}"
            )
        }
        byId[account.id] = account
        byUsername[account.username] = account.id
    }

    override suspend fun count(): Int = byId.size
}

class FakeRelationshipDao : RelationshipDao {

    private val byId = linkedMapOf<String, Relationship>()
    // For [findPair]: keyed by pair, value is the *latest* row id. Updated on
    // every insert; update doesn't prune older ids because revoked rows are
    // kept for audit.
    private val pairLatest = mutableMapOf<Pair<String, String>, String>()

    override suspend fun insert(relationship: Relationship) {
        if (byId.containsKey(relationship.id)) error("duplicate id: ${relationship.id}")
        byId[relationship.id] = relationship
        pairLatest[relationship.wardId to relationship.guardianId] = relationship.id
    }

    override suspend fun updateStatus(id: String, status: String, acceptedAt: Long?) {
        val existing = byId[id] ?: return
        byId[id] = existing.copy(status = status, acceptedAt = acceptedAt)
    }

    override suspend fun getById(id: String): Relationship? = byId[id]

    override suspend fun findPair(wardId: String, guardianId: String): Relationship? =
        pairLatest[wardId to guardianId]?.let { byId[it] }

    override fun observeMyWards(accountId: String): Flow<List<Relationship>> =
        MutableStateFlow(
            byId.values.filter { it.guardianId == accountId && it.status != "REVOKED" }
                .sortedByDescending { it.invitedAt }
        ).asStateFlow()

    override fun observeMyGuardians(accountId: String): Flow<List<Relationship>> =
        MutableStateFlow(
            byId.values.filter { it.wardId == accountId && it.status != "REVOKED" }
                .sortedByDescending { it.invitedAt }
        ).asStateFlow()

    override suspend fun forAccount(accountId: String): List<Relationship> =
        byId.values
            .filter { (it.wardId == accountId || it.guardianId == accountId) && it.status != "REVOKED" }
            .sortedByDescending { it.invitedAt }

    override suspend fun count(): Int = byId.size
}