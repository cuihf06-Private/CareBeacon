package com.carebeacon.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single reminder entry. Times are stored as wall-clock (hour/minute/weekday) plus the
 * absolute epoch-millis of the next fire. The alarm engine reads [nextTriggerAt] directly
 * when calling AlarmManager.setAlarmClock.
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Local id for now; in v2 the guardian_id/ward_id come from the server. */
    @ColumnInfo(name = "owner_role")
    val ownerRole: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "note")
    val note: String = "",

    /** Hour of day in 24h (0..23). */
    @ColumnInfo(name = "hour")
    val hour: Int,

    /** Minute of hour (0..59). */
    @ColumnInfo(name = "minute")
    val minute: Int,

    /** Days of week as a 7-bit mask, bit0 = Monday. 0 means "no repeat / one-shot". */
    @ColumnInfo(name = "week_mask")
    val weekMask: Int = 0,

    /** Pre-computed next trigger epoch-millis. */
    @ColumnInfo(name = "next_trigger_at")
    val nextTriggerAt: Long,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "audio_note_path")
    val audioNotePath: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROLE_GUARDIAN = "guardian"
        const val ROLE_WARD = "ward"
    }
}