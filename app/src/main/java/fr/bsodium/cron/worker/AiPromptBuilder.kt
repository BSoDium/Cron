package fr.bsodium.cron.worker

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Builds the user-turn prompt the planner receives, for both the evening plan and overnight replans.
 * Pure string construction over the [SleepSession] domain model (the only impurity is reading the
 * wall clock for "it is now …"), so it's testable without a worker or Android.
 */
object AiPromptBuilder {

    fun build(session: SleepSession, isEveningPlan: Boolean, instructions: String?): String {
        val localNow = Clock.System.now().toLocalDateTime(TimeZone.of(session.timezone))
        return if (isEveningPlan) buildEveningPlanMessage(session, localNow, instructions)
        else buildOvernightReplanMessage(session, localNow, instructions)
    }

    private fun buildEveningPlanMessage(
        session: SleepSession,
        localNow: LocalDateTime,
        userInstructions: String?,
    ): String {
        val plan = session.plan
        // Latest evening-plan event, so a manual replan's freshly-captured location wins.
        val eveningEvent = session.events.lastOrNull { it.trigger == TriggerType.EveningPlan }
        val location = (eveningEvent?.data as? EventData.EveningPlan)?.location

        return buildString {
            appendLine("It is now $localNow (${session.timezone}).")
            appendLine()
            appendLine("## Session context")
            appendLine("- Morning date: ${session.date}")
            appendLine("- Hard latest (never exceed): ${plan.hardLatest}")
            appendLine("- Wake window: ${plan.wakeWindowStart} – ${plan.wakeWindowEnd}")
            appendLine("- Travel buffer (minimum commute floor, NOT prep): ${plan.commuteBufferMinutes} min")
            appendLine("- Morning preparation time (getting ready, separate from travel): ${plan.preparationBufferMinutes} min")
            appendLine("- Allowed commute modes (only estimate with these): ${plan.allowedCommuteModes.joinToString { it.promptToken }}")
            appendLine("- Free day wake window: ${plan.wakeWindowStart} – ${plan.wakeWindowEnd}")
            appendLine()
            appendLocation(location)
            appendLine()
            appendUserInstructions(userInstructions)
            appendLine("Plan tomorrow's alarm. Follow the process in your system prompt: read calendar, identify anchor event, estimate commute if applicable, then call set_alarm.")
        }
    }

    /** Appends the device location block (the commute ORIGIN), or an unavailable note. Shared by the
     *  evening plan and the overnight replan so both give the planner real coordinates — without this on
     *  the replan path the model has no origin and invents a city. */
    private fun StringBuilder.appendLocation(location: LocationPayload?) {
        if (location != null) {
            appendLine("## Current location")
            appendLine("- Latitude: ${location.lat}, Longitude: ${location.lng}")
            location.address?.let { appendLine("- Address: $it") }
            appendLine("- Source: ${location.source.name.lowercase()}")
            appendLine("- Accuracy: ±${location.accuracyMeters?.let { "${it.toInt()} m" } ?: "unknown"}")
            appendLine("- Captured at: ${location.capturedAt}")
        } else {
            appendLine("## Location")
            appendLine("- Source: unavailable — apply a flat +30 min buffer and mention it in the reason")
        }
    }

    /** Appends the user's standing custom instructions, when set, as a labelled block. */
    private fun StringBuilder.appendUserInstructions(text: String?) {
        if (text.isNullOrBlank()) return
        appendLine("## User instructions")
        appendLine("Standing instructions from the user — honour them unless they'd push the alarm past the hard latest:")
        appendLine(text)
        appendLine()
    }

    private fun isPhoneOnlyMode(session: SleepSession): Boolean {
        val sessionAge = Clock.System.now() - session.createdAt
        if (sessionAge < PHONE_ONLY_THRESHOLD) return false
        return session.events.none {
            it.trigger == TriggerType.HcStageUpdate &&
                (it.data as? EventData.HcStageUpdate)?.confidence == SignalConfidence.High
        }
    }

    private fun buildOvernightReplanMessage(
        session: SleepSession,
        localNow: LocalDateTime,
        userInstructions: String?,
    ): String {
        val plan = session.plan
        val instr = session.currentInstruction
        // Same origin the evening plan captured — without it the replan model has no coordinates and
        // guesses a city.
        val location = (session.events.lastOrNull { it.trigger == TriggerType.EveningPlan }
            ?.data as? EventData.EveningPlan)?.location

        return buildString {
            appendLine("It is now $localNow (${session.timezone}).")
            appendLine()
            if (isPhoneOnlyMode(session)) {
                appendLine("**Note: phone-only mode is active.** No high-confidence Health Connect data has arrived in the past 90 minutes. Rely exclusively on screen-state and activity signals. Do not request or expect sleep stage updates; treat any stage signals as low-confidence approximations.")
                appendLine()
            }
            appendLine("## Day plan")
            appendLine("- Morning date: ${session.date}")
            appendLine("- Hard latest (never exceed): ${plan.hardLatest}")
            appendLine("- Wake window: ${plan.wakeWindowStart} – ${plan.wakeWindowEnd}")
            appendLine("- Allowed commute modes (only estimate with these): ${plan.allowedCommuteModes.joinToString { it.promptToken }}")
            appendLine("- Snooze count so far: ${session.snoozeCount}")
            appendLine()
            appendLine("## Current instruction")
            appendLine("- Action: ${instr.action.name}")
            if (instr.alarmTime != null) appendLine("- Alarm set for: ${instr.alarmTime}")
            appendLine("- Reason: ${instr.reason}")
            appendLine()
            appendLine("## Event log (oldest first)")
            session.events.forEachIndexed { i, ev ->
                appendLine("${i + 1}. [${ev.timestamp}] ${ev.trigger.name}: ${summarizeEventData(ev)}")
            }
            appendLine()
            val last = session.events.lastOrNull()
            if (last != null) {
                appendLine("## Triggering event")
                appendLine("[${last.timestamp}] ${last.trigger.name}: ${summarizeEventData(last)}")
            }
            appendLine()
            appendLocation(location)
            appendLine()
            appendUserInstructions(userInstructions)
            appendLine("Decide what the alarm system should do. Call set_alarm if you want to adjust the wake time. If the current alarm is already optimal, respond with a brief explanation and do not call any tool.")
        }
    }

    private fun summarizeEventData(event: SessionEvent): String = when (val d = event.data) {
        is EventData.EveningPlan -> "timezone=${d.timezone}, location_source=${d.location.source}"
        is EventData.SleepOnset -> "screen_off_since=${d.screenOffSince}, rearm=${d.rearm}"
        is EventData.HcStageUpdate -> "stage=${d.stage}, source=${d.source}, confidence=${d.confidence}"
        is EventData.MidSleepActivity -> "activity=${d.activityType}, screen_on=${d.screenOn}, duration=${d.durationSeconds}s"
        is EventData.OutOfBedConfirmed -> "evidence=${d.evidence}"
        is EventData.WakeWindowOpportunity -> "stage=${d.currentStage}, window=${d.windowStart}–${d.windowEnd}"
        is EventData.AlarmInteraction -> "snooze_duration=${d.snoozeDurationMinutes}min, count=${d.snoozeCount}"
        is EventData.CalendarChange -> "type=${d.changeType}, affects_first=${d.affectsFirstEvent}"
        EventData.Empty -> "(empty)"
    }

    private val PHONE_ONLY_THRESHOLD = 90.minutes
}
