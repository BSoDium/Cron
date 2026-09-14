package fr.bsodium.cron.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bsodium.cron.R
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.ui.screens.alarm.AlarmActivity

/**
 * Rings the alarm until dismissed or snoozed.
 *
 * A notification's sound and vibration (`NotificationChannel.setSound`/`vibrationPattern`) play
 * exactly once when the system posts it — there's no channel-level looping mechanism, so
 * [AlarmReceiver] used to produce a few seconds of sound and then silence (#158). This service owns
 * a looping [MediaPlayer] on the alarm audio stream plus a repeating [Vibrator] pattern instead,
 * both of which only stop on an explicit [ACTION_STOP].
 */
class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            stopSelf()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(AlarmReceiver.EXTRA_LABEL) ?: "Cron Alarm"
        val requestCode = intent?.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 0) ?: 0
        val sessionId = intent?.getStringExtra(AlarmConstants.EXTRA_SESSION_ID)
        val snoozeCount = intent?.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, 0) ?: 0

        releasePlaybackResources() // defensive: release a stale player if a second ALARM_FIRED overlaps this one
        startForegroundRinging(buildRingingNotification(label, requestCode, sessionId, snoozeCount))
        startLoopingSound()
        startContinuousVibration()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun startForegroundRinging(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(AlarmReceiver.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(AlarmReceiver.NOTIFICATION_ID, notification)
        }
    }

    private fun startLoopingSound() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(this@AlarmSoundService, soundUri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Failed to start looping alarm sound", it) }.getOrNull()
    }

    private fun startContinuousVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        // repeatIndex 0 — the whole pattern loops, matching a real alarm's insistent buzz.
        val effect = VibrationEffect.createWaveform(AlarmReceiver.ALARM_VIBRATION_PATTERN, 0)
        runCatching { vibrator?.vibrate(effect) }
            .onFailure { Log.w(TAG, "Failed to start continuous alarm vibration", it) }
    }

    private fun stopRinging() {
        releasePlaybackResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun releasePlaybackResources() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }.onFailure { Log.w(TAG, "Failed to stop alarm sound cleanly", it) }
            player.release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun buildRingingNotification(
        label: String,
        requestCode: Int,
        sessionId: String?,
        snoozeCount: Int,
    ): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(AlarmConstants.EXTRA_SESSION_ID, sessionId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, requestCode, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // No .setSound()/.setVibrate() here — on this app's minSdk (26+) a channel-based notification
        // always sources those from the channel, and the channel only ever plays them once. The
        // looping MediaPlayer/Vibrator above are the real ring; see the class KDoc.
        return NotificationCompat.Builder(this, AlarmReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Cron")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    companion object {
        private const val TAG = "AlarmSoundService"
        const val ACTION_STOP = "fr.bsodium.cron.ALARM_SOUND_STOP"

        fun stopIntent(context: Context): Intent =
            Intent(context, AlarmSoundService::class.java).apply { action = ACTION_STOP }
    }
}
