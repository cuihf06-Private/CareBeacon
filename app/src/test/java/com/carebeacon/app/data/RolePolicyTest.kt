package com.carebeacon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RolePolicy].
 *
 * The product rule the tests encode:
 *  - Guardians configure reminders but never receive alerts.
 *  - Wards receive alerts but never configure reminders.
 *  - The on-demand "fire now" button only exists in demo mode.
 */
class RolePolicyTest {

    private fun guardianReminder(id: Long = 1L) = Reminder(
        id = id,
        ownerRole = RolePolicy.ROLE_GUARDIAN,
        wardId = "ward-1",
        guardianId = "guardian-1",
        title = "G-$id",
        note = "",
        hour = 8,
        minute = 0,
        weekMask = 0,
        nextTriggerAt = 0L,
        enabled = true,
    )

    private fun wardReminder(id: Long = 100L) = Reminder(
        id = id,
        ownerRole = RolePolicy.ROLE_WARD,
        wardId = "ward-1",
        guardianId = "guardian-1",
        title = "W-$id",
        note = "",
        hour = 8,
        minute = 0,
        weekMask = 0,
        nextTriggerAt = 0L,
        enabled = true,
    )

    @Test
    fun `visibleReminders hides everything when no role is set`() {
        val all = listOf(guardianReminder(1), wardReminder(101))
        val visible = RolePolicy.visibleReminders(all, localRole = null)
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `visibleReminders shows only the matching role regardless of demo mode`() {
        val all = listOf(guardianReminder(1), wardReminder(101))
        assertEquals(
            listOf<Long>(1L),
            RolePolicy.visibleReminders(all, RolePolicy.ROLE_GUARDIAN).map { it.id }
        )
        assertEquals(
            listOf<Long>(101L),
            RolePolicy.visibleReminders(all, RolePolicy.ROLE_WARD).map { it.id }
        )
    }

    @Test
    fun `canArm is true only when local device is ward AND reminder targets ward AND is enabled`() {
        val w = wardReminder()
        assertFalse(RolePolicy.canArm(w, localRole = null))
        assertFalse(RolePolicy.canArm(w, localRole = RolePolicy.ROLE_GUARDIAN))
        assertTrue(RolePolicy.canArm(w, localRole = RolePolicy.ROLE_WARD))
        assertFalse(RolePolicy.canArm(w.copy(enabled = false), localRole = RolePolicy.ROLE_WARD))
    }

    @Test
    fun `canArm is false even on a ward device for a guardian-owned reminder`() {
        val g = guardianReminder()
        assertFalse(RolePolicy.canArm(g, localRole = RolePolicy.ROLE_WARD))
    }

    @Test
    fun `canEdit is true only when local device is guardian AND reminder is guardian-owned`() {
        val g = guardianReminder()
        val w = wardReminder()
        assertTrue(RolePolicy.canEdit(g, RolePolicy.ROLE_GUARDIAN))
        assertFalse(RolePolicy.canEdit(g, RolePolicy.ROLE_WARD))
        assertFalse(RolePolicy.canEdit(g, null))
        assertFalse(RolePolicy.canEdit(w, RolePolicy.ROLE_GUARDIAN))
        assertFalse(RolePolicy.canEdit(w, RolePolicy.ROLE_WARD))
    }

    @Test
    fun `canFireOnDemand is false in production mode`() {
        assertFalse(RolePolicy.canFireOnDemand(RolePolicy.ROLE_GUARDIAN, demoMode = false))
        assertFalse(RolePolicy.canFireOnDemand(RolePolicy.ROLE_WARD, demoMode = false))
    }

    @Test
    fun `canFireOnDemand is true for any role when demo mode is on`() {
        assertTrue(RolePolicy.canFireOnDemand(RolePolicy.ROLE_GUARDIAN, demoMode = true))
        assertTrue(RolePolicy.canFireOnDemand(RolePolicy.ROLE_WARD, demoMode = true))
        assertFalse(RolePolicy.canFireOnDemand(null, demoMode = true))
    }

    @Test
    fun `strict invariant - a guardian's local reminder can never be armed on this device`() {
        // This is the rule the AlarmEngine enforces. Document it as a property test.
        val g = guardianReminder()
        for (role in listOf(null, RolePolicy.ROLE_GUARDIAN, RolePolicy.ROLE_WARD)) {
            assertFalse(
                "guardian reminder must never arm on localRole=$role",
                RolePolicy.canArm(g, role)
            )
        }
    }
}