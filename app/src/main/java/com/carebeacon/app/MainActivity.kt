package com.carebeacon.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.carebeacon.app.data.RolePolicy
import com.carebeacon.app.permissions.PermissionHelper
import com.carebeacon.app.ui.AppViewModel
import com.carebeacon.app.ui.AuthScreen
import com.carebeacon.app.ui.GuardianScreen
import com.carebeacon.app.ui.HomeScreen
import com.carebeacon.app.ui.InviteSheet
import com.carebeacon.app.ui.WardScreen
import com.carebeacon.app.ui.theme.CareBeaconTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; user choice persisted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        setContent {
            CareBeaconTheme {
                val session by viewModel.session.collectAsState()
                // Which legacy role screen to show while we're inside it. The
                // value flips to null when the user goes back to HomeScreen.
                // PR4 deletes this and the role-based screens entirely.
                var workspace by rememberSaveable { mutableStateOf<String?>(null) }
                var showInvite by remember { mutableStateOf(false) }

                when {
                    session == null -> AuthScreen(
                        showLegacyHint = false, // PR3 keeps this off; PR4 flips it on.
                        onLogin = { username, cb ->
                            viewModel.login(username) { result -> cb(result.map {}) }
                        },
                        onRegister = { username, displayName, cb ->
                            viewModel.register(username, displayName) { result -> cb(result.map {}) }
                        }
                    )
                    workspace == null -> HomeScreen(
                        viewModel = viewModel,
                        onEnterGuardianMode = {
                            viewModel.setRole(RolePolicy.ROLE_GUARDIAN)
                            workspace = "guardian"
                        },
                        onEnterWardMode = {
                            viewModel.setRole(RolePolicy.ROLE_WARD)
                            viewModel.armVisibleReminders()
                            startServiceCompat()
                            workspace = "ward"
                        },
                        onOpenInvite = { showInvite = true },
                    )
                    workspace == "guardian" -> GuardianScreen(
                        viewModel = viewModel,
                        demoMode = viewModel.demoMode.collectAsState().value,
                        onAdd = { startActivity(Intent(this, ReminderEditActivity::class.java)) },
                        onTest = viewModel::fireNow,
                        onBack = { workspace = null }
                    )
                    workspace == "ward" -> WardScreen(
                        viewModel = viewModel,
                        onRequestPermissions = ::requestWardPermissions,
                        onArmReminders = ::armWardReminders,
                        onBack = { workspace = null }
                    )
                }

                if (showInvite && session != null) {
                    InviteSheet(
                        onDismiss = { showInvite = false },
                        onSubmit = { username, cb ->
                            viewModel.inviteGuardian(
                                wardId = session!!,
                                guardianUsername = username,
                                onResult = { result -> cb(result.map {}) }
                            )
                        }
                    )
                }
            }
        }
    }

    private fun startServiceCompat() {
        val app = application as CareBeaconApp
        app.startWardService()
    }

    private fun armWardReminders() {
        viewModel.armVisibleReminders()
        startServiceCompat()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestWardPermissions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_battery_title)
            .setMessage(
                "${getString(R.string.perm_battery_desc)}\n\n" +
                    "${getString(R.string.perm_overlay_title)}\n" +
                    "${getString(R.string.perm_autostart_title)}\n" +
                    "${getString(R.string.perm_notifications_title)}"
            )
            .setPositiveButton(R.string.perm_battery_action) { _, _ ->
                PermissionHelper.requestIgnoreBatteryOptimizations(this)
                PermissionHelper.requestOverlayPermission(this)
                if (!PermissionHelper.canScheduleExactAlarms(this)) {
                    PermissionHelper.requestExactAlarmPermission(this)
                }
                PermissionHelper.openManufacturerAutoStart(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}