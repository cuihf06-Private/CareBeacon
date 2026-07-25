package com.carebeacon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.alarm.AlarmEngine
import com.carebeacon.app.alarm.AlarmReceiver
import com.carebeacon.app.data.AckLog
import com.carebeacon.app.data.Reminder
import com.carebeacon.app.data.RolePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val cb: CareBeaconApp = app as CareBeaconApp
    private val dao = cb.database.reminderDao()
    private val ackDao = cb.database.ackLogDao()
    private val roleStore = cb.roleStore

    val role: StateFlow<String?> = roleStore.role.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

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

    private val _pairCode = MutableStateFlow<String?>(null)
    val pairCode: StateFlow<String?> = _pairCode.asStateFlow()

    init {
        viewModelScope.launch {
            _pairCode.value = generateCode()
        }
    }

    fun setRole(role: String) {
        viewModelScope.launch { roleStore.setRole(role) }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { roleStore.setDemoMode(enabled) }
    }

    fun pairWithGuardian(code: String) {
        viewModelScope.launch { roleStore.setPairingCode(code) }
    }

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
            val reminder = Reminder(
                id = id ?: 0L,
                ownerRole = RolePolicy.ROLE_GUARDIAN,
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