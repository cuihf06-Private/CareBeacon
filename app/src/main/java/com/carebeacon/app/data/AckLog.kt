package com.carebeacon.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A confirmation record produced when the Ward taps "我知道了".
 *
 * If [synced] is false the row has not yet been delivered to the server. The
 * [SyncWorker] walks over unsynced rows and re-attempts the POST when network
 * comes back, satisfying the document's "断网容灾" requirement.
 */
@Entity(tableName = "ack_logs")
data class AckLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "reminder_id")
    val reminderId: Long,

    @ColumnInfo(name = "title_snapshot")
    val titleSnapshot: String,

    @ColumnInfo(name = "acknowledged_at")
    val acknowledgedAt: Long,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false,
)