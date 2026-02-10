package fr.bsodium.cron.engine.orchestrator

import fr.bsodium.cron.engine.calendar.CalendarReader
import fr.bsodium.cron.engine.config.CronConfig
import fr.bsodium.cron.engine.model.CalendarEvent
import fr.bsodium.cron.engine.model.ScheduledAlarm
import fr.bsodium.cron.engine.model.SyncResult
import fr.bsodium.cron.engine.scheduler.AlarmScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The brain of the Cron engine.
 *
 * Reads upcoming calendar events, applies the scheduling algorithm,
 * and instructs the [AlarmScheduler] to set or cancel alarms.
 *
 * This class is intentionally not tied to any Android UI component.
 * It can be called from a ViewModel, a Worker, or a BroadcastReceiver.
 */
class CronOrchestrator(
    private val calendarReader: CalendarReader,
    private val alarmScheduler: AlarmScheduler,
    private val config: CronConfig = CronConfig.DEFAULT
) {

    /**
     * Runs a full synchronization pass:
     * 1. Reads calendar events within the look-ahead window.
     * 2. Finds the first event block of the "next day" (tomorrow).
     * 3. Computes the optimal alarm time.
     * 4. Schedules or cancels the alarm accordingly.
     *
     * @param now The current instant (injectable for testing).
     * @return A [SyncResult] describing what happened.
     */
    fun synchronize(now: Instant = Instant.now()): SyncResult {
        if (!config.enabled) {
            // Cancel any previously scheduled alarm
            val requestCode = computeRequestCode(now)
            alarmScheduler.cancel(requestCode)
            return SyncResult(
                events = emptyList(),
                alarm = null,
                status = SyncResult.Status.DISABLED
            )
        }

        val zone = ZoneId.systemDefault()
        val to = now.plus(config.lookAheadHours, ChronoUnit.HOURS)

        // Read all events in the look-ahead window
        val allEvents = calendarReader.readEvents(now, to)

        // Filter to only events that start on "the next day" (tomorrow or later today
        // if all of today's events have passed). We focus on the next calendar day.
        val tomorrow = LocalDate.ofInstant(now, zone).plusDays(1)
        val tomorrowStart = tomorrow.atStartOfDay(zone).toInstant()
        val tomorrowEnd = tomorrow.plusDays(1).atStartOfDay(zone).toInstant()

        val tomorrowEvents = allEvents.filter { event ->
            event.startTime >= tomorrowStart && event.startTime < tomorrowEnd
        }

        if (tomorrowEvents.isEmpty()) {
            // No events tomorrow — cancel any existing alarm for that day
            val requestCode = computeRequestCodeForDate(tomorrow)
            alarmScheduler.cancel(requestCode)
            return SyncResult(
                events = allEvents,
                alarm = null,
                status = SyncResult.Status.NO_EVENTS
            )
        }

        // Merge nearby events into blocks; find the first block's start time
        val firstBlockStart = findFirstBlockStart(tomorrowEvents)

        // Compute alarm time: first event start minus prep time
        val alarmInstant = firstBlockStart.minus(config.prepTime)
        val alarmLocalTime = LocalTime.ofInstant(alarmInstant, zone)

        // Clamp to the allowed alarm window
        val requestCode = computeRequestCodeForDate(tomorrow)

        // If the alarm would be after the latest allowed time, skip it
        if (alarmLocalTime.isAfter(config.latestAlarm)) {
            alarmScheduler.cancel(requestCode)
            return SyncResult(
                events = allEvents,
                alarm = null,
                status = SyncResult.Status.ALARM_TOO_LATE
            )
        }

        // Determine the final alarm instant
        val finalAlarmInstant = if (alarmLocalTime.isBefore(config.earliestAlarm)) {
            // Clamp to earliest allowed time
            tomorrow.atTime(config.earliestAlarm).atZone(zone).toInstant()
        } else {
            alarmInstant
        }

        // If the alarm is in the past, skip it
        if (finalAlarmInstant.isBefore(now)) {
            alarmScheduler.cancel(requestCode)
            return SyncResult(
                events = allEvents,
                alarm = null,
                status = SyncResult.Status.ALARM_IN_PAST
            )
        }

        // Build the target event (the first event in the first block)
        val targetEvent = tomorrowEvents.first()
        val label = "Wake up for: ${targetEvent.title}"

        val alarm = ScheduledAlarm(
            triggerTime = finalAlarmInstant,
            targetEvent = targetEvent,
            label = label,
            requestCode = requestCode
        )

        // Schedule the alarm
        alarmScheduler.schedule(alarm)

        return SyncResult(
            events = allEvents,
            alarm = alarm,
            status = SyncResult.Status.ALARM_SET
        )
    }

    /**
     * Merges nearby events and returns the start time of the first block.
     *
     * Events are considered part of the same block if the gap between
     * the end of one event and the start of the next is less than
     * [CronConfig.eventMergeThreshold].
     */
    private fun findFirstBlockStart(sortedEvents: List<CalendarEvent>): Instant {
        // Events are already sorted by startTime from CalendarReader.
        // The first block starts with the first event.
        return sortedEvents.first().startTime
    }

    /**
     * Generates a stable request code for a given date.
     * Using the epoch day ensures one alarm per day, and re-syncing
     * with the same date overwrites the previous alarm.
     */
    private fun computeRequestCodeForDate(date: LocalDate): Int {
        return date.toEpochDay().toInt()
    }

    /**
     * Generates a request code for "tomorrow" relative to [now].
     */
    private fun computeRequestCode(now: Instant): Int {
        val tomorrow = LocalDate.ofInstant(now, ZoneId.systemDefault()).plusDays(1)
        return computeRequestCodeForDate(tomorrow)
    }
}
