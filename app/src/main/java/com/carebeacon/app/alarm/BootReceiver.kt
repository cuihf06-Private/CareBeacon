package com.carebeacon.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * On boot, all AlarmManager schedules are wiped. We re-arm every enabled reminder so
 * they fire correctly after a reboot, satisfying the document's "开机自启" requirement.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        Log.i(TAG, "Boot completed — re-arming alarms")
        val pendingResult = goAsync()
        val app = context.applicationContext as CareBeaconApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = app.database.reminderDao().allEnabled()
                val engine = AlarmEngine(app)
                engine.rescheduleAll(reminders.filter { it.ownerRole == Reminder.ROLE_WARD })
                if (reminders.any { it.ownerRole == Reminder.ROLE_WARD }) {
                    app.startWardService()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}