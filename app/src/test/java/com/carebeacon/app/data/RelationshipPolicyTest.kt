package com.carebeacon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-rule tests for [RelationshipPolicy]. No Android types involved.
 */
class RelationshipPolicyTest {

    private fun rel(
        wardId: String = "ward-1",
        guardianId: String = "guardian-1",
        status: String = RelationshipPolicy.STATUS_ACCEPTED,
    ) = Relationship(
        id = "rel-1",
        wardId = wardId,
        guardianId = guardianId,
        status = status,
        invitedAt = 0L,
        acceptedAt = 0L,
    )

    @Test
    fun `canInvite is true when no existing row`() {
        assertTrue(RelationshipPolicy.canInvite("a", "b", existing = null))
    }

    @Test
    fun `canInvite is false when an active row already exists`() {
        assertFalse(
            RelationshipPolicy.canInvite("a", "b", existing = rel(status = RelationshipPolicy.STATUS_ACCEPTED))
        )
        assertFalse(
            RelationshipPolicy.canInvite("a", "b", existing = rel(status = RelationshipPolicy.STATUS_PENDING))
        )
    }

    @Test
    fun `canInvite is true again after the previous row is revoked`() {
        assertTrue(
            RelationshipPolicy.canInvite("a", "b", existing = rel(status = RelationshipPolicy.STATUS_REVOKED))
        )
    }

    @Test
    fun `self invite is legal - same account on both sides`() {
        // wardId == guardianId should not by itself block the invite.
        assertTrue(RelationshipPolicy.canInvite("me", "me", existing = null))
        assertTrue(RelationshipPolicy.canInvite("me", "me", existing = rel(
            wardId = "me", guardianId = "me", status = RelationshipPolicy.STATUS_REVOKED
        )))
        // But a duplicate active self-invite is still blocked.
        assertFalse(RelationshipPolicy.canInvite("me", "me", existing = rel(
            wardId = "me", guardianId = "me", status = RelationshipPolicy.STATUS_ACCEPTED
        )))
    }

    @Test
    fun `isActive mirrors the not-revoked rule`() {
        assertTrue(RelationshipPolicy.isActive(rel(status = RelationshipPolicy.STATUS_PENDING)))
        assertTrue(RelationshipPolicy.isActive(rel(status = RelationshipPolicy.STATUS_ACCEPTED)))
        assertFalse(RelationshipPolicy.isActive(rel(status = RelationshipPolicy.STATUS_REVOKED)))
    }

    @Test
    fun `status constants are stable - pins the contract callers depend on`() {
        assertEquals("PENDING", RelationshipPolicy.STATUS_PENDING)
        assertEquals("ACCEPTED", RelationshipPolicy.STATUS_ACCEPTED)
        assertEquals("REVOKED", RelationshipPolicy.STATUS_REVOKED)
    }
}