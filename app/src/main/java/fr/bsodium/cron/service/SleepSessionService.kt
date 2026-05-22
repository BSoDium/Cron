package fr.bsodium.cron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bsodium.cron.MainActivity
import fr.bsodium.cron.R
import fr.bsodium.cron.sensors.ActivityRecognitionMonitor
import fr.bsodium.cron.sensors.DebugSensorEventSink
import fr.bsodium.cron.sensors.SensorEventSink
import fr.bsodium.cron.sensors.ScreenStateMonitor
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Long-running foreground service that hosts dynamically-registered
 * sensor receivers during a sleep session.
 *
 * Foreground service type is `specialUse` so we don't take a dependency
 * on Health Connect specifically and so the Play Console flow is
 * predictable (HEALTH type has been a moving target since 2024).
 *
 * Phase 4 scope: starts both [ScreenStateMonitor] and
 * [ActivityRecognitionMonitor], shows a low-importance notification, and
 * stops on explicit stop. Phase 5 wires the lifecycle to the FSM.
 */
class SleepSessionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenStateMonitor: ScreenStateMonitor? = null
    private var activityRecognitionMonitor: ActivityRecognitionMonitor? = null

    /** Routes sensor events to both the debug UI and the session FSM. */
    private val fsmSink: SensorEventSink = object : SensorEventSink {
        override suspend fun emit(event: SessionEvent) {
            DebugSensorEventSink.emit(event)
            try {
                SessionFsm(applicationContext, SessionRepository(applicationContext)).onEvent(event)
            } catch (t: Throwable) {
                Log.e(TAG, "FSM error on sensor event ${event.trigger}", t)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
            ACTION_REARM -> {
                screenStateMonitor?.rearm()
                return START_NOT_STICKY
            }
        }
        ensureNotificationChannel()
        startForegroundWithSpecialUse()

        if (screenStateMonitor == null) {
            screenStateMonitor = ScreenStateMonitor(applicationContext, fsmSink, serviceScope).also { it.start() }
        }
        if (activityRecognitionMonitor == null) {
            activityRecognitionMonitor = ActivityRecognitionMonitor(applicationContext, fsmSink, serviceScope).also { it.start() }
        }

        Log.i(TAG, "SleepSessionService started")
        return START_STICKY
    }

    private fun stop() {
        screenStateMonitor?.stop()
        screenStateMonitor = null
        activityRecognitionMonitor?.stop()
        activityRecognitionMonitor = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "SleepSessionService stopped")
    }

    override fun onDestroy() {
        screenStateMonitor?.stop()
        screenStateMonitor = null
        activityRecognitionMonitor?.stop()
        activityRecognitionMonitor = null
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithSpecialUse() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, SleepSessionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Cron is watching")
            .setContentText("Listening for the best moment to wake you.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .addAction(0, "Stop tracking", stop)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sleep session",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Persistent notification shown while Cron is tracking your sleep"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "cron_session_channel"
        const val NOTIFICATION_ID = 9010
        const val ACTION_STOP = "fr.bsodium.cron.SLEEP_SESSION_STOP"
        const val ACTION_REARM = "fr.bsodium.cron.SLEEP_SESSION_REARM"
        private const val TAG = "SleepSessionService"

        fun startIntent(context: Context): Intent =
            Intent(context, SleepSessionService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, SleepSessionService::class.java).apply { action = ACTION_STOP }

        fun rearmIntent(context: Context): Intent =
            Intent(context, SleepSessionService::class.java).apply { action = ACTION_REARM }
    }
}
