package fr.bsodium.cron.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import fr.bsodium.cron.alarm.AlarmRingingState
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Listens to ACTION_SCREEN_OFF / SCREEN_ON / USER_PRESENT broadcasts and synthesizes:
 *
 *  - [TriggerType.SleepOnset] when the screen has been off long enough **and** the room is dark
 *    (Google Clock's "motionless in a dark room"). Uncharged devices need a longer sustained window,
 *    since a phone set down somewhere is more likely than one charging at a bedside. Conditions are
 *    re-checked while the screen stays off, so onset still fires once the lights go out.
 *  - [TriggerType.OutOfBedConfirmed] on a genuine unlock mid-session — the strongest "awake" signal
 *    we have, so a pickup wakes the session instead of firing a plan on every handle.
 *
 * Receivers MUST be registered dynamically — static registration of ACTION_SCREEN_ON / OFF has been
 * blocked since Android 8.
 */
class ScreenStateMonitor(
    private val context: Context,
    private val sink: SensorEventSink,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val sleepOnsetThreshold: Duration = 20.minutes,
    private val rearmThreshold: Duration = REARM_ONSET_THRESHOLD,
    private val outOfBedThreshold: Duration = OUT_OF_BED_CONFIRM_THRESHOLD,
    private val lightReader: AmbientLightReader = AmbientLightReader(context),
    private val isAlarmRinging: () -> Boolean = { AlarmRingingState.isRinging },
) {

    private var screenOffSince: Instant? = null
    private var sleepOnsetEmitted: Boolean = false
    private var pendingOnset: Job? = null
    private var pendingOutOfBed: Job? = null
    /** True after [rearm] has been called; consumed by the next SleepOnset emission. */
    private var isRearm: Boolean = false
    /** The threshold the pending onset check is using; survives a screen-on/off blip during rearm. */
    private var currentOnsetThreshold: Duration = sleepOnsetThreshold
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_USER_PRESENT -> onUserPresent()
            }
        }
    }

    fun start() {
        lightReader.start()
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
            .onFailure { Log.w(TAG, "unregisterReceiver failed", it) }
        lightReader.stop()
        pendingOnset?.cancel()
        pendingOutOfBed?.cancel()
        Log.i(TAG, "ScreenStateMonitor stopped")
    }

    /**
     * Resets sleep-onset latch after the FSM moves to AWAKE. Uses a shorter threshold by default since
     * the user has already proven they can fall asleep tonight, and we want to catch a second sleep
     * onset quickly so the AI can re-arm the alarm without losing too much window.
     */
    fun rearm(threshold: Duration = rearmThreshold) {
        sleepOnsetEmitted = false
        isRearm = true
        currentOnsetThreshold = threshold
        screenOffSince = if (!powerManager.isInteractive) Clock.System.now() else null
        scheduleOnsetCheck(threshold)
    }

    private fun onScreenOff() {
        screenOffSince = Clock.System.now()
        // A re-lock before out-of-bed confirms aborts it — that's the fix for a momentary glance
        // in bed (pick up phone, put it back down) being mistaken for getting up.
        pendingOutOfBed?.cancel()
        Log.d(TAG, "Screen off — onset check scheduled (threshold=$currentOnsetThreshold)")
        scheduleOnsetCheck(currentOnsetThreshold)
    }

    private fun onScreenOn() {
        val offSince = screenOffSince ?: return
        val offDuration = Clock.System.now() - offSince
        screenOffSince = null
        pendingOnset?.cancel()
        Log.d(TAG, "Screen on after ${offDuration.inWholeSeconds}s off")
    }

    /**
     * A genuine unlock is a candidate "awake" signal, but on its own it doesn't distinguish a
     * momentary glance in bed (checking the time, a notification) from actually getting up — so it
     * only starts a [scheduleOutOfBedConfirm] debounce rather than emitting immediately. If we'd
     * latched sleep and the unlock is sustained, treat it as getting out of bed (one event → FSM
     * Awake → [rearm]) rather than firing a plan on every pickup.
     *
     * Suppressed while an alarm is actively ringing: [AlarmActivity][fr.bsodium.cron.ui.screens.alarm.AlarmActivity]
     * dismisses the keyguard as soon as it launches, so USER_PRESENT fires a beat before the user's
     * actual slide-to-dismiss gesture. Without this guard that unlock alone would drive the session to
     * Awake, and the dismiss gesture right behind it would then complete the session immediately —
     * collapsing the dismiss-while-asleep re-ring guarantee on the very first (and only) real
     * dismissal. Suppressing here leaves status Monitoring/ReMonitoring, so AlarmDismissed itself
     * carries the transition to Awake as intended.
     */
    private fun onUserPresent() {
        screenOffSince = null
        pendingOnset?.cancel()
        if (!sleepOnsetEmitted) return
        if (isAlarmRinging()) {
            Log.i(TAG, "User present while an alarm is ringing — treating as the dismiss unlock, not out-of-bed")
            return
        }
        scheduleOutOfBedConfirm()
    }

    /** Debounce-then-confirm, mirroring [scheduleOnsetCheck]'s shape: a genuine wake-up keeps the
     *  screen interactive past [outOfBedThreshold]; a glance re-locks or times out before then and
     *  [onScreenOff]/[stop] cancel this job, so [sleepOnsetEmitted] stays true and the session
     *  remains correctly latched as asleep. Reaching the end of the delay uncancelled already proves
     *  the interactive duration was met; [powerManager.isInteractive] is rechecked only as a safety
     *  net against the rare race where the screen times out just before its off-broadcast is delivered. */
    private fun scheduleOutOfBedConfirm() {
        pendingOutOfBed?.cancel()
        pendingOutOfBed = scope.launch {
            kotlinx.coroutines.delay(outOfBedThreshold)
            if (!shouldConfirmOutOfBed(outOfBedThreshold, outOfBedThreshold, powerManager.isInteractive)) {
                Log.i(TAG, "Unlock not sustained — treating as an in-bed glance, not out-of-bed")
                return@launch
            }
            sleepOnsetEmitted = false
            sink.emit(
                SessionEvent(
                    trigger = TriggerType.OutOfBedConfirmed,
                    timestamp = Clock.System.now(),
                    data = EventData.OutOfBedConfirmed(evidence = listOf("device_unlocked", "sustained_${outOfBedThreshold.inWholeSeconds}s")),
                )
            )
            Log.i(TAG, "Sustained unlock while asleep — out of bed")
        }
    }

    private fun scheduleOnsetCheck(threshold: Duration = sleepOnsetThreshold) {
        pendingOnset?.cancel()
        pendingOnset = scope.launch {
            kotlinx.coroutines.delay(threshold)
            while (isActive) {
                val since = screenOffSince ?: return@launch
                if (sleepOnsetEmitted) return@launch
                val screenOff = Clock.System.now() - since
                if (shouldEmitOnset(screenOff, threshold, lightReader.isDark(), batteryManager.isCharging)) {
                    emitOnset(since)
                    return@launch
                }
                kotlinx.coroutines.delay(ONSET_RECHECK_INTERVAL)
            }
        }
    }

    private suspend fun emitOnset(since: Instant) {
        sleepOnsetEmitted = true
        val wasRearm = isRearm
        isRearm = false
        sink.emit(
            SessionEvent(
                trigger = TriggerType.SleepOnset,
                timestamp = Clock.System.now(),
                data = EventData.SleepOnset(screenOffSince = since, rearm = wasRearm),
            )
        )
        Log.i(TAG, "Sleep onset emitted at ${Clock.System.now()} (rearm=$wasRearm)")
    }

    companion object {
        private const val TAG = "ScreenStateMonitor"
        /** Shorter threshold for re-arm; user has already proven they sleep tonight. */
        private val REARM_ONSET_THRESHOLD = 15.minutes
        /** How often to re-test the dark/charging gate while the screen stays off. */
        private val ONSET_RECHECK_INTERVAL = 5.minutes
        /** A momentary in-bed glance (checking the time, a notification) rarely keeps the screen
         *  interactive this long; a genuine wake-up comfortably does, and 90s is trivial next to the
         *  15-20 min onset windows, so a real wake-driven replan isn't noticeably delayed. */
        private val OUT_OF_BED_CONFIRM_THRESHOLD = 90.seconds

        /**
         * Pure onset decision — unit-testable. Sleep onset requires a dark room; an uncharged device
         * (more likely just set down than in bed) must stay dark + idle for twice as long. Conservative
         * by design: missing an onset only delays an auto-replan, a false one risks a wrong wake + cost.
         */
        internal fun shouldEmitOnset(
            screenOff: Duration,
            baseThreshold: Duration,
            isDark: Boolean,
            isCharging: Boolean,
        ): Boolean {
            if (!isDark) return false
            val required = if (isCharging) baseThreshold else baseThreshold * 2
            return screenOff >= required
        }

        /**
         * Pure out-of-bed decision — unit-testable. A genuine unlock ends sleep only if the screen
         * stayed interactive for [threshold]; a momentary pickup (re-locked/screen-off before then) is
         * a glance in bed, not getting up. Conservative like onset: a missed confirmation only delays
         * a replan, a false one wrongly ends tracking.
         */
        internal fun shouldConfirmOutOfBed(
            interactiveFor: Duration,
            threshold: Duration,
            stillInteractive: Boolean,
        ): Boolean = stillInteractive && interactiveFor >= threshold
    }
}
