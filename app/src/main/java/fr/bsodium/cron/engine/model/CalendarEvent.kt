package fr.bsodium.cron.engine.model

import java.time.Instant

/**
 * Domain model representing a single calendar event.
 * This is a pure Kotlin data class with no Android dependencies.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val isAllDay: Boolean,
    val calendarId: Long,
    val location: String? = null
)
