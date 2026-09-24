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
import fr.bsodium.cron.session.model.Placement
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Listens to ACTION_SCREEN_OFF / SCREEN_ON / USER_PRESENT broadcasts and synthesizes:
 *
 *  - [TriggerType.SleepOnset] when the screen has been off long enough **and** the room is dark
 *    (Google Clock's "motionless in a dark room"). Uncharged devices need a longer sustained window,
 *    since a phone set down somewhere is more likely than one charging at a bedside. Conditions are
 *    re-checked while the screen stays off, so onset still fires once the lights go out. When
 *    [PlacementClassifier] reads [Placement.Enclosed] (pocket/drawer — dark at any hour, so the dark
 *    gate is meaningless there), [shouldEmitEnclosedOnset] is used instead: a longer window plus a
 *    clock reading inside the session's bedtime window.
 *  - [TriggerType.OutOfBedConfirmed] on a genuine unlock held open for [outOfBedThreshold], **or** on
 *    a brief unlock followed by a walking accelerometer signature (see [checkMotionForWake]) — the
 *    latter covers "unlocked for a few seconds, then pocketed and walked off," which a held-open
 *    unlock alone would miss entirely.
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
    private val rawLog: RawObservationSink = NoOpObservationSink,
    private val proximityReader: ProximitySource = ProximityReader(context),
    private val motionProbe: MotionSource = MotionProbe(context),
    private val motionProbeWindow: Duration = 90.seconds,
    private val onsetRecheckInterval: Duration = ONSET_RECHECK_INTERVAL,
    /** Resolved fresh on each onset recheck (cheap: one session/event read) rather than once at
     *  construction, so it reflects the current session even if this monitor outlives a rearm. Null
     *  when no session data is available -- Enclosed placement then falls back to the ordinary
     *  dark-gate check, same as before this existed, rather than never onsetting at all. */
    private val bedtimeWindowProvider: suspend () -> ClosedRange<Instant>? = { null },
) {

    private var screenOffSince: Instant? = null
    private var sleepOnsetEmitted: Boolean = false
    private var pendingOnset: Job? = null
    private var pendingOutOfBed: Job? = null
    /** Classified once per screen-off (see [refreshPlacement]); attached to the next SleepOnset. */
    private var lastPlacement: Placement = Placement.Unknown
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
        // Seed from the current state — service might start with the screen already off (e.g. a
        // killed-service restart, or the phone was already locked/pocketed at first start).
        if (!powerManager.isInteractive) {
            val now = Clock.System.now()
            screenOffSince = now
            scope.launch { refreshPlacement(now) }
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
        pendingOutOfBed = null
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
        val now = Clock.System.now()
        val stillOff = !powerManager.isInteractive
        screenOffSince = if (stillOff) now else null
        if (stillOff) scope.launch { refreshPlacement(now) }
        scheduleOnsetCheck(threshold)
    }

    private fun onScreenOff() {
        val now = Clock.System.now()
        screenOffSince = now
        scope.launch { refreshPlacement(now) }
        // See docs/sleep-detection-architecture.md §4 — a re-lock aborts the debounce below, but
        // checkMotionForWake still catches "unlocked briefly, then walked away with it."
        val hadPendingUnlockConfirm = pendingOutOfBed != null
        pendingOutOfBed?.cancel()
        pendingOutOfBed = null
        if (sleepOnsetEmitted && hadPendingUnlockConfirm) {
            scope.launch { checkMotionForWake() }
        }
        Log.d(TAG, "Screen off — onset check scheduled (threshold=$currentOnsetThreshold)")
        scheduleOnsetCheck(currentOnsetThreshold)
    }

    /** After a genuine unlock re-locks before [scheduleOutOfBedConfirm] would fire, a short
     *  accelerometer window distinguishes "set back down" from "walked away with it" without needing
     *  the unlock held open — see docs/sleep-detection-architecture.md §4. */
    private suspend fun checkMotionForWake() {
        val summary = motionProbe.sample(motionProbeWindow)
        rawLog.log(
            RawObservation(
                "motion_probe",
                Clock.System.now(),
                """{"classification":"${summary.classification.name}","peakDeltaG":${summary.peakDeltaG},"variance":${summary.variance}}""",
            )
        )
        if (!shouldConfirmWakeFromMotion(summary.classification)) return
        if (!sleepOnsetEmitted) return // already confirmed awake by another path meanwhile
        sleepOnsetEmitted = false
        sink.emit(
            SessionEvent(
                trigger = TriggerType.OutOfBedConfirmed,
                timestamp = Clock.System.now(),
                data = EventData.OutOfBedConfirmed(evidence = listOf("motion_walking")),
            )
        )
        Log.i(TAG, "Walking detected right after an unlock+re-lock — out of bed")
    }

    /** Samples proximity+lux once at screen-off and stores the result for [emitOnset] to attach to
     *  the SleepOnset event, and logs it unconditionally for hindsight relabeling (§5, F2 pattern). */
    private suspend fun refreshPlacement(now: Instant) {
        val covered = proximityReader.readCovered()
        lastPlacement = PlacementClassifier.classify(covered, lightReader.latestLux())
        rawLog.log(RawObservation("screen_off", now, """{"placement":"${lastPlacement.name.lowercase(Locale.ROOT)}"}"""))
    }

    private fun onScreenOn() {
        val offSince = screenOffSince ?: return
        val now = Clock.System.now()
        val offDuration = now - offSince
        screenOffSince = null
        pendingOnset?.cancel()
        scope.launch {
            val payload = buildJsonObject { put("offForSec", offDuration.inWholeSeconds) }.toString()
            rawLog.log(RawObservation("screen_on", now, payload))
        }
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
        val now = Clock.System.now()
        scope.launch { rawLog.log(RawObservation("user_present", now)) }
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
            if (!shouldConfirmOutOfBed(
                    interactiveFor = outOfBedThreshold,
                    threshold = outOfBedThreshold,
                    stillInteractive = powerManager.isInteractive,
                )
            ) {
                Log.i(TAG, "Unlock not sustained — treating as an in-bed glance, not out-of-bed")
                pendingOutOfBed = null
                return@launch
            }
            sleepOnsetEmitted = false
            pendingOutOfBed = null
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
                val now = Clock.System.now()
                val screenOff = now - since
                if (shouldEmitOnsetNow(now, screenOff, threshold)) {
                    emitOnset(since)
                    return@launch
                }
                kotlinx.coroutines.delay(onsetRecheckInterval)
            }
        }
    }

    /** Branches on [lastPlacement]: the ordinary dark gate is meaningless inside a pocket or drawer
     *  (it's dark in there at any hour), so [Placement.Enclosed] instead requires a longer screen-off
     *  window and a clock reading inside the session's bedtime window (see [shouldEmitEnclosedOnset]
     *  and docs/sleep-detection-architecture.md §4). Falls back to the ordinary gate when no bedtime
     *  window is resolvable, so a broken/missing session never means "can never onset." */
    private suspend fun shouldEmitOnsetNow(now: Instant, screenOff: Duration, threshold: Duration): Boolean {
        if (lastPlacement == Placement.Enclosed) {
            val window = bedtimeWindowProvider()
            if (window != null) return shouldEmitEnclosedOnset(screenOff, threshold, now, window)
        }
        return shouldEmitOnset(screenOff, threshold, lightReader.isDark(), batteryManager.isCharging)
    }

    private suspend fun emitOnset(since: Instant) {
        sleepOnsetEmitted = true
        val wasRearm = isRearm
        isRearm = false
        sink.emit(
            SessionEvent(
                trigger = TriggerType.SleepOnset,
                timestamp = Clock.System.now(),
                data = EventData.SleepOnset(screenOffSince = since, rearm = wasRearm, placement = lastPlacement),
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

        /** Pure decision — unit-testable. [MotionClassification.Walking] alone is trusted (it already
         *  requires both a footstep-scale peak and sustained rhythmic variance, per [MotionProbe]);
         *  [MotionClassification.Handled] is deliberately not enough on its own — a single jostle from
         *  setting the phone back down looks like that too. */
        internal fun shouldConfirmWakeFromMotion(classification: MotionClassification): Boolean =
            classification == MotionClassification.Walking

        /** A pocket/drawer needs a longer, undisturbed screen-off window before onset, since there's no
         *  dark-gate signal to lean on. Calibration knob. */
        private const val ENCLOSED_ONSET_MULTIPLIER = 1.5

        /**
         * Pure onset decision for [Placement.Enclosed] — unit-testable. No "no significant motion since
         * screen-off" corroboration yet (that needs a continuous motion sensor, not [MotionProbe]'s
         * triggered windows) — see docs/sleep-detection-architecture.md §4 for the full design and why
         * this is a deliberate, documented gap rather than an oversight.
         *
         * Deliberately ignores charging state, unlike [shouldEmitOnset]: charging correlates with "at a
         * fixed bedside spot" only for a phone left out in the open, which doesn't apply once it's
         * already in a pocket or drawer — the bedtime-window check is the discriminator doing that job
         * here instead.
         */
        internal fun shouldEmitEnclosedOnset(
            screenOff: Duration,
            baseThreshold: Duration,
            now: Instant,
            bedtimeWindow: ClosedRange<Instant>,
        ): Boolean {
            if (now !in bedtimeWindow) return false
            return screenOff >= baseThreshold * ENCLOSED_ONSET_MULTIPLIER
        }
    }
}
