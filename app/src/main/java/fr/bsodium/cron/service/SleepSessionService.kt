package fr.bsodium.cron.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.bsodium.cron.MainActivity
import fr.bsodium.cron.R
import fr.bsodium.cron.calendar.requestCalendarSync
import fr.bsodium.cron.location.LocationProvider
import fr.bsodium.cron.sensors.ActivityRecognitionMonitor
import fr.bsodium.cron.sensors.DebugSensorEventSink
import fr.bsodium.cron.sensors.SensorEventSink
import fr.bsodium.cron.sensors.ScreenStateMonitor
import fr.bsodium.cron.sensors.SleepTuning
import fr.bsodium.cron.session.ObservationLogRepository
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.session.model.latestEveningPlanLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
    private val observationLog by lazy { ObservationLogRepository(applicationContext) }

    /** Routes sensor events to both the debug UI and the session FSM. */
    private val fsmSink: SensorEventSink = object : SensorEventSink {
        override suspend fun emit(event: SessionEvent) {
            DebugSensorEventSink.emit(event)
            if (event.trigger == TriggerType.SleepOnset) {
                refreshLocationIfStale()
            }
            try {
                SessionFsm(applicationContext, SessionRepository(applicationContext)).onEvent(event)
            } catch (t: Throwable) {
                Log.e(TAG, "FSM error on sensor event ${event.trigger}", t)
            }
            // Keep ActivityRecognitionMonitor in sync with sleep state (emit only while asleep).
            when (event.trigger) {
                TriggerType.SleepOnset        -> activityRecognitionMonitor?.onSleepOnset()
                TriggerType.OutOfBedConfirmed -> activityRecognitionMonitor?.onWake()
                else                          -> Unit
            }
        }
    }

    /**
     * Overnight replans read [fr.bsodium.cron.session.model.SleepSession.latestEveningPlanLocation],
     * which stays pinned to wherever the user was at the ~8-10pm evening plan. If they end up sleeping
     * somewhere else, every replan for the rest of the night uses a stale commute origin (#223). A
     * genuine sleep onset is the natural moment to refresh it: appended directly (not through
     * [SessionFsm.onEvent]) so this silently updates the location without also firing its own AI turn
     * — the [TriggerType.SleepOnset] event right behind it is what should trigger the replan, once,
     * reading the now-fresh location.
     */
    private suspend fun refreshLocationIfStale() {
        val repo = SessionRepository(applicationContext)
        val session = repo.findCurrent() ?: return
        val current = session.latestEveningPlanLocation() ?: return
        val threshold = SleepTuning.staleLocationThreshold(applicationContext)
        if (!isLocationStale(current.capturedAt, Clock.System.now(), threshold)) return
        val fresh = LocationProvider(applicationContext).acquireForEveningPlan()
        if (fresh.source == LocationSource.Unavailable) return
        repo.appendEvent(
            session.id,
            SessionEvent(
                trigger = TriggerType.EveningPlan,
                timestamp = Clock.System.now(),
                data = EventData.EveningPlan(timezone = session.timezone, location = fresh, isManual = false),
            ),
        )
        Log.i(TAG, "Refreshed stale location for session ${session.id} (source=${fresh.source})")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        val isRearm = intent?.action == ACTION_REARM
        val eveningPlan = intent?.action == ACTION_EVENING_PLAN
        ensureNotificationChannel()
        // Keep location service typing for the full session because sleep-onset refreshes may need it.
        startForegroundService(includeLocation = true)

        // Rebuild monitors after a killed-service restart so REARM cannot silently no-op.
        val freshlyConstructed = screenStateMonitor == null
        if (freshlyConstructed) {
            screenStateMonitor = ScreenStateMonitor(
                applicationContext,
                fsmSink,
                serviceScope,
                sleepOnsetThreshold = SleepTuning.onsetThreshold(applicationContext),
                rearmThreshold = SleepTuning.rearmThreshold(applicationContext),
                outOfBedThreshold = SleepTuning.outOfBedConfirmThreshold(applicationContext),
                rawLog = observationLog,
            ).also { it.start() }
        }
        if (activityRecognitionMonitor == null) {
            activityRecognitionMonitor = ActivityRecognitionMonitor(
                applicationContext,
                fsmSink,
                serviceScope,
                sustainedMovementThreshold = SleepTuning.sustainedMovementThreshold(applicationContext),
                onSustainedMovement = { screenStateMonitor?.rearm() },
                rawLog = observationLog,
            ).also { it.start() }
        }

        // Only rearm explicitly on a fresh construction — start() already seeds onset detection from the current screen state, and rearm() would just redundantly reset the same latch.
        if (isRearm && !freshlyConstructed) {
            screenStateMonitor?.rearm()
        }

        if (eveningPlan) {
            val tzId = intent.getStringExtra(EXTRA_TIMEZONE) ?: TimeZone.currentSystemDefault().id
            serviceScope.launch { runEveningPlan(tzId) }
        }

        Log.i(TAG, "SleepSessionService started")
        return START_STICKY
    }

    /**
     * Captures a fresh location (this service is a running location FGS, so [LocationProvider]'s
     * `getCurrentLocation` is "in use" and unthrottled) and emits the evening_plan event that
     * creates the session and kicks the first AI turn.
     */
    private suspend fun runEveningPlan(tzId: String) {
        try {
            // Pull fresh calendar data before the read; the location fetch below gives it time.
            requestCalendarSync()
            val location = LocationProvider(applicationContext).acquireForEveningPlan()
            val event = SessionEvent(
                trigger = TriggerType.EveningPlan,
                timestamp = Clock.System.now(),
                data = EventData.EveningPlan(timezone = tzId, location = location),
            )
            SessionFsm(applicationContext, SessionRepository(applicationContext)).onEvent(event)
            Log.i(TAG, "Evening plan session started (location_source=${location.source})")
        } catch (t: Throwable) {
            Log.e(TAG, "Evening plan setup failed", t)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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

    private fun startForegroundService(includeLocation: Boolean) {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            // Only add the location type when permission is granted — API 34 throws otherwise.
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (includeLocation && hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notif, type)
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
        const val ACTION_EVENING_PLAN = "fr.bsodium.cron.SLEEP_SESSION_EVENING_PLAN"
        const val EXTRA_TIMEZONE = "timezone"
        private const val TAG = "SleepSessionService"

        /** How old the evening-plan location can get before a genuine sleep onset refreshes it (#223). */
        private val STALE_LOCATION_THRESHOLD = 4.hours

        /** Pure staleness decision — unit-testable. */
        internal fun isLocationStale(
            capturedAt: Instant,
            now: Instant,
            threshold: Duration = STALE_LOCATION_THRESHOLD,
        ): Boolean = now - capturedAt >= threshold

        fun startIntent(context: Context): Intent =
            Intent(context, SleepSessionService::class.java)

        /** Starts the service to capture a fresh location (in-FGS) and bootstrap the evening plan. */
        fun eveningPlanIntent(context: Context, timezoneId: String): Intent =
            Intent(context, SleepSessionService::class.java).apply {
                action = ACTION_EVENING_PLAN
                putExtra(EXTRA_TIMEZONE, timezoneId)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, SleepSessionService::class.java).apply { action = ACTION_STOP }

        fun rearmIntent(context: Context): Intent =
            Intent(context, SleepSessionService::class.java).apply { action = ACTION_REARM }
    }
}
