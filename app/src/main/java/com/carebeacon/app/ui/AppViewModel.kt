package com.carebeacon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.alarm.AlarmEngine
import com.carebeacon.app.data.Account
import com.carebeacon.app.data.AckLog
import com.carebeacon.app.data.DuplicateInvite
import com.carebeacon.app.data.GuardianNotFound
import com.carebeacon.app.data.InvalidCredentials
import com.carebeacon.app.data.LEGACY_ACCOUNT_ID
import com.carebeacon.app.data.Relationship
import com.carebeacon.app.data.RelationshipPolicy
import com.carebeacon.app.data.Reminder
import com.carebeacon.app.data.ReminderSide
import com.carebeacon.app.data.RolePolicy
import com.carebeacon.app.data.UsernameAlreadyTaken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val cb: CareBeaconApp = app as CareBeaconApp
    private val dao = cb.database.reminderDao()
    private val ackDao = cb.database.ackLogDao()
    private val accountRepo = cb.accountRepository
    private val relationshipRepo = cb.relationshipRepository

    /* ----- Session / account state ---------------------------------------- */

    /** Currently logged-in account id, or null. */
    val session: StateFlow<String?> = cb.sessionStore.currentAccountId.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Reactive view of the currently logged-in account, or null. */
    val currentAccount: StateFlow<Account?> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else cb.database.accountDao().observeById(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /* ----- Relationship state --------------------------------------------- */

    val myWards: StateFlow<List<Relationship>> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else relationshipRepo.observeMyWards(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myGuardians: StateFlow<List<Relationship>> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else relationshipRepo.observeMyGuardians(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Snapshot of every account referenced by any relationship in
     * [myWards] / [myGuardians], keyed by id. Lets the UI render displayNames
     * for the peers without a join. Empty map when no session.
     */
    val accountsById: StateFlow<Map<String, Account>> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap())
            else myWards.flatMapLatest { ws ->
                myGuardians.map { gs ->
                    val map = mutableMapOf<String, Account>()
                    for (r in ws + gs) {
                        cb.database.accountDao().getById(r.wardId)?.let { map[it.id] = it }
                        cb.database.accountDao().getById(r.guardianId)?.let { map[it.id] = it }
                    }
                    map
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /* ----- Reminder state (account-aware) --------------------------------- */

    /** Reminders authored by the current account (guardian-side view). */
    val remindersAsGuardian: StateFlow<List<Reminder>> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.observeAuthoredBy(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Reminders targeting the current account (ward-side view). */
    val remindersAsWard: StateFlow<List<Reminder>> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.observeTargetingWard(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** All ack logs for the current account (the ward-side view). */
    val acks: StateFlow<List<AckLog>> = cb.database.ackLogDao().observeAll().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    /* ----- Account / session API ----------------------------------------- */

    fun register(username: String, displayName: String, onResult: (Result<Account>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { accountRepo.register(username, displayName) }
            onResult(result)
        }
    }

    fun login(username: String, onResult: (Result<Account>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { accountRepo.login(username) }
            onResult(result)
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountRepo.logout()
        }
    }

    /**
     * Invite [guardianUsername] to act as a guardian for [wardId]. Both ids
     * resolve to real accounts. The row is created in ACCEPTED status (local
     * mock — the server will mediate acceptance in a later phase).
     */
    fun inviteGuardian(
        wardId: String,
        guardianUsername: String,
        onResult: (Result<Relationship>) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = runCatching {
                relationshipRepo.inviteGuardianByUsername(wardId, guardianUsername)
            }
            onResult(result)
        }
    }

    /** Marks the given relationship as REVOKED. */
    fun revokeRelationship(relationshipId: String) {
        viewModelScope.launch { relationshipRepo.revoke(relationshipId) }
    }

    /* ----- Reminder authoring --------------------------------------------- */

    /**
     * Creates or updates a reminder authored by the current account. The
     * reminder targets [wardId]; only the current account is allowed to do
     * this — the editor UI already gates by account but we double-check here.
     */
    fun saveReminder(
        id: Long?,
        wardId: String,
        title: String,
        hour: Int,
        minute: Int,
        weekMask: Int,
        note: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAccountId = session.first()
            if (currentAccountId == null) return@launch
            val reminder = Reminder(
                id = id ?: 0L,
                ownerRole = RolePolicy.ROLE_GUARDIAN,
                wardId = wardId,
                guardianId = currentAccountId,
                title = title.ifBlank { "未命名提醒" },
                note = note,
                hour = hour,
                minute = minute,
                weekMask = weekMask,
                nextTriggerAt = 0L,
                enabled = true,
                createdAt = System.currentTimeMillis(),
            )
            if (id == null) {
                dao.insert(reminder)
            } else {
                dao.update(reminder)
            }
            // Guardian devices never arm reminders on themselves; the alarm
            // engine will pick this up when the target ward next logs in.
        }
    }

    /** Bridge for the legacy GuardianScreen that doesn't pass a wardId. */
    fun saveReminderLegacy(
        id: Long?,
        title: String,
        hour: Int,
        minute: Int,
        weekMask: Int,
        note: String,
    ) {
        saveReminder(id, LEGACY_ACCOUNT_ID, title, hour, minute, weekMask, note)
    }

    fun deleteReminder(r: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAccountId = session.first() ?: return@launch
            if (!RolePolicy.canEdit(r, currentAccountId)) return@launch
            dao.delete(r)
            AlarmEngine(cb).cancel(r.id)
        }
    }

    /**
     * Re-arm every enabled reminder whose wardId is the current account.
     * Idempotent — safe to call multiple times.
     */
    fun armVisibleReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAccountId = session.first() ?: return@launch
            val all = dao.allEnabledTargetingWard(currentAccountId)
            AlarmEngine(cb).rescheduleAll(all, currentAccountId)
        }
    }
}

// Re-export exception types so callers can import them from the ViewModel
// package without reaching into data.
typealias AuthUsernameTaken = UsernameAlreadyTaken
typealias AuthInvalidCredentials = InvalidCredentials
typealias InviteGuardianNotFound = GuardianNotFound
typealias InviteDuplicate = DuplicateInvite