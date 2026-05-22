package fr.bsodium.cron.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import fr.bsodium.cron.session.model.ActivityType
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Listens to ACTION_SCREEN_OFF / SCREEN_ON / USER_PRESENT broadcasts and
 * synthesizes two derived events:
 *
 *  - [TriggerType.SleepOnset] when the screen has been off continuously
 *    for [sleepOnsetThreshold].
 *  - [TriggerType.MidSleepActivity] when the screen turns on mid-session
 *    (during MONITORING_SLEEP).
 *
 * Receivers MUST be registered dynamically — static registration of
 * ACTION_SCREEN_ON / OFF has been blocked since Android 8.
 */
class ScreenStateMonitor(
    private val context: Context,
    private val sink: SensorEventSink,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val sleepOnsetThreshold: kotlin.time.Duration = 20.minutes,
) {

    private var screenOffSince: Instant? = null
    private var sleepOnsetEmitted: Boolean = false
    private var pendingOnset: Job? = null
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_USER_PRESENT -> Unit // unlock; treat like SCREEN_ON
            }
        }
    }

    fun start() {
        // Seed from the current state — service might start with the screen already off.
        if (!powerManager.isInteractive) {
            screenOffSince = Clock.System.now()
            scheduleOnsetCheck()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(receiver, filter)
        Log.i(TAG, "ScreenStateMonitor started")
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        pendingOnset?.cancel()
        Log.i(TAG, "ScreenStateMonitor stopped")
    }

    /** Resets sleep-onset latch after the FSM moves to MONITORING_AWAKE. */
    fun rearm(threshold: kotlin.time.Duration = sleepOnsetThreshold) {
        sleepOnsetEmitted = false
        screenOffSince = if (!powerManager.isInteractive) Clock.System.now() else null
        scheduleOnsetCheck(threshold)
    }

    private fun onScreenOff() {
        screenOffSince = Clock.System.now()
        scheduleOnsetCheck()
    }

    private fun onScreenOn() {
        val offSince = screenOffSince ?: return
        val offDuration = Clock.System.now() - offSince
        screenOffSince = null
        pendingOnset?.cancel()

        if (sleepOnsetEmitted) {
            // Already-sleeping → screen on means activity. Emit MidSleepActivity.
            scope.launch {
                sink.emit(
                    SessionEvent(
                        trigger = TriggerType.MidSleepActivity,
                        timestamp = Clock.System.now(),
                        data = EventData.MidSleepActivity(
                            activityType = ActivityType.Still,
                            screenOn = true,
                            durationSeconds = 0, // populated when screen turns off again
                        ),
                    )
                )
            }
        }
        Log.d(TAG, "Screen on after ${offDuration.inWholeSeconds}s off")
    }

    private fun scheduleOnsetCheck(threshold: kotlin.time.Duration = sleepOnsetThreshold) {
        pendingOnset?.cancel()
        pendingOnset = scope.launch {
            kotlinx.coroutines.delay(threshold.inWholeMilliseconds.milliseconds.inWholeMilliseconds)
            val since = screenOffSince ?: return@launch
            if (sleepOnsetEmitted) return@launch
            val onsetThresholdMet = (Clock.System.now() - since) >= threshold
            if (onsetThresholdMet) {
                sleepOnsetEmitted = true
                sink.emit(
                    SessionEvent(
                        trigger = TriggerType.SleepOnset,
                        timestamp = Clock.System.now(),
                        data = EventData.SleepOnset(
                            screenOffSince = since,
                            rearm = false,
                        ),
                    )
                )
                Log.i(TAG, "Sleep onset emitted at ${Clock.System.now()}")
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateMonitor"
    }
}
