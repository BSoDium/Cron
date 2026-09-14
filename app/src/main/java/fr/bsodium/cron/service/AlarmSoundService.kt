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
    private var currentLabel: String = "Cron Alarm"
    private var currentRequestCode: Int = 0
    private var currentSessionId: String? = null
    private var currentSnoozeCount: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACTIVITY_SHOWN -> {
                suppressFullScreenFallback(intent.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1))
                return START_NOT_STICKY
            }
        }

        currentLabel = intent?.getStringExtra(AlarmReceiver.EXTRA_LABEL) ?: "Cron Alarm"
        currentRequestCode = intent?.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 0) ?: 0
        currentSessionId = intent?.getStringExtra(AlarmConstants.EXTRA_SESSION_ID)
        currentSnoozeCount = intent?.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, 0) ?: 0

        releasePlaybackResources() // defensive: release a stale player if a second ALARM_FIRED overlaps this one
        startForegroundRinging(
            buildRingingNotification(currentLabel, currentRequestCode, currentSessionId, currentSnoozeCount, includeFullScreenIntent = true),
        )
        launchAlarmActivityDirectly()
        startLoopingSound()
        startContinuousVibration()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    /**
     * The notification's `setFullScreenIntent` isn't a reliable way to get [AlarmActivity] on screen
     * promptly: live testing (#214) caught Android's systemui itself firing that `PendingIntent` up to
     * ~10s after the alarm rang — holding it as a heads-up peek first — and playing its own alert tone
     * at that moment, audibly doubling this service's already-looping ring. A foreground service with
     * an alarm-category notification gets its own Background-Activity-Launch exemption, so launching
     * directly sidesteps that delay. The full-screen intent stays as a fallback for the rare case this
     * direct launch is itself blocked — see [suppressFullScreenFallback] for how that fallback gets
     * turned off once we know it wasn't needed.
     */
    private fun launchAlarmActivityDirectly() {
        runCatching {
            startActivity(buildAlarmActivityIntent(currentLabel, currentRequestCode, currentSessionId, currentSnoozeCount))
        }.onFailure { Log.w(TAG, "Direct AlarmActivity launch failed; relying on the notification's full-screen intent", it) }
    }

    /** [AlarmActivity] calls this back the moment it's actually created, confirming the direct launch
     *  worked — only then is it safe to drop the full-screen-intent fallback, since a blocked direct
     *  launch fails silently (no exception) rather than throwing. Ignores a stale signal from a ring
     *  this service has since moved on from (e.g. the AI alarm superseded by hard-latest moments later). */
    private fun suppressFullScreenFallback(requestCode: Int) {
        if (requestCode != currentRequestCode) return
        startForegroundRinging(
            buildRingingNotification(currentLabel, currentRequestCode, currentSessionId, currentSnoozeCount, includeFullScreenIntent = false),
        )
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

    private fun buildAlarmActivityIntent(label: String, requestCode: Int, sessionId: String?, snoozeCount: Int): Intent =
        Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(AlarmConstants.EXTRA_SESSION_ID, sessionId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
        }

    private fun buildRingingNotification(
        label: String,
        requestCode: Int,
        sessionId: String?,
        snoozeCount: Int,
        includeFullScreenIntent: Boolean,
    ): Notification {
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, requestCode, buildAlarmActivityIntent(label, requestCode, sessionId, snoozeCount),
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
            .apply { if (includeFullScreenIntent) setFullScreenIntent(fullScreenPendingIntent, true) }
            .build()
    }

    companion object {
        private const val TAG = "AlarmSoundService"
        const val ACTION_STOP = "fr.bsodium.cron.ALARM_SOUND_STOP"
        const val ACTION_ACTIVITY_SHOWN = "fr.bsodium.cron.ALARM_ACTIVITY_SHOWN"

        fun stopIntent(context: Context): Intent =
            Intent(context, AlarmSoundService::class.java).apply { action = ACTION_STOP }

        fun activityShownIntent(context: Context, requestCode: Int): Intent =
            Intent(context, AlarmSoundService::class.java).apply {
                action = ACTION_ACTIVITY_SHOWN
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            }
    }
}
