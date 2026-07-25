package com.carebeacon.app.permissions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helpers for jumping to system settings screens that matter for surviving aggressive
 * background-kill behaviour on Chinese Android skins (MIUI/EMUI/ColorOS/OneUI/Funtouch).
 *
 * The document is explicit: "必须采用'前台服务保活' + setAlarmClock 最高级别闹钟 +
 * '引导用户开启自启动/无限制电池优化' + '本地离线存储策略'". This object contains the
 * platform-specific glue for the second and third of those.
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    // ----- Battery optimisation ---------------------------------------------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (se: SecurityException) {
            Log.w(TAG, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS unavailable, opening battery list", se)
            openBatteryOptimizationSettings(activity)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (se: Exception) {
            Log.w(TAG, "Battery optimization list unavailable", se)
        }
    }

    // ----- Overlay (SYSTEM_ALERT_WINDOW) ------------------------------------------

    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
    }

    // ----- Exact alarms -----------------------------------------------------------

    fun canScheduleExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.canScheduleExactAlarms()
        } else true

    fun requestExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${activity.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                activity.startActivity(intent)
            } catch (se: Exception) {
                Log.w(TAG, "Exact alarm settings unavailable", se)
            }
        }
    }

    // ----- Manufacturer auto-start screens ----------------------------------------

    /**
     * Try to open the "auto-start" / "background activity" management page on the
     * device's OEM. Returns true if a vendor-specific intent was launched, false if
     * the user must manually locate the toggle in regular system settings.
     */
    fun openManufacturerAutoStart(context: Context): Boolean {
        val intents = manufacturerIntents(context)
        for (component in intents) {
            val intent = Intent().apply {
                setComponent(component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Manufacturer intent $component not resolvable")
            }
        }
        return false
    }

    private fun manufacturerIntents(context: Context): List<ComponentName> {
        val intents = mutableListOf<ComponentName>()

        // Xiaomi MIUI
        intents += ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        // Huawei EMUI
        intents += ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        )
        intents += ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"
        )
        // OPPO ColorOS
        intents += ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        )
        intents += ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.startupapp.StartupAppListActivity"
        )
        intents += ComponentName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        )
        // vivo Funtouch
        intents += ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        )
        intents += ComponentName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
        )
        // Samsung
        intents += ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
        // Meizu
        intents += ComponentName(
            "com.meizu.safe",
            "com.meizu.safe.security.SHOW_APPSEC"
        )

        return intents
    }
}