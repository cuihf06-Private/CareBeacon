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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carebeacon.app.permissions.PermissionHelper
import com.carebeacon.app.ui.AppViewModel
import com.carebeacon.app.ui.GuardianScreen
import com.carebeacon.app.ui.ReminderEditScreen
import com.carebeacon.app.ui.RoleSelectScreen
import com.carebeacon.app.ui.WardScreen
import com.carebeacon.app.ui.theme.CareBeaconTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; user choice persisted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = AppViewModel(application)

        maybeRequestNotificationPermission()

        setContent {
            CareBeaconTheme {
                val role by viewModel.role.collectAsState()
                val reminders by viewModel.reminders.collectAsState()
                when {
                    role == null -> RoleSelectScreen(
                        onGuardian = { viewModel.setRole("guardian") },
                        onWard = { viewModel.setRole("ward") }
                    )
                    role == "guardian" -> GuardianScreen(
                        viewModel = viewModel,
                        onAdd = { startActivity(Intent(this, ReminderEditActivity::class.java)) },
                        onTest = { viewModel.fireNow(it) }
                    )
                    role == "ward" -> WardScreen(
                        viewModel = viewModel,
                        onRequestPermissions = { requestWardPermissions() }
                    )
                }
            }
        }
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
        // Build a single dialog walking the user through every quirk.
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