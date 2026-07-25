package com.carebeacon.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.carebeacon.app.MainActivity
import com.carebeacon.app.data.Reminder
import com.carebeacon.app.data.RolePolicy

/**
 * The alarm engine.
 *
 * Per design:
 *  - All scheduling uses [AlarmManager.setAlarmClock] — Android's highest priority lane.
 *  - Wakes the device even from deep doze on AOSP/Google.
 *  - Chinese OEM survival is layered on top via [com.carebeacon.app.permissions.PermissionHelper]
 *    and a foreground service.
 *
 * Account-aware role rule (this class enforces it):
 *  - Only arms reminders that pass [RolePolicy.canArm] for [accountId] — i.e.
 *    the reminder's `wardId` is the current account. A device logged in as a
 *    different account (or with no session) will explicitly cancel any stale
 *    alarms it finds, so account switches take effect immediately on the
 *    next arm cycle.
 */
class AlarmEngine(private val context: Context) {

    private val am: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Re-arm every reminder that this device is allowed to fire.
     * Reminders that fail [RolePolicy.canArm] are cancelled defensively.
     */
    fun rescheduleAll(reminders: List<Reminder>, accountId: String?) {
        reminders.forEach { schedule(it, accountId) }
    }

    /**
     * Schedule (or cancel) a single reminder based on the current account.
     *
     * If the device is not the ward for this reminder the alarm is cancelled.
     * That means a Guardian device, or a device logged out, will never fire,
     * even if the row leaked into the local DB through some other path.
     */
    fun schedule(reminder: Reminder, accountId: String?) {
        if (!RolePolicy.canArm(reminder, accountId)) {
            cancel(reminder.id)
            return
        }
        val triggerAt = TriggerCalculator.nextTrigger(
            now = System.currentTimeMillis(),
            hour = reminder.hour,
            minute = reminder.minute,
            weekMask = reminder.weekMask
        )
        if (triggerAt <= System.currentTimeMillis()) {
            Log.w(TAG, "Computed trigger for ${reminder.title} is in the past; cancelling")
            cancel(reminder.id)
            return
        }

        val pi = pendingIntentFor(reminder.id)
        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, pendingIntentForShow(reminder)),
                pi
            )
            Log.i(
                TAG,
                "Scheduled alarm id=${reminder.id} '${reminder.title}' at $triggerAt (accountId=$accountId)"
            )
        } catch (se: SecurityException) {
            Log.w(TAG, "setAlarmClock denied; falling back to setAndAllowWhileIdle", se)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(reminderId: Long) {
        am.cancel(pendingIntentFor(reminderId))
    }

    private fun pendingIntentFor(id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent, pendingIntentFlags()
        )
    }

    /** Pending intent shown in the system status bar when an alarm is upcoming. */
    private fun pendingIntentForShow(reminder: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("alarm_show_id", reminder.id)
        }
        return PendingIntent.getActivity(
            context, reminder.id.toInt() + 100000, intent, pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    companion object {
        private const val TAG = "AlarmEngine"
    }
}