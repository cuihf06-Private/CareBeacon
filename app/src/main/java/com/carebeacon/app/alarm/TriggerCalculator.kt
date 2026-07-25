package com.carebeacon.app.alarm

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure date arithmetic for "when does this reminder next fire".
 *
 * Extracted from [AlarmEngine] so it can be unit-tested on the JVM without pulling
 * in [android.app.AlarmManager]. The contract mirrors the spec:
 *
 *  - weekMask == 0 → one-shot: fires today at HH:MM, or tomorrow if that time has
 *    already passed.
 *  - weekMask != 0 → repeats on the days whose bit is set. The first matching day
 *    strictly *after* [now] is returned. Bit 0 = Monday, bit 6 = Sunday.
 *
 * @param now epoch-millis reference instant (injectable for tests).
 * @param hour 0..23, [minute] 0..59.
 * @param weekMask 7-bit bitmap, 0 for one-shot.
 * @param tz timezone for the wall-clock interpretation.
 */
object TriggerCalculator {

    fun nextTrigger(
        now: Long,
        hour: Int,
        minute: Int,
        weekMask: Int,
        tz: TimeZone = TimeZone.getDefault()
    ): Long {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
        require(weekMask in 0..0b1111111) { "weekMask out of range: $weekMask" }

        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // One-shot: try today; if past, push to tomorrow.
        if (weekMask == 0) {
            if (cal.timeInMillis <= now) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // Repeating: walk forward up to 8 days until we find a matching weekday
        // strictly later than [now].
        for (i in 0..7) {
            if (i > 0) cal.add(Calendar.DAY_OF_YEAR, 1)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val bitIndex = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
            val matched = (weekMask shr bitIndex) and 1
            if (matched == 1 && cal.timeInMillis > now) {
                return cal.timeInMillis
            }
        }
        // Unreachable when weekMask != 0; defensive fallback.
        return cal.timeInMillis
    }
}