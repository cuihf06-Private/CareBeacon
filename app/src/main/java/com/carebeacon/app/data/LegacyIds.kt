package com.carebeacon.app.data

/**
 * Stable identifiers used by the v2 → v3 migration when back-filling reminders
 * created under the old device-role model. The legacy account has username
 * "legacy" so the user can find their old data from the AuthScreen.
 *
 * Also used by AppViewModel for reminders created during PR2 (before PR3 wires
 * up the new AuthScreen + HomeScreen flow) — new rows are stamped with these
 * ids so they keep working end-to-end.
 */
const val LEGACY_ACCOUNT_ID = "00000000-0000-0000-0000-000000000001"
const val LEGACY_RELATIONSHIP_ID = "00000000-0000-0000-0000-000000000002"
const val LEGACY_USERNAME = "legacy"