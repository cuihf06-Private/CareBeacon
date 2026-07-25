package com.carebeacon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the account-aware visibility helper that PR2 introduces. The
 * device-role-based [RolePolicy.visibleReminders] keeps working until PR4
 * deletes it.
 */
class AccountAwareVisibilityTest {

    private fun rem(id: Long, wardId: String, guardianId: String) = Reminder(
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
        enabled = true,
    )

    @Test
    fun `empty account id yields empty list`() {
        val all = listOf(rem(1, "w", "g"))
        assertTrue(RolePolicy.visibleRemindersForAccount(all, null, ReminderSide.WARD).isEmpty())
        assertTrue(RolePolicy.visibleRemindersForAccount(all, "", ReminderSide.WARD).isEmpty())
    }

    @Test
    fun `ward view shows only reminders targeted at the account`() {
        val all = listOf(
            rem(1, wardId = "alice", guardianId = "bob"),
            rem(2, wardId = "alice", guardianId = "carol"),
            rem(3, wardId = "bob", guardianId = "alice"),
        )
        val aliceWards = RolePolicy.visibleRemindersForAccount(all, "alice", ReminderSide.WARD)
        assertEquals(listOf(1L, 2L), aliceWards.map { it.id })
    }

    @Test
    fun `guardian view shows only reminders authored by the account`() {
        val all = listOf(
            rem(1, wardId = "alice", guardianId = "bob"),
            rem(2, wardId = "alice", guardianId = "carol"),
            rem(3, wardId = "bob", guardianId = "alice"),
        )
        val aliceGuardians = RolePolicy.visibleRemindersForAccount(all, "alice", ReminderSide.GUARDIAN)
        assertEquals(listOf(3L), aliceGuardians.map { it.id })
    }

    @Test
    fun `reminders with empty ids are dropped`() {
        // Pre-migration junk that slipped past the backfill. Must not leak into
        // the UI under the account model.
        val all = listOf(
            rem(1, wardId = "", guardianId = ""),
            rem(2, wardId = "alice", guardianId = "alice"),
        )
        assertEquals(listOf(2L), RolePolicy.visibleRemindersForAccount(all, "alice", ReminderSide.WARD).map { it.id })
        assertEquals(listOf(2L), RolePolicy.visibleRemindersForAccount(all, "alice", ReminderSide.GUARDIAN).map { it.id })
    }
}