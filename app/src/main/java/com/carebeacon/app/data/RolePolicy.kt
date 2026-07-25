package com.carebeacon.app.data

/**
 * Pure policy object for role-based visibility and alarm-arming.
 *
 * The design document defines two roles:
 *  - **guardian** — configures reminders; never receives alerts.
 *  - **ward** — receives alerts; never configures.
 *
 * On a real deployment the Guardian and Ward live on different devices and the
 * server pushes reminders from one to the other. Without a server we support a
 * "demo mode" in which both roles can be exercised on the same device.
 *
 * This object holds the rules so they can be unit-tested on the JVM without
 * any Android framework. The actual database reads live elsewhere.
 */
object RolePolicy {

    const val ROLE_GUARDIAN = "guardian"
    const val ROLE_WARD = "ward"

    /**
     * Reminders visible to a screen that knows the local role.
     *
     * Guardians see reminders they authored (ownerRole = guardian), wards see
     * reminders targeted at them (ownerRole = ward). The rule is identical in
     * demo mode and production; demo mode only changes *how* the rows got into
     * the DB, not which rows the local UI shows.
     */
    fun visibleReminders(
        all: List<Reminder>,
        localRole: String?
    ): List<Reminder> {
        if (localRole == null) return emptyList()
        val target = if (localRole == ROLE_GUARDIAN) ROLE_GUARDIAN else ROLE_WARD
        return all.filter { it.ownerRole == target }
    }

    /**
     * Whether the given reminder should be armed by the local AlarmManager.
     *
     * Strict rule (the design's central invariant): only fire when this device is
     * the **ward** for that reminder. Guardians must never see their own
     * configured alerts.
     */
    fun canArm(reminder: Reminder, localRole: String?): Boolean {
        return localRole == ROLE_WARD &&
            reminder.ownerRole == ROLE_WARD &&
            reminder.enabled
    }

    /**
     * Whether the given reminder can be edited from this device's UI.
     *
     * Only guardians edit; wards must not see an editor at all.
     */
    fun canEdit(reminder: Reminder, localRole: String?): Boolean {
        return localRole == ROLE_GUARDIAN && reminder.ownerRole == ROLE_GUARDIAN
    }

    /**
     * Whether the user is allowed to fire a reminder on this device ad-hoc
     * (e.g. the Guardian's "立即触发" button). Production flow has no such
     * button; demo mode is the only legitimate reason to expose it.
     */
    fun canFireOnDemand(localRole: String?, demoMode: Boolean): Boolean {
        return demoMode && (localRole == ROLE_GUARDIAN || localRole == ROLE_WARD)
    }
}