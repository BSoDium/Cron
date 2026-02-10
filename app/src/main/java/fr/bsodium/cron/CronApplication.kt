package fr.bsodium.cron

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.worker.CalendarSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Application subclass that initializes:
 * - Notification channel for alarm notifications
 * - WorkManager periodic sync for calendar changes
 */
class CronApplication : Application() {

    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "cron_periodic_calendar_sync"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        enqueuePeriodicSync()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                AlarmReceiver.CHANNEL_ID,
                "Cron Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wake-up alarm notifications scheduled by Cron"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(alarmSound, audioAttributes)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun enqueuePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            3, TimeUnit.HOURS,       // repeat every 3 hours
            30, TimeUnit.MINUTES     // flex interval
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(false) // alarms are important
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // don't restart if already enqueued
            syncRequest
        )
    }
}
