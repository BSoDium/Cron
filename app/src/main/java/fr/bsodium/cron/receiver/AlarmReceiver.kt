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
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bsodium.cron.MainActivity
import fr.bsodium.cron.R
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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

        private const val TAG = "AlarmReceiver"
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
        val kind = intent.getStringExtra(AlarmConstants.EXTRA_KIND) ?: "legacy"
        val sessionId = intent.getStringExtra(AlarmConstants.EXTRA_SESSION_ID)
        Log.i(TAG, "Alarm fired kind=$kind label=$label sessionId=$sessionId")

        if (kind == AlarmConstants.KIND_HARD_LATEST && !sessionId.isNullOrBlank()) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = SessionRepository(context)
                    SessionFsm(context, repository).onEvent(SessionEvent(
                        trigger = TriggerType.HardLatestFired,
                        timestamp = Clock.System.now(),
                        data = EventData.Empty,
                    ))
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to emit hard_latest_fired event", t)
                } finally {
                    pending.finish()
                }
            }
        }

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
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)

        // Emit alarm_dismissed to the FSM (completes the session, clears both alarms).
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = SessionRepository(context)
                val session = repository.findCurrent()
                if (session != null) {
                    SessionFsm(context, repository).onEvent(SessionEvent(
                        trigger = TriggerType.AlarmDismissed,
                        timestamp = Clock.System.now(),
                        data = EventData.Empty,
                    ))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to emit alarm_dismissed event", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Cron Alarm"

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)

        // Emit alarm_snoozed to the FSM; the FSM handles escalation and scheduling.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = SessionRepository(context)
                val session = repository.findCurrent()
                if (session != null) {
                    val fsm = SessionFsm(context, repository)
                    val event = SessionEvent(
                        trigger = TriggerType.AlarmSnoozed,
                        timestamp = Clock.System.now(),
                        data = EventData.AlarmInteraction(
                            snoozeDurationMinutes = 10,
                            snoozeCount = session.snoozeCount,
                        ),
                    )
                    fsm.onSnooze(session.id, event)
                } else {
                    // No session — fall back to a plain 10-min snooze.
                    scheduleSimpleSnooze(context, requestCode, label)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle snooze via FSM", t)
                scheduleSimpleSnooze(context, requestCode, label)
            } finally {
                pending.finish()
            }
        }
    }

    private fun scheduleSimpleSnooze(context: Context, requestCode: Int, label: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val snoozeMillis = System.currentTimeMillis() + 10 * 60 * 1000L
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_LABEL, "$label (snoozed)")
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode + 30000, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            android.app.AlarmManager.AlarmClockInfo(
                snoozeMillis,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
            pi,
        )
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
