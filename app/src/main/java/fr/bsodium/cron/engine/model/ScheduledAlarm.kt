package fr.bsodium.cron.engine.model

import java.time.Instant

/**
 * Domain model representing an alarm that the engine has decided to schedule.
 * This is a pure Kotlin data class with no Android dependencies.
 */
data class ScheduledAlarm(
    /** The exact time the alarm should fire. */
    val triggerTime: Instant,

    /** The calendar event this alarm is based on. */
    val targetEvent: CalendarEvent,

    /** Human-readable label, e.g. "Wake up for: Team Standup" */
    val label: String,

    /** Unique request code for the PendingIntent. Derived from the target
     *  date so re-syncing overwrites the previous alarm for the same day. */
    val requestCode: Int
)
