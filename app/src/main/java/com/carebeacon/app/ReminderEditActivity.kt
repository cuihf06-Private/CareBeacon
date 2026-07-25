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
 */
class ReminderEditActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editingId = intent.getLongExtra(EXTRA_ID, -1L)
        setContent {
            CareBeaconTheme {
                val reminders by viewModel.reminders.collectAsState()
                val initial = reminders.firstOrNull { it.id == editingId }
                ReminderEditScreen(
                    initial = initial,
                    onSave = { title, hour, minute, weekMask, note ->
                        viewModel.saveReminder(
                            id = initial?.id,
                            title = title,
                            hour = hour,
                            minute = minute,
                            weekMask = weekMask,
                            note = note
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
    }
}