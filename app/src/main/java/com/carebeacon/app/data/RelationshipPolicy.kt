package com.carebeacon.app.data

/**
 * Pure rules for [Relationship] invariants. Lives outside the DAO so it can be
 * unit-tested on the JVM without Room.
 *
 * Invariants the helpers below encode:
 *  - A relationship is uniquely identified by (wardId, guardianId).
 *  - Self-invite (wardId == guardianId) is legal in production.
 *  - A non-revoked pair can only be created once; re-invite requires revoking
 *    the previous row first.
 *
 * Reminder-side policy helpers (`isAuthor` / `isTarget`) live here too once
 * Reminder gains `wardId`/`guardianId` columns in PR2.
 */
object RelationshipPolicy {

    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_REVOKED = "REVOKED"

    /** True iff the (wardId, guardianId) pair has no active row. */
    @Suppress("UNUSED_PARAMETER")
    fun canInvite(
        wardId: String,
        guardianId: String,
        existing: Relationship?,
    ): Boolean {
        // Self-invite is legal; same-ward + same-guardian pair only collides if
        // there is already an active row.
        return existing == null || existing.status == STATUS_REVOKED
    }

    /** Active = non-revoked. Helper for callers that don't want to compare strings. */
    fun isActive(r: Relationship): Boolean = r.status != STATUS_REVOKED
}