package fr.bsodium.cron.session.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
data class SessionEvent(
    val trigger: TriggerType,
    val timestamp: Instant,
    val data: EventData,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class EventData {

    @Serializable
    @SerialName("evening_plan")
    data class EveningPlan(
        val timezone: String,
        val location: LocationPayload,
        /** True when the user kicked off this plan from the FAB (vs. the scheduled nightly job). */
        val isManual: Boolean = false,
    ) : EventData()

    @Serializable
    @SerialName("sleep_onset")
    data class SleepOnset(
        val screenOffSince: Instant,
        val rearm: Boolean,
    ) : EventData()

    @Serializable
    @SerialName("hc_stage_update")
    data class HcStageUpdate(
        val stage: SleepStage,
        val source: String,
        val confidence: SignalConfidence,
        val recordStart: Instant,
        val recordEnd: Instant,
    ) : EventData()

    @Serializable
    @SerialName("mid_sleep_activity")
    data class MidSleepActivity(
        val activityType: ActivityType,
        val screenOn: Boolean,
        val durationSeconds: Int,
    ) : EventData()

    @Serializable
    @SerialName("out_of_bed_confirmed")
    data class OutOfBedConfirmed(
        val evidence: List<String>,
    ) : EventData()

    @Serializable
    @SerialName("wake_window_opportunity")
    data class WakeWindowOpportunity(
        val currentStage: SleepStage?,
        val windowStart: LocalTime,
        val windowEnd: LocalTime,
    ) : EventData()

    @Serializable
    @SerialName("alarm_interaction")
    data class AlarmInteraction(
        val snoozeDurationMinutes: Int? = null,
        val snoozeCount: Int,
    ) : EventData()

    @Serializable
    @SerialName("calendar_change")
    data class CalendarChange(
        val changeType: String,
        val eventId: String,
        val affectsFirstEvent: Boolean,
    ) : EventData()

    @Serializable
    @SerialName("empty")
    data object Empty : EventData()
}

@Serializable
data class LocationPayload(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float? = null,
    val source: LocationSource,
    val capturedAt: Instant,
    /** Device-resolved human-readable address; null when reverse geocoding is unavailable or fails. */
    val address: String? = null,
)
