package com.carebeacon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.alarm.AlarmEngine
import com.carebeacon.app.alarm.AlarmReceiver
import com.carebeacon.app.data.Account
import com.carebeacon.app.data.AckLog
import com.carebeacon.app.data.DuplicateInvite
import com.carebeacon.app.data.GuardianNotFound
import com.carebeacon.app.data.InvalidCredentials
import com.carebeacon.app.data.LEGACY_ACCOUNT_ID
import com.carebeacon.app.data.Relationship
import com.carebeacon.app.data.Reminder
import com.carebeacon.app.data.RolePolicy
import com.carebeacon.app.data.UsernameAlreadyTaken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val cb: CareBeaconApp = app as CareBeaconApp
    private val dao = cb.database.reminderDao()
    private val ackDao = cb.database.ackLogDao()
    private val roleStore = cb.roleStore
    private val accountRepo = cb.accountRepository
    private val relationshipRepo = cb.relationshipRepository

    /* ----- Legacy device-role state (deprecated; removed in PR4) ---------- */

    @Deprecated("Device role is replaced by account-based model in PR3.")
    val role: StateFlow<String?> = roleStore.role.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    @Deprecated("Demo mode only existed under the device-role model; gone in PR4.")
    val demoMode: StateFlow<Boolean> = roleStore.demoMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    /** Reminders filtered by the strict role rule — Guardian only sees its own. */
    val reminders: StateFlow<List<Reminder>> = combine(
        dao.observeAll(),
        roleStore.role,
        roleStore.demoMode
    ) { all, localRole, _ ->
        RolePolicy.visibleReminders(all, localRole)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** All ack logs for the local ward (no filter for now — single ward per device). */
    val acks: StateFlow<List<AckLog>> = ackDao.observeAll().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    /* ----- New account-based state ----------------------------------------- */

    /** Currently logged-in account id, or null. */
    val session: StateFlow<String?> = cb.sessionStore.currentAccountId.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    /** Reactive view of the currently logged-in account, or null. */
    @Suppress("OPT_IN_USAGE")
    val currentAccount: StateFlow<Account?> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else cb.database.accountDao().observeById(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All relationships where the current account appears on either side. */
    val myRelationships: StateFlow<List<Relationship>> = cb.sessionStore.currentAccountId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else relationshipRepo.forAccount(id).let { snap ->
                // Turn the one-shot snapshot into a Flow so the StateFlow machinery is happy.
                flowOf(snap)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pairCode = MutableStateFlow<String?>(null)
    val pairCode: StateFlow<String?> = _pairCode.asStateFlow()

    init {
        viewModelScope.launch {
            _pairCode.value = generateCode()
        }
    }

    /* ----- Legacy methods (deprecated) ------------------------------------- */

    @Deprecated("Replaced by account/relationship model.")
    fun setRole(role: String) {
        viewModelScope.launch { roleStore.setRole(role) }
    }

    @Deprecated("Demo mode only; removed in PR4.")
    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { roleStore.setDemoMode(enabled) }
    }

    @Deprecated("Replaced by inviteGuardian().")
    fun pairWithGuardian(code: String) {
        viewModelScope.launch { roleStore.setPairingCode(code) }
    }

    /* ----- New account-based API ------------------------------------------- */

    /**
     * Registers a brand-new account and signs in. Throws
     * [UsernameAlreadyTaken] if the username is in use.
     */
    fun register(username: String, displayName: String, onResult: (Result<Account>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { accountRepo.register(username, displayName) }
            onResult(result)
        }
    }

    /**
     * Logs in by username. Throws [InvalidCredentials] if no such account
     * exists.
     */
    fun login(username: String, onResult: (Result<Account>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { accountRepo.login(username) }
            onResult(result)
        }
    }

    /** Logs out the current account. No-op if already logged out. */
    fun logout() {
        viewModelScope.launch {
            accountRepo.logout()
            // Ward service is kept running; AlarmEngine logic in PR4 will gate it
            // on whether the current account has any ward relationships.
        }
    }

    /**
     * Invite [guardianUsername] to act as a guardian for [wardId]. Both
     * [wardId] and [guardianUsername] resolve to real accounts; the row is
     * created in ACCEPTED status (local mock — server mediates accept in a
     * later phase).
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

    fun saveReminder(
        id: Long?,
        title: String,
        hour: Int,
        minute: Int,
        weekMask: Int,
        note: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val localRole = roleStore.role.first()
            // Strict invariant: only the guardian may write to its own reminders.
            if (localRole != RolePolicy.ROLE_GUARDIAN) {
                return@launch
            }
            // PR2 bridge: until PR3 wires the new UI, new reminders are stamped
            // with the legacy ids so they remain visible/usable in the existing
            // single-device flow. PR4 deletes the legacy bridge.
            val reminder = Reminder(
                id = id ?: 0L,
                ownerRole = RolePolicy.ROLE_GUARDIAN,
                wardId = LEGACY_ACCOUNT_ID,
                guardianId = LEGACY_ACCOUNT_ID,
                title = title.ifBlank { "未命名提醒" },
                note = note,
                hour = hour,
                minute = minute,
                weekMask = weekMask,
                nextTriggerAt = 0L,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
            if (id == null) {
                dao.insert(reminder)
            } else {
                dao.update(reminder)
            }
            // Guardian devices never arm reminders on themselves — AlarmEngine
            // would refuse anyway via RolePolicy.canArm. The reminder is the
            // source of truth; the server (Phase 4/5) would replicate it to the
            // Ward. Without a server, demo mode simulates that round-trip via
            // the shared local DB.
        }
    }

    fun deleteReminder(r: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val localRole = roleStore.role.first()
            if (!RolePolicy.canEdit(r, localRole)) return@launch
            dao.delete(r)
            AlarmEngine(cb).cancel(r.id)
        }
    }

    /**
     * Re-arm all currently visible reminders. Called by WardScreen's "启动守护"
     * button. Idempotent — safe to call multiple times.
     */
    fun armVisibleReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            val localRole = roleStore.role.first()
            val all = dao.allEnabled()
            AlarmEngine(cb).rescheduleAll(all, localRole)
        }
    }

    /**
     * Fire the alert immediately on this device. Strictly for demo mode — the
     * production flow has the ward's own alarm scheduler do this. Gated by
     * [RolePolicy.canFireOnDemand].
     */
    fun fireNow(r: Reminder) {
        viewModelScope.launch {
            val localRole = roleStore.role.first()
            val demo = cb.roleStore.demoMode
            // First read of demoMode returns the latest snapshot.
            val demoEnabled = demo.first()
            if (!RolePolicy.canFireOnDemand(localRole, demoEnabled)) return@launch
            val ctx = cb
            val intent = android.content.Intent(ctx, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                putExtra(AlarmReceiver.EXTRA_REMINDER_ID, r.id)
            }
            ctx.sendBroadcast(intent)
        }
    }

    private fun generateCode(): String =
        (1..6).map { Random.nextInt(0, 10) }.joinToString("")
}

// Re-export exception types so callers can import them from the ViewModel
// package without reaching into data.
typealias AuthUsernameTaken = UsernameAlreadyTaken
typealias AuthInvalidCredentials = InvalidCredentials
typealias InviteGuardianNotFound = GuardianNotFound
typealias InviteDuplicate = DuplicateInvite