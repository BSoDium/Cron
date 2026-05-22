package fr.bsodium.cron.sensors

import android.Manifest
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Wraps Google Play Services Activity Recognition Transition API.
 *
 * We use transitions (not the continuous API) for battery efficiency.
 * Subscribes to ENTER events on STILL, WALKING, RUNNING and routes them
 * to [sink] as session events:
 *
 *  - STILL → contributes to sleep onset detection (handled in
 *    [ScreenStateMonitor]; we emit a snapshot signal)
 *  - WALKING / RUNNING → emit MidSleepActivity events
 *
 * Requires ACTIVITY_RECOGNITION runtime permission.
 */
class ActivityRecognitionMonitor(
    private val context: Context,
    private val sink: SensorEventSink,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
        pendingIntent = null
        Log.i(TAG, "ActivityRecognitionMonitor stopped")
    }

    private fun handleEnter(activity: Int) {
        val type = when (activity) {
            DetectedActivity.STILL -> ActivityType.Still
            DetectedActivity.WALKING -> ActivityType.Walking
            DetectedActivity.RUNNING -> ActivityType.Running
            else -> return
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

    private fun transition(activityType: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(activityType)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build()

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_TRANSITIONS).apply { setPackage(context.packageName) }
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
    }
}
