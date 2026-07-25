package com.carebeacon.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account-aware reminder policy tests. Replaces the device-role tests that
 * PR3 left around. The invariants encoded here:
 *  - A reminder is visible to the ward whose [Reminder.wardId] matches.
 *  - A reminder is visible to the guardian whose [Reminder.guardianId] matches.
 *  - The same account can be both a guardian (for some wards) and a ward
 *    (under some guardians) — the filtering is per-side, not exclusive.
 *  - The alarm engine arms only reminders whose [Reminder.wardId] is the
 *    current account, regardless of side.
 *  - Editors only work for the author (guardian).
 */
class RolePolicyTest {

    private fun rem(
        id: Long = 1L,
        wardId: String = "ward-1",
        guardianId: String = "guardian-1",
        enabled: Boolean = true,
    ) = Reminder(
        id = id,
        ownerRole = RolePolicy.ROLE_GUARDIAN,
        wardId = wardId,
        guardianId = guardianId,
        title = "r-$id",
        note = "",
        hour = 8,
        minute = 0,
        weekMask = 0,
        nextTriggerAt = 0L,
        enabled = enabled,
    )

    @Test
    fun `visibleRemindersForAccount requires a non-blank account id`() {
        val r = rem()
        assertTrue(RolePolicy.visibleRemindersForAccount(listOf(r), null, ReminderSide.WARD).isEmpty())
        assertTrue(RolePolicy.visibleRemindersForAccount(listOf(r), "", ReminderSide.WARD).isEmpty())
    }

    @Test
    fun `ward view shows only reminders whose wardId matches`() {
        val a = rem(id = 1, wardId = "alice", guardianId = "bob")
        val b = rem(id = 2, wardId = "alice", guardianId = "carol")
        val c = rem(id = 3, wardId = "bob", guardianId = "alice")
        val visible = RolePolicy.visibleRemindersForAccount(
            listOf(a, b, c), "alice", ReminderSide.WARD
        )
        assertTrue(visible.any { it.id == 1L })
        assertTrue(visible.any { it.id == 2L })
        assertFalse(visible.any { it.id == 3L })
    }

    @Test
    fun `guardian view shows only reminders whose guardianId matches`() {
        val a = rem(id = 1, wardId = "alice", guardianId = "bob")
        val b = rem(id = 2, wardId = "bob", guardianId = "alice")
        val visible = RolePolicy.visibleRemindersForAccount(
            listOf(a, b), "alice", ReminderSide.GUARDIAN
        )
        assertTrue(visible.any { it.id == 2L })
        assertFalse(visible.any { it.id == 1L })
    }

    @Test
    fun `pre-migration junk with empty ids is dropped`() {
        val junk = rem(id = 1, wardId = "", guardianId = "")
        val real = rem(id = 2, wardId = "alice", guardianId = "alice")
        assertTrue(
            RolePolicy.visibleRemindersForAccount(listOf(junk, real), "alice", ReminderSide.WARD)
                .none { it.id == 1L }
        )
    }

    @Test
    fun `canArm is true only when current account is the ward`() {
        val r = rem(wardId = "me")
        assertTrue(RolePolicy.canArm(r, "me"))
        assertFalse(RolePolicy.canArm(r, "someone-else"))
        assertFalse(RolePolicy.canArm(r, null))
    }

    @Test
    fun `canArm is false when reminder is disabled`() {
        val r = rem(wardId = "me", enabled = false)
        assertFalse(RolePolicy.canArm(r, "me"))
    }

    @Test
    fun `canEdit is true only for the author`() {
        val r = rem(guardianId = "bob", wardId = "alice")
        assertTrue(RolePolicy.canEdit(r, "bob"))
        assertFalse(RolePolicy.canEdit(r, "alice"))
        assertFalse(RolePolicy.canEdit(r, null))
    }
}