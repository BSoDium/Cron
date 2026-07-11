package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.SleepSegment
import kotlinx.datetime.LocalTime

data class HomeUiState(
    val sessionDisplay: SessionDisplayState? = null,
    val greetingPrefix: String = "Welcome",
    val greetingName: String? = null,
    val dateLabel: String = "",

    val aiPlan: AiPlanUi? = null,
    val timeline: List<TimelineItem> = emptyList(),
    /** [TimelineItem.id]s that are genuinely new since the last emission of this flow's lifetime —
     *  the entrance-animation gate (Round 32). Empty on the very first emission (cold start) and on
     *  any emission that's identical to the previous one (e.g. a Home→Settings→back round trip with
     *  no underlying data change) — see [diffNewlyArrivedIds]. */
    val newlyArrivedIds: Set<String> = emptySet(),
    val hasMoreHistory: Boolean = false,
    val isRetrying: Boolean = false,
    /** False until the backing flows have produced their first value — gates the onboarding so it
     *  doesn't flash over an existing plan during the cold-start load. */
    val initialized: Boolean = false,
    /** A plan-affecting setting changed since the last plan was written — offers a re-run. */
    val settingsChangedSincePlan: Boolean = false,
    /** The latest AI turn failed (and hasn't been dismissed) — surfaces a dismissible banner. */
    val aiFailure: AiTurnFailure? = null,
    /** User preference: fire subtle haptic ticks while the assistant streams. */
    val hapticsEnabled: Boolean = true,
    /** User preference: auto-plan and arm alarms each night. Off cancels everything armed. */
    val autoAlarmsEnabled: Boolean = true,
    /** The local time the nightly planning run fires — drives the resting screen's "next plan at …" line. */
    val eveningTriggerTime: LocalTime = LocalTime(20, 0),
)

/** Why the most recent AI turn ended without updating the plan, for the home failure banner. */
sealed interface AiTurnFailure {
    data class BudgetExhausted(val used: Int, val limit: Int) : AiTurnFailure
    data object MissingApiKey : AiTurnFailure
    data class Generic(val reason: String?) : AiTurnFailure
}

data class SleepStatsUi(
    val durationLabel: String,
    val segments: List<SleepSegment>,
)
