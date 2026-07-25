package com.carebeacon.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single reminder entry. Times are stored as wall-clock (hour/minute/weekday) plus the
 * absolute epoch-millis of the next fire. The alarm engine reads [nextTriggerAt] directly
 * when calling AlarmManager.setAlarmClock.
 *
 * Authoring is identified by [guardianId] and [wardId] — the reminder targets the
 * ward account and is configured by the guardian account. [ownerRole] is the
 * legacy field kept until PR4 deletes it (it always implies `guardianId` on
 * insert, and the UI gate that used to read it is gone in PR3).
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Ward account id — the account that receives this reminder. */
    @ColumnInfo(name = "ward_id")
    val wardId: String,

    /** Guardian account id — the account that configured this reminder. */
    @ColumnInfo(name = "guardian_id")
    val guardianId: String,

    /**
     * Legacy field: which role this row was authored under the old device-role
     * model. Always equal to [RolePolicy.ROLE_GUARDIAN] for new rows. Kept for
     * one migration cycle so the old UI can still render reminders that haven't
     * been backfilled to a real account.
     */
    @ColumnInfo(name = "owner_role")
    val ownerRole: String = RolePolicy.ROLE_GUARDIAN,

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
        const val ROLE_GUARDIAN = RolePolicy.ROLE_GUARDIAN
        const val ROLE_WARD = RolePolicy.ROLE_WARD
    }
}