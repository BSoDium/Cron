package fr.bsodium.cron.engine.config

import java.time.Duration
import java.time.LocalTime

/**
 * Configuration constants for the Cron scheduling engine.
 *
 * Structured as a data class so a future settings UI can construct
 * custom instances from persisted user preferences (DataStore, etc.).
 * For now, [DEFAULT] provides sensible hard-coded values.
 */
data class CronConfig(

    /** Time to subtract from the first event's start to compute the alarm.
     *  This represents the user's preparation time (shower, breakfast, etc.). */
    val prepTime: Duration = Duration.ofMinutes(75),

    /** Earliest allowed alarm time. Alarms computed before this
     *  will be clamped to this value. */
    val earliestAlarm: LocalTime = LocalTime.of(5, 0),

    /** Latest allowed alarm time. If the computed alarm would be
     *  after this, no alarm is scheduled (the user is presumably awake). */
    val latestAlarm: LocalTime = LocalTime.of(10, 0),

    /** Snooze duration when the user hits snooze. */
    val snoozeDuration: Duration = Duration.ofMinutes(10),

    /** Maximum number of snoozes allowed per alarm. */
    val maxSnoozeCount: Int = 3,

    /** Whether to skip all-day events (typically holidays, birthdays). */
    val skipAllDayEvents: Boolean = true,

    /** Minimum gap between two events before the second event is
     *  considered a separate block for alarm purposes. Events closer
     *  than this threshold are merged into one block. */
    val eventMergeThreshold: Duration = Duration.ofMinutes(30),

    /** How far into the future to look for events (in hours from now). */
    val lookAheadHours: Long = 36,

    /** Which calendar IDs to include. Empty list = all calendars. */
    val calendarIds: List<Long> = emptyList(),

    /** Whether the automatic alarm engine is enabled. */
    val enabled: Boolean = true
) {
    companion object {
        val DEFAULT = CronConfig()
    }
}
