package fr.bsodium.cron

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.worker.HealthConnectPollWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CronApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        armEveningPlan()
        enqueueHealthConnectPoll()
    }

    private fun armEveningPlan() {
        appScope.launch {
            try {
                EveningPlanScheduler(this@CronApplication).armNext()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to arm evening plan", t)
            }
        }
    }

    private fun enqueueHealthConnectPoll() {
        val request = PeriodicWorkRequestBuilder<HealthConnectPollWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES,
        ).setConstraints(Constraints.Builder().build()).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HealthConnectPollWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // Alarm channel
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmChannel = NotificationChannel(
            AlarmReceiver.CHANNEL_ID,
            "Cron Alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Wake-up alarm notifications scheduled by Cron"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(alarmSound, audioAttributes)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(alarmChannel)

        // Sleep session foreground service channel
        if (nm.getNotificationChannel(SleepSessionService.CHANNEL_ID) == null) {
            val sessionChannel = NotificationChannel(
                SleepSessionService.CHANNEL_ID,
                "Sleep session",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification shown while Cron is tracking your sleep"
                setShowBadge(false)
            }
            nm.createNotificationChannel(sessionChannel)
        }

        // Planning result channel (debug builds only — silent informational notification)
        if (nm.getNotificationChannel(PLANNING_CHANNEL_ID) == null) {
            val planningChannel = NotificationChannel(
                PLANNING_CHANNEL_ID,
                "Planning result (debug)",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown after an AI evening plan completes (debug builds only)"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(planningChannel)
        }
    }

    companion object {
        private const val TAG = "CronApplication"
        const val PLANNING_CHANNEL_ID = "cron_planning_channel"
        const val PLANNING_NOTIFICATION_ID = 9004
    }
}
