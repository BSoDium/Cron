package fr.bsodium.cron.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import fr.bsodium.cron.session.model.ActivityType
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Wraps Google Play Services Activity Recognition Transition API.
 *
 * We use transitions (not the continuous API) for battery efficiency.
 * Subscribes to ENTER events on STILL, WALKING, RUNNING and routes them
 * to [sink] as session events:
 *
 *  - STILL → contributes to sleep onset detection (handled in
 *    [ScreenStateMonitor]; we emit a snapshot signal)
 *  - WALKING / RUNNING → emit MidSleepActivity events, and — if uninterrupted by a STILL for
 *    [sustainedMovementThreshold] — invoke [onSustainedMovement] (#97)
 *
 * Requires ACTIVITY_RECOGNITION runtime permission.
 */
class ActivityRecognitionMonitor(
    private val context: Context,
    private val sink: SensorEventSink,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val sustainedMovementThreshold: Duration = 10.minutes,
    private val onSustainedMovement: () -> Unit = {},
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (!ActivityTransitionResult.hasResult(intent)) return
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            for (event in result.transitionEvents) {
                if (event.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) continue
                handleEnter(event.activityType)
            }
        }
    }

    private var pendingIntent: PendingIntent? = null
    private var sleepOnsetDetected = false
    private var continuousMovementSince: Instant? = null
    private var pendingSustainedMovement: Job? = null

    fun onSleepOnset() { sleepOnsetDetected = true }

    fun onWake() {
        sleepOnsetDetected = false
        pendingSustainedMovement?.cancel()
        continuousMovementSince = null
    }

    @SuppressLint("WrongConstant") // ContextCompat.RECEIVER_NOT_EXPORTED is the correct compat value for API < 33
    fun start(): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission not granted; monitor inactive")
            return false
        }
        context.registerReceiver(
            receiver,
            IntentFilter(ACTION_TRANSITIONS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val transitions = listOf(
            transition(DetectedActivity.STILL),
            transition(DetectedActivity.WALKING),
            transition(DetectedActivity.RUNNING),
        )
        val request = ActivityTransitionRequest(transitions)
        val pi = createPendingIntent()
        pendingIntent = pi
        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pi)
                .addOnFailureListener { Log.e(TAG, "requestActivityTransitionUpdates failed", it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on requestActivityTransitionUpdates", e)
            stop()
            return false
        }
        Log.i(TAG, "ActivityRecognitionMonitor started")
        return true
    }

    fun stop() {
        pendingIntent?.let { pi ->
            try {
                ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pi)
            } catch (_: SecurityException) {
                // permission revoked at runtime; nothing to clean up
            }
        }
        runCatching { context.unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "unregisterReceiver failed", it) }
        pendingIntent = null
        pendingSustainedMovement?.cancel()
        continuousMovementSince = null
        Log.i(TAG, "ActivityRecognitionMonitor stopped")
    }

    private fun handleEnter(activity: Int) {
        if (!sleepOnsetDetected) return
        val type = when (activity) {
            DetectedActivity.STILL -> ActivityType.Still
            DetectedActivity.WALKING -> ActivityType.Walking
            DetectedActivity.RUNNING -> ActivityType.Running
            else -> return // only STILL/WALKING/RUNNING are subscribed to; other Play Services codes can't arrive
        }
        if (type == ActivityType.Still) {
            pendingSustainedMovement?.cancel()
            continuousMovementSince = null
        } else {
            scheduleSustainedMovementCheck()
        }
        scope.launch {
            sink.emit(
                SessionEvent(
                    trigger = TriggerType.MidSleepActivity,
                    timestamp = Clock.System.now(),
                    data = EventData.MidSleepActivity(
                        activityType = type,
                        screenOn = false,
                        durationSeconds = 0,
                    ),
                )
            )
        }
    }

    /** A false [TriggerType.SleepOnset] from pocket detection (screen off + dark, but the phone is
     *  actually in a moving pocket) leaves [ScreenStateMonitor]'s onset latch stuck, so a genuine
     *  onset once the user truly stops moving never fires (#97). Uninterrupted WALKING/RUNNING for
     *  [sustainedMovementThreshold] is strong enough evidence of "not asleep" to call
     *  [onSustainedMovement] ([ScreenStateMonitor.rearm]) directly, rather than waiting on the AI to
     *  infer it from the advisory MidSleepActivity events alone. A STILL enter in between cancels the
     *  pending check — only an unbroken streak counts as sustained. */
    private fun scheduleSustainedMovementCheck() {
        if (continuousMovementSince != null) return // already tracking an unbroken streak
        val since = Clock.System.now()
        continuousMovementSince = since
        pendingSustainedMovement = scope.launch {
            kotlinx.coroutines.delay(sustainedMovementThreshold)
            if (isSustainedMovement(since, Clock.System.now(), sustainedMovementThreshold)) {
                Log.i(TAG, "Sustained movement for $sustainedMovementThreshold — rearming onset detection")
                onSustainedMovement()
            }
        }
    }

    private fun transition(activityType: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(activityType)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_TRANSITIONS).apply { setPackage(context.packageName) }
        // FLAG_MUTABLE required — Activity Recognition fills ActivityTransitionResult extras into the intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // pre-Q didn't require the runtime permission
        }
    }

    companion object {
        private const val TAG = "ActivityRecogMonitor"
        private const val ACTION_TRANSITIONS = "fr.bsodium.cron.ACTIVITY_TRANSITIONS"
        private const val REQUEST_CODE = 410001

        /** Pure sustained-movement decision — unit-testable. */
        internal fun isSustainedMovement(since: Instant, now: Instant, threshold: Duration): Boolean =
            now - since >= threshold
    }
}
