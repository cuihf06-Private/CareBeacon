package com.carebeacon.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A directed pairing: [wardId] is the account that receives reminders, [guardianId]
 * is the account that configures them.
 *
 * Self-invite (`wardId == guardianId`) is allowed — both in dev and in
 * production — so a single account can be its own guardian (useful for testing
 * and for users who configure their own reminders).
 *
 * Uniqueness on (wardId, guardianId) is enforced at the application layer by
 * [RelationshipPolicy] rather than by a DB unique index. That lets us keep
 * revoked rows for audit while rejecting a second *active* row for the same
 * pair.
 */
@Entity(
    tableName = "relationships",
    indices = [
        Index(value = ["ward_id"]),
        Index(value = ["guardian_id"]),
    ],
)
data class Relationship(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "ward_id")
    val wardId: String,

    @ColumnInfo(name = "guardian_id")
    val guardianId: String,

    /** One of [RelationshipPolicy.STATUS_PENDING] / ACCEPTED / REVOKED. */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "invited_at")
    val invitedAt: Long,

    @ColumnInfo(name = "accepted_at")
    val acceptedAt: Long? = null,
)