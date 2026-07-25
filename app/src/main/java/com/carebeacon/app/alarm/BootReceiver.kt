package com.carebeacon.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.data.RolePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * On boot, all AlarmManager schedules are wiped. We re-arm every reminder that
 * the local device is allowed to fire.
 *
 * The role gate is read from [com.carebeacon.app.data.RoleStore]: a Guardian
 * device will pick up ward-role reminders from the DB and immediately cancel
 * them, satisfying the design rule that a Guardian never receives alerts.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        Log.i(TAG, "Boot completed — re-arming alarms under strict role gate")
        val pendingResult = goAsync()
        val app = context.applicationContext as CareBeaconApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localRole = app.roleStore.role.first()
                val reminders = app.database.reminderDao().allEnabled()
                AlarmEngine(app).rescheduleAll(reminders, localRole)
                if (localRole == RolePolicy.ROLE_WARD && reminders.any { RolePolicy.canArm(it, localRole) }) {
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