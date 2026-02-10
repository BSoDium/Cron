package fr.bsodium.cron.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.bsodium.cron.MainActivity
import fr.bsodium.cron.R

/**
 * Fires when a scheduled alarm triggers.
 * Shows a high-priority notification with the default alarm sound,
 * vibration, and dismiss/snooze action buttons.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_FIRED = "fr.bsodium.cron.ALARM_FIRED"
        const val ACTION_DISMISS = "fr.bsodium.cron.ALARM_DISMISS"
        const val ACTION_SNOOZE = "fr.bsodium.cron.ALARM_SNOOZE"

        const val EXTRA_REQUEST_CODE = "extra_request_code"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"

        const val CHANNEL_ID = "cron_alarm_channel"
        const val NOTIFICATION_ID = 9001
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_FIRED -> handleAlarmFired(context, intent)
            ACTION_DISMISS -> handleDismiss(context)
            ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun handleAlarmFired(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Cron Alarm"
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)

        ensureNotificationChannel(context)

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Full-screen intent to open the app
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, requestCode + 10000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_SNOOZE_COUNT, intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0))
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, requestCode + 20000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Cron")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze", snoozePendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun handleDismiss(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Cron Alarm"
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)

        // Dismiss the current notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        // Schedule a new alarm for snooze duration from now
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        val snoozeMillis = System.currentTimeMillis() + (10 * 60 * 1000) // 10 min default

        val snoozeAlarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_LABEL, "$label (snoozed)")
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount + 1)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, requestCode + 30000 + snoozeCount,
            snoozeAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(
            snoozeMillis,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        alarmManager.setAlarmClock(alarmClockInfo, snoozePendingIntent)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cron Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications from Cron"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(alarmSound, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
