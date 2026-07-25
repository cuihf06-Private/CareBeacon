package com.carebeacon.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.carebeacon.app.alert.AlertActivity

/**
 * Receives the broadcast fired by [AlarmEngine] when an alarm time arrives.
 *
 * Responsibilities:
 *  1. Wake the device, optionally surface a notification.
 *  2. Launch [AlertActivity] with FLAG_ACTIVITY_NEW_TASK + show-on-lock-screen flags so the
 *     full-screen unignorable alert is brought to the foreground.
 *  3. After the user taps "我知道了", [AlertActivity] records an [com.carebeacon.app.data.AckLog]
 *     and the alarm engine schedules the next occurrence.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) {
            Log.w(TAG, "Received alarm broadcast without reminder id; ignoring")
            return
        }
        Log.i(TAG, "Alarm fired for reminderId=$reminderId")

        val launch = Intent(context, AlertActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(AlertActivity.EXTRA_REMINDER_ID, reminderId)
        }
        context.startActivity(launch)
    }

    companion object {
        const val ACTION_FIRE = "com.carebeacon.app.action.ALARM_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val TAG = "AlarmReceiver"
    }
}