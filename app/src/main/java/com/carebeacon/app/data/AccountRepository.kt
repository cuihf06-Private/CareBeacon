package com.carebeacon.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Surface for account CRUD. The interface exists so the v1 (local-only) impl
 * and a future server-backed impl can be swapped without touching the UI or
 * ViewModel.
 *
 * Password-less for now: [login] resolves by username alone. A real backend
 * will fold credential verification into the same call.
 */
interface AccountRepository {

    /**
     * Creates a new account. Throws [UsernameAlreadyTaken] if the username is
     * already in the local store. Returns the freshly created account on
     * success.
     */
    suspend fun register(username: String, displayName: String): Account

    /**
     * Looks up an account by username. Throws [InvalidCredentials] when no
     * row matches — there is no password yet so the username is the whole
     * credential.
     */
    suspend fun login(username: String): Account

    /** Removes the in-memory "current account" pointer. */
    suspend fun logout()

    /** Snapshot of the currently logged-in account, if any. */
    suspend fun currentAccount(): Account?

    /** Reactive view of the currently logged-in account (re-emits on change). */
    fun observeCurrentAccount(): Flow<Account?>

    suspend fun findByUsername(username: String): Account?
}

class UsernameAlreadyTaken(message: String) : IllegalStateException(message)
class InvalidCredentials(message: String) : IllegalStateException(message)

/**
 * Local-only implementation backed by Room and [SessionStore]. Single-device,
 * single-process — concurrency is bounded by the coroutine dispatcher.
 */
class LocalAccountRepository(
    private val accountDao: AccountDao,
    private val sessionStore: SessionStore,
) : AccountRepository {

    override suspend fun register(username: String, displayName: String): Account {
        val normalized = username.trim()
        require(normalized.isNotEmpty()) { "username must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        if (accountDao.findByUsername(normalized) != null) {
            throw UsernameAlreadyTaken("username '$normalized' already exists")
        }
        val account = Account(
            id = UUID.randomUUID().toString(),
            username = normalized,
            displayName = displayName.trim(),
            createdAt = System.currentTimeMillis(),
        )
        accountDao.insert(account)
        sessionStore.setCurrent(account.id)
        return account
    }

    override suspend fun login(username: String): Account {
        val account = accountDao.findByUsername(username.trim())
            ?: throw InvalidCredentials("no account with username '$username'")
        sessionStore.setCurrent(account.id)
        return account
    }

    override suspend fun logout() {
        sessionStore.clear()
    }

    override suspend fun currentAccount(): Account? {
        val id = sessionStore.currentAccountId.firstOrNull() ?: return null
        return accountDao.getById(id)
    }

    @Suppress("OPT_IN_USAGE")
    override fun observeCurrentAccount(): Flow<Account?> =
        sessionStore.currentAccountId.flatMapLatest { id ->
            if (id == null) flowOf(null) else accountDao.observeById(id)
        }

    override suspend fun findByUsername(username: String): Account? =
        accountDao.findByUsername(username.trim())
}