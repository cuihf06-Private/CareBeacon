package com.carebeacon.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.carebeacon.app.data.Reminder
import java.util.Calendar

/**
 * The alarm engine.
 *
 * The product spec is emphatic: alarms MUST use [AlarmManager.setAlarmClock]. That call gets the
 * highest priority Android grants; it surfaces in the system status bar and survives doze on
 * AOSP/Google builds. For Chinese OEMs we additionally rely on the user enabling autostart +
 * ignoring battery optimisations — see [com.carebeacon.app.permissions.PermissionHelper].
 */
class AlarmEngine(private val context: Context) {

    private val am: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Replaces all alarms for the given list (idempotent). */
    fun rescheduleAll(reminders: List<Reminder>) {
        reminders.forEach { schedule(it) }
    }

    /** Schedule (or reschedule) a single reminder. */
    fun schedule(reminder: Reminder) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        val triggerAt = nextTrigger(reminder)
        if (triggerAt <= System.currentTimeMillis()) {
            Log.w(TAG, "Computed trigger for ${reminder.title} is in the past; skipping")
            return
        }

        val pi = pendingIntentFor(reminder.id)
        try {
            // setAlarmClock shows the system alarm icon and uses the highest priority lane.
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, pendingIntentForShow(reminder)),
                pi
            )
            Log.i(TAG, "Scheduled alarm id=${reminder.id} '${reminder.title}' at $triggerAt")
        } catch (se: SecurityException) {
            // On Android 12+ the user must grant SCHEDULE_EXACT_ALARM. Fall back to
            // setAndAllowWhileIdle so we still fire, just less precisely.
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
        val flags = pendingIntentFlags()
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent, flags
        )
    }

    /** Pending intent shown in the system status bar when an alarm is upcoming. */
    private fun pendingIntentForShow(reminder: Reminder): PendingIntent {
        val intent = Intent(context, com.carebeacon.app.MainActivity::class.java).apply {
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

    /**
     * Compute the next epoch-millis at which the alarm should fire given the reminder's
     * wall-clock time + repeat mask.
     */
    private fun nextTrigger(reminder: Reminder): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // One-shot (no repeat mask): fire today; if past, push to tomorrow.
        if (reminder.weekMask == 0) {
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }
        // Repeating: walk up to 7 days until we find a matching weekday.
        for (i in 0..7) {
            if (i > 0) target.add(Calendar.DAY_OF_YEAR, 1)
            // Calendar.SUNDAY = 1 ... SATURDAY = 7. Convert to our bitmask convention
            // (bit0 = Monday ... bit6 = Sunday).
            val cal = target.get(Calendar.DAY_OF_WEEK)
            val bitIndex = if (cal == Calendar.SUNDAY) 6 else cal - Calendar.MONDAY
            val mask = (reminder.weekMask shr bitIndex) and 1
            if (mask == 1 && target.timeInMillis > now.timeInMillis) {
                return target.timeInMillis
            }
        }
        // Shouldn't happen — weekMask contained at least one bit, otherwise it was a one-shot.
        return target.timeInMillis
    }

    companion object {
        private const val TAG = "AlarmEngine"
    }
}