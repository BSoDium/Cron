package fr.bsodium.cron.engine.model

/**
 * Result of a synchronization pass by the [fr.bsodium.cron.engine.orchestrator.CronOrchestrator].
 */
data class SyncResult(
    /** All calendar events found in the look-ahead window. */
    val events: List<CalendarEvent>,

    /** The alarm that was scheduled, or null if none. */
    val alarm: ScheduledAlarm?,

    /** Why the sync resulted in this outcome. */
    val status: Status,

    /** Diagnostic information about the travel time estimation attempt. */
    val travelInfo: TravelInfo? = null
) {
    enum class Status {
        /** An alarm was successfully scheduled. */
        ALARM_SET,

        /** No events were found in the look-ahead window. */
        NO_EVENTS,

        /** The computed alarm time falls after the latest allowed alarm. */
        ALARM_TOO_LATE,

        /** The computed alarm time is in the past. */
        ALARM_IN_PAST,

        /** The engine is disabled via configuration. */
        DISABLED
    }
}
