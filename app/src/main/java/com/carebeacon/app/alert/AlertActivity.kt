package com.carebeacon.app.alert

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.carebeacon.app.CareBeaconApp
import com.carebeacon.app.alarm.AlarmEngine
import com.carebeacon.app.data.AckLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "unignorable alert" the spec calls for.
 *
 * Behavior:
 *  1. Show over the lock screen, wake the screen, dismiss keyguard.
 *  2. Crank STREAM_ALARM volume to the max, ignoring mute.
 *  3. Loop a loud alarm tone.
 *  4. Display one giant red "我知道了" button — the only exit.
 *  5. On back press, prevent leaving — bring ourselves back to the front.
 *  6. On PAUSE (e.g. user Home-pressed), fall back to a SYSTEM_ALERT_WINDOW overlay that
 *     re-renders the same UI via [OverlayService].
 *
 * The user MUST tap "我知道了" — at which point we:
 *   - Stop the alarm tone.
 *   - Insert an [AckLog] (synced=false until the network sync worker picks it up).
 *   - Reschedule the reminder for its next occurrence.
 */
class AlertActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var reminderId: Long = -1L
    private var reminderTitle: String = ""
    private var originalAlarmVolume: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }

        reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)

        setContent {
            MaterialTheme {
                AlertScreen(
                    titleProvider = { reminderTitle },
                    onAck = ::onAcknowledged
                )
            }
        }

        lifecycleScope.launch {
            val app = applicationContext as CareBeaconApp
            val reminder = withContext(Dispatchers.IO) {
                if (reminderId >= 0) app.database.reminderDao().get(reminderId) else null
            }
            reminderTitle = reminder?.title ?: getString(com.carebeacon.app.R.string.app_name)
            startAlarmSound()
        }
    }

    override fun onResume() {
        super.onResume()
        ensureVolumeMax()
    }

    override fun onPause() {
        super.onPause()
        // The user is trying to escape via Home. Surface an overlay so we can't be ignored.
        if (!isFinishing) {
            try {
                OverlayService.show(this, reminderId, reminderTitle)
            } catch (e: Exception) {
                Log.w(TAG, "Overlay fallback failed", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        try {
            OverlayService.hide(this)
        } catch (_: Exception) { /* ignore */ }
    }

    @Deprecated("Deprecated in Java but still functional for blocking the back key")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        moveTaskToBack(true)
        OverlayService.show(this, reminderId, reminderTitle)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun startAlarmSound() {
        if (mediaPlayer != null) return
        ensureVolumeMax()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            Log.w(TAG, "No alarm ringtone available on this device")
            return
        }
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            try {
                setDataSource(this@AlertActivity, uri)
                isLooping = true
                prepare()
                start()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start default alarm tone", e)
            }
        }
        mediaPlayer = mp
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) { }
            it.release()
        }
        mediaPlayer = null
    }

    private fun ensureVolumeMax() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (originalAlarmVolume < 0) {
            originalAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
    }

    private fun restoreVolume() {
        if (originalAlarmVolume >= 0) {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            originalAlarmVolume = -1
        }
    }

    private fun onAcknowledged() {
        stopAlarmSound()
        restoreVolume()
        val app = applicationContext as CareBeaconApp
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
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
                        val engine = AlarmEngine(app)
                        // The alert only ever fires on a Ward device (see RolePolicy.canArm),
                        // so reading the role from the store is safe and self-consistent.
                        val localRole = app.roleStore.role.first()
                        engine.rescheduleAll(listOf(reminder), localRole)
                    }
                }
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        private const val TAG = "AlertActivity"
    }
}

@Composable
private fun AlertScreen(
    titleProvider: () -> String,
    onAck: () -> Unit
) {
    var displayedTitle by remember { mutableStateOf(titleProvider()) }
    LaunchedEffect(titleProvider) {
        // recompute when called
        displayedTitle = titleProvider()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFD32F2F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "⏰", fontSize = 96.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = displayedTitle.ifEmpty { "Reminder" },
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "请点击下方按钮确认",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onAck,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFD32F2F)
                ),
                modifier = Modifier.size(width = 240.dp, height = 96.dp)
            ) {
                Text(
                    text = "我知道了",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}