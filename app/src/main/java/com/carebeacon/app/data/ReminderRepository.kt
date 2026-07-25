package com.carebeacon.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Surface for reminder CRUD, scoped to an account on one side of the
 * relationship. Mirrors the responsibilities that [AppViewModel] used to do
 * inline; the ViewModel is the only caller and the room DAO is the only
 * implementation for now.
 *
 * Authorisation (can the current account write/read this reminder?) lives in
 * [RelationshipPolicy] and is checked by callers, not by the repository.
 */
interface ReminderRepository {

    /** Reminders authored by [accountId] (i.e. the account is the guardian side). */
    fun observeAuthoredBy(accountId: String): Flow<List<Reminder>>

    /** Reminders targeted at [accountId] (i.e. the account is the ward side). */
    fun observeTargetingWard(accountId: String): Flow<List<Reminder>>

    suspend fun allEnabledTargetingWard(accountId: String): List<Reminder>

    suspend fun upsert(reminder: Reminder): Long

    suspend fun delete(id: Long)
}

/**
 * Local-only implementation backed by Room. No caching — the DAO already gives
 * us a Flow.
 */
class LocalReminderRepository(
    private val dao: ReminderDao,
) : ReminderRepository {

    override fun observeAuthoredBy(accountId: String): Flow<List<Reminder>> =
        dao.observeAuthoredBy(accountId)

    override fun observeTargetingWard(accountId: String): Flow<List<Reminder>> =
        dao.observeTargetingWard(accountId)

    override suspend fun allEnabledTargetingWard(accountId: String): List<Reminder> =
        dao.allEnabledTargetingWard(accountId)

    override suspend fun upsert(reminder: Reminder): Long =
        if (reminder.id == 0L) dao.insert(reminder) else {
            dao.update(reminder); reminder.id
        }

    override suspend fun delete(id: Long) {
        val r = dao.get(id) ?: return
        dao.delete(r)
    }
}