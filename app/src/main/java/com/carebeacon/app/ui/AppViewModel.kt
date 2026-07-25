package com.carebeacon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.alarm.AlarmEngine
import com.carebeacon.app.alarm.AlarmReceiver
import com.carebeacon.app.data.AckLog
import com.carebeacon.app.data.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val cb: CareBeaconApp = app as CareBeaconApp
    private val dao = cb.database.reminderDao()
    private val ackDao = cb.database.ackLogDao()
    private val roleStore = cb.roleStore

    val role: StateFlow<String?> = roleStore.role.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    val reminders: StateFlow<List<Reminder>> = dao.observeAll().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val acks: StateFlow<List<AckLog>> = ackDao.observeAll().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val paired: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val _pairCode = MutableStateFlow<String?>(null)
    val pairCode: StateFlow<String?> = _pairCode.asStateFlow()

    init {
        viewModelScope.launch {
            // Pre-generate a pairing code for display in the Guardian screen.
            _pairCode.value = generateCode()
        }
    }

    fun setRole(role: String) {
        viewModelScope.launch { roleStore.setRole(role) }
    }

    fun pairWithGuardian(code: String) {
        // Local-only pairing stub — the server sync is Phase 4/5 work.
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
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            val reminder = Reminder(
                id = id ?: 0L,
                ownerRole = roleStore.role.first() ?: Reminder.ROLE_GUARDIAN,
                title = title.ifBlank { "未命名提醒" },
                note = note,
                hour = hour,
                minute = minute,
                weekMask = weekMask,
                nextTriggerAt = next.timeInMillis,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
            val savedId = if (id == null) {
                dao.insert(reminder)
            } else {
                dao.update(reminder); reminder.id
            }
            val engine = AlarmEngine(cb)
            engine.rescheduleAll(listOf(reminder.copy(id = savedId)))
        }
    }

    fun deleteReminder(r: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(r)
            AlarmEngine(cb).cancel(r.id)
        }
    }

    /**
     * Fire the alert immediately, bypassing the alarm schedule. Useful for the Guardian's
     * "立即触发" button to test the unignorable UI on the same device.
     */
    fun fireNow(r: Reminder) {
        val ctx = cb
        val intent = android.content.Intent(ctx, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, r.id)
        }
        ctx.sendBroadcast(intent)
    }

    private fun generateCode(): String =
        (1..6).map { Random.nextInt(0, 10) }.joinToString("")

    override fun onCleared() {
        super.onCleared()
    }
}