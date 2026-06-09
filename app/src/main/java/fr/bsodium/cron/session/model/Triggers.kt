package fr.bsodium.cron.session.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TriggerType {
    @SerialName("evening_plan") EveningPlan,
    @SerialName("sleep_onset") SleepOnset,
    @SerialName("hc_stage_update") HcStageUpdate,
    @SerialName("mid_sleep_activity") MidSleepActivity,
    @SerialName("out_of_bed_confirmed") OutOfBedConfirmed,
    @SerialName("wake_window_opportunity") WakeWindowOpportunity,
    @SerialName("alarm_dismissed") AlarmDismissed,
    @SerialName("alarm_snoozed") AlarmSnoozed,
    @SerialName("calendar_change") CalendarChange,
    @SerialName("hard_latest_fired") HardLatestFired,
}

@Serializable
enum class SleepStage {
    @SerialName("awake") Awake,
    @SerialName("light") Light,
    @SerialName("deep") Deep,
    @SerialName("rem") Rem,
}

@Serializable
enum class ActivityType {
    @SerialName("still") Still,
    @SerialName("walking") Walking,
    @SerialName("running") Running,
    @SerialName("out_of_bed") OutOfBed,
}

@Serializable
enum class SignalConfidence {
    @SerialName("high") High,
    @SerialName("medium") Medium,
    @SerialName("low") Low,
}

/** [wireToken] mirrors the @SerialName so prompt text speaks the same vocabulary as the wire format. */
@Serializable
enum class LocationSource(val wireToken: String) {
    @SerialName("gps") Gps("gps"),
    @SerialName("last_known") LastKnown("last_known"),
    @SerialName("home_address") HomeAddress("home_address"),
    @SerialName("unavailable") Unavailable("unavailable"),
}

@Serializable
enum class SessionStatus {
    @SerialName("planning") Planning,
    @SerialName("monitoring") Monitoring,
    @SerialName("awake") Awake,
    @SerialName("re_monitoring") ReMonitoring,
    @SerialName("complete") Complete,
}
