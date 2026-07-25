package com.carebeacon.app.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.R
import com.carebeacon.app.alarm.AlarmEngine
import com.carebeacon.app.data.AckLog
import com.carebeacon.app.data.RolePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fallback "you can't ignore this" overlay.
 *
 * If the user mashes Home and escapes the [AlertActivity], this service spawns a
 * TYPE_APPLICATION_OVERLAY window covering the screen. It cannot be dismissed without
 * tapping the button. Note: this is the second line of defense — the activity is the
 * first, and most Android versions never let the user fully escape that.
 */
class OverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var reminderId: Long = -1L
    private var reminderTitle: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        reminderTitle = intent?.getStringExtra(EXTRA_REMINDER_TITLE) ?: ""
        ensureForeground()
        showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }

    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.alert_overlay_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.alert_overlay_channel_desc)
                    }
                )
            }
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlertActivity::class.java)
                .putExtra(AlertActivity.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.alert_overlay_channel))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_alert, null)
        view.findViewById<TextView>(R.id.overlay_title).text =
            reminderTitle.ifEmpty { getString(R.string.app_name) }
        view.findViewById<Button>(R.id.overlay_ack).setOnClickListener {
            acknowledgeAndStop()
        }
        try {
            wm.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add overlay view; permission missing?", e)
            stopSelf()
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "removeView failed", e)
            }
        }
        overlayView = null
    }

    private fun acknowledgeAndStop() {
        val app = applicationContext as CareBeaconApp
        GlobalScope.launch(Dispatchers.IO) {
            if (reminderId >= 0) {
                app.database.ackLogDao().insert(
                    AckLog(
                        reminderId = reminderId,
                        titleSnapshot = reminderTitle,
                        acknowledgedAt = System.currentTimeMillis(),
                        synced = false
                    )
                )
                val reminder = app.database.reminderDao().get(reminderId)
                if (reminder != null) {
                    val accountId = app.sessionStore.currentAccountId.first()
                    AlarmEngine(app).rescheduleAll(listOf(reminder), accountId)
                }
            }
        }
        hideOverlay()
        stopSelf()
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val CHANNEL_ID = "alert_overlay"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "OverlayService"

        fun show(context: Context, reminderId: Long, title: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_REMINDER_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}