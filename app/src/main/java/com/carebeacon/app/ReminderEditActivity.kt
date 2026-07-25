package com.carebeacon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.carebeacon.app.ui.AppViewModel
import com.carebeacon.app.ui.ReminderEditScreen
import com.carebeacon.app.ui.theme.CareBeaconTheme

/**
 * Dedicated activity for editing a single reminder. Split out of [MainActivity] so the
 * Compose navigation graph stays simple and the activity can be opened directly from
 * any notification in future revisions.
 *
 * PR4: uses the account-aware guardian-side reminder list. When the caller
 * passes [EXTRA_WARD_ID] the saved reminder targets that account; otherwise
 * the current account is used (self-invite / single-user demo).
 */
class ReminderEditActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editingId = intent.getLongExtra(EXTRA_ID, -1L)
        val requestedWardId = intent.getStringExtra(EXTRA_WARD_ID)
        setContent {
            CareBeaconTheme {
                val reminders by viewModel.remindersAsGuardian.collectAsState()
                val account by viewModel.currentAccount.collectAsState()
                val initial = reminders.firstOrNull { it.id == editingId }
                ReminderEditScreen(
                    initial = initial,
                    onSave = { title, hour, minute, weekMask, note ->
                        // If the activity was opened for a specific ward use it,
                        // otherwise fall back to the current account.
                        val wardId = requestedWardId
                            ?: initial?.wardId
                            ?: account?.id
                            ?: return@ReminderEditScreen
                        viewModel.saveReminder(
                            id = initial?.id,
                            wardId = wardId,
                            title = title,
                            hour = hour,
                            minute = minute,
                            weekMask = weekMask,
                            note = note,
                        )
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_WARD_ID = "ward_id"
    }
}