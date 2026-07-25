package com.carebeacon.app.data

/**
 * Account-aware reminder policy. The old "device role" model is gone — the
 * only concept left is "given an account id and a side of the relationship,
 * which reminders should this user see / arm / edit?".
 *
 * Reminders with empty `wardId` / `guardianId` (pre-migration junk that
 * somehow slipped past the backfill) are dropped — they have no account
 * attribution and the UI cannot authoritatively show them.
 */
object RolePolicy {

    const val ROLE_GUARDIAN = "guardian"
    const val ROLE_WARD = "ward"

    /**
     * Account-aware visibility. Replaces the device-role-based filter. Filters
     * reminders to those targeted at [accountId] when viewing as a ward, or
     * authored by [accountId] when viewing as a guardian.
     */
    fun visibleRemindersForAccount(
        all: List<Reminder>,
        accountId: String?,
        side: ReminderSide,
    ): List<Reminder> {
        if (accountId.isNullOrBlank()) return emptyList()
        return when (side) {
            ReminderSide.GUARDIAN -> all.filter {
                it.guardianId == accountId && it.guardianId.isNotBlank()
            }
            ReminderSide.WARD -> all.filter {
                it.wardId == accountId && it.wardId.isNotBlank()
            }
        }
    }

    /**
     * Account-aware edit gate. A reminder can be edited only by its author
     * (the guardian). The UI hides the editor for everyone else.
     */
    fun canEdit(reminder: Reminder, accountId: String?): Boolean {
        if (accountId.isNullOrBlank()) return false
        return reminder.guardianId == accountId
    }

    /**
     * Account-aware arm gate. The alarm engine only arms reminders whose
     * [Reminder.wardId] is the current account. Guardians never arm.
     */
    fun canArm(reminder: Reminder, accountId: String?): Boolean {
        if (accountId.isNullOrBlank()) return false
        return reminder.enabled && reminder.wardId == accountId && reminder.wardId.isNotBlank()
    }
}

enum class ReminderSide { GUARDIAN, WARD }