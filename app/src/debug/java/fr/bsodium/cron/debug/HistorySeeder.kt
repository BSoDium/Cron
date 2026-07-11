package fr.bsodium.cron.debug

import android.content.Context
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.ActivityType
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * DEBUG-ONLY. Clears every session and writes back a handful of internally-consistent days —
 * varied plans, replans, and sleep/wake events — so the timeline can be visually reviewed against
 * realistic data instead of the noise ad hoc manual testing accumulates. Wired from
 * `DeveloperSettingsScreen`. Writes go through the same [SessionRepository]/[CronDatabase] surfaces
 * the real app uses (`appendEvent`, direct `aiMessageDao` inserts) — no separate mock path, so the
 * timeline renders it exactly like real history.
 */
object HistorySeeder {

    suspend fun seed(context: Context) {
        val repo = SessionRepository(context)
        val db = CronDatabase.get(context)

        // Deleting the DB rows does nothing to a REAL Android alarm already armed from earlier manual testing — AlarmManager is independent of Room, so a stale alarm could still fire later and write into the freshly-seeded fake session; cancel both real schedulers and stop the live sleep-monitoring service for the outgoing session first, so nothing external can write into the DB once synthetic history takes over.
        repo.findCurrent()?.let { outgoing ->
            AlarmScheduler(context).cancel(outgoing.date)
            HardLatestScheduler(context).clear(outgoing.date)
            repo.cancelAiTurn(outgoing.id)
        }
        context.startService(SleepSessionService.stopIntent(context))

        repo.clearAll()

        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date

        DAYS.forEachIndexed { index, day ->
            val date = today.minus(DAYS.size - 1 - index, DateTimeUnit.DAY)
            seedDay(repo, db, tz, date, day)
        }
    }

    private suspend fun seedDay(repo: SessionRepository, db: CronDatabase, tz: TimeZone, date: LocalDate, day: DayScript) {
        fun at(dayOffset: Int, time: LocalTime): Instant =
            LocalDateTime(date.plus(dayOffset, DateTimeUnit.DAY), time).toInstant(tz)

        val eveningPlanAt = at(-1, day.eveningPlanTime)
        val plan = DayPlan(
            hardLatest = day.hardLatest,
            wakeWindowStart = day.wakeWindowStart,
            wakeWindowEnd = day.wakeWindowEnd,
            commuteBufferMinutes = 25,
            isFreeDayFallback = false,
            generatedAt = eveningPlanAt,
        )
        val session = repo.createSession(plan, date, tz.id)

        repo.appendEvent(
            session.id,
            SessionEvent(
                trigger = TriggerType.EveningPlan,
                timestamp = eveningPlanAt,
                data = EventData.EveningPlan(
                    timezone = tz.id,
                    location = LocationPayload(
                        lat = 48.8566,
                        lng = 2.3522,
                        source = LocationSource.Gps,
                        capturedAt = eveningPlanAt,
                    ),
                    isManual = day.manualBase,
                ),
            ),
        )

        var turnIndex = 0
        for (beat in day.beats) {
            when (beat) {
                is Beat.Turn -> {
                    insertTurn(db, session.id, turnIndex, at(beat.dayOffset, beat.time), beat)
                    turnIndex++
                }
                is Beat.Event -> repo.appendEvent(
                    session.id,
                    SessionEvent(trigger = beat.trigger, timestamp = at(beat.dayOffset, beat.time), data = beat.data),
                )
            }
        }

        repo.updateStatus(session.id, day.finalStatus)
    }

    private suspend fun insertTurn(db: CronDatabase, sessionId: String, turnIndex: Int, at: Instant, turn: Beat.Turn) {
        val toolId = "seed-$sessionId-$turnIndex"
        val (toolUse, toolResultContent) = when (val action = turn.action) {
            is TurnAction.SetAlarm -> ContentBlock.ToolUse(
                id = toolId,
                name = "set_alarm",
                input = buildJsonObject { put("time_iso", action.time.toString()) },
            ) to """{"alarm_time":"${action.time}"}"""
            TurnAction.CancelAlarm -> ContentBlock.ToolUse(toolId, "cancel_alarm", buildJsonObject {}) to """{"status":"ok"}"""
            is TurnAction.DoNothing -> ContentBlock.ToolUse(
                id = toolId,
                name = "do_nothing",
                input = buildJsonObject { put("reason", action.reason) },
            ) to """{"status":"ok"}"""
        }
        val assistantBlocks: List<ContentBlock> = listOf(
            ContentBlock.Thinking(thinking = turn.thinking),
            toolUse,
            ContentBlock.Text(text = "SUMMARY: ${turn.summary}\n\n${turn.body}"),
        )
        db.aiMessageDao().insert(
            AiMessageEntity(
                sessionId = sessionId,
                turnIndex = turnIndex,
                role = "assistant",
                contentJson = SessionJson.encodeToString(assistantBlocks),
                createdAt = at.toEpochMilliseconds(),
            ),
        )
        db.aiMessageDao().insert(
            AiMessageEntity(
                sessionId = sessionId,
                turnIndex = turnIndex,
                role = "user",
                contentJson = SessionJson.encodeToString<List<ContentBlock>>(
                    listOf(ContentBlock.ToolResult(tool_use_id = toolId, content = toolResultContent)),
                ),
                createdAt = at.toEpochMilliseconds() + TOOL_RESULT_LAG_MS,
            ),
        )
    }

    private const val TOOL_RESULT_LAG_MS = 4_000L

    private sealed interface TurnAction {
        data class SetAlarm(val time: LocalTime) : TurnAction
        data object CancelAlarm : TurnAction
        data class DoNothing(val reason: String) : TurnAction
    }

    /** One beat in a day's script — either an AI turn (assigned the next turnIndex in order) or a
     *  plain session event. [dayOffset] is relative to the session's own morning date: -1 is the prior
     *  evening, 0 is the morning itself. Ordering matters: a turn's inferred [fr.bsodium.cron.ui.screens.home.RunKind]
     *  comes from whichever event precedes it in time (`AiPlanMapper.buildPlan`), so an event meant to
     *  motivate a replan must be listed before that replan's turn. */
    private sealed interface Beat {
        data class Turn(
            val dayOffset: Int,
            val time: LocalTime,
            val thinking: String,
            val summary: String,
            val body: String,
            val action: TurnAction,
        ) : Beat

        data class Event(
            val dayOffset: Int,
            val time: LocalTime,
            val trigger: TriggerType,
            val data: EventData,
        ) : Beat
    }

    private data class DayScript(
        val eveningPlanTime: LocalTime,
        val manualBase: Boolean,
        val hardLatest: LocalTime,
        val wakeWindowStart: LocalTime,
        val wakeWindowEnd: LocalTime,
        val beats: List<Beat>,
        val finalStatus: SessionStatus,
    )

    private fun time(hour: Int, minute: Int) = LocalTime(hour, minute)

    private val DAYS = listOf(
        // Day 1 (3 days ago): a calm baseline night — one plan, no replans.
        DayScript(
            eveningPlanTime = time(22, 10),
            manualBase = false,
            hardLatest = time(8, 0),
            wakeWindowStart = time(7, 15),
            wakeWindowEnd = time(7, 45),
            beats = listOf(
                Beat.Turn(
                    dayOffset = -1, time = time(22, 11),
                    thinking = "Checking tomorrow's calendar for the first hard commitment — an 08:45 meeting, " +
                        "roughly 25 minutes away by transit.",
                    summary = "Wake at 07:30 for your 08:45 meeting",
                    body = "Set a 07:30 alarm — commute is about 25 minutes and I added standard prep time.",
                    action = TurnAction.SetAlarm(time(7, 30)),
                ),
                Beat.Event(0, time(23, 40), TriggerType.SleepOnset, EventData.SleepOnset(screenOffSince = Instant.DISTANT_PAST, rearm = false)),
                Beat.Event(
                    0, time(3, 20), TriggerType.MidSleepActivity,
                    EventData.MidSleepActivity(activityType = ActivityType.Still, screenOn = false, durationSeconds = 45),
                ),
                Beat.Event(
                    0, time(7, 15), TriggerType.WakeWindowOpportunity,
                    EventData.WakeWindowOpportunity(currentStage = SleepStage.Light, windowStart = time(7, 15), windowEnd = time(7, 45)),
                ),
                Beat.Event(0, time(7, 32), TriggerType.OutOfBedConfirmed, EventData.OutOfBedConfirmed(evidence = listOf("accelerometer", "screen_on"))),
                Beat.Event(0, time(7, 33), TriggerType.AlarmDismissed, EventData.Empty),
            ),
            finalStatus = SessionStatus.Complete,
        ),
        // Day 2 (2 days ago): a manual evening plan, a schedule change overnight, then a snooze.
        DayScript(
            eveningPlanTime = time(21, 50),
            manualBase = true,
            hardLatest = time(7, 0),
            wakeWindowStart = time(6, 0),
            wakeWindowEnd = time(6, 30),
            beats = listOf(
                Beat.Turn(
                    dayOffset = -1, time = time(21, 51),
                    thinking = "Replanning now since you asked — reading tomorrow's schedule for the earliest commitment.",
                    summary = "Wake at 06:45 for your early train",
                    body = "Set a 06:45 alarm for tomorrow's 07:30 departure — factored in a 30 minute commute.",
                    action = TurnAction.SetAlarm(time(6, 45)),
                ),
                Beat.Event(0, time(23, 10), TriggerType.SleepOnset, EventData.SleepOnset(screenOffSince = Instant.DISTANT_PAST, rearm = false)),
                Beat.Event(0, time(23, 50), TriggerType.CalendarChange, EventData.CalendarChange(changeType = "event_moved", eventId = "evt-1", affectsFirstEvent = true)),
                Beat.Turn(
                    dayOffset = 0, time = time(23, 51),
                    thinking = "Your first meeting just moved earlier — recalculating the wake window against the new time.",
                    summary = "Moved your alarm to 06:15",
                    body = "Your train now leaves earlier, so I moved the alarm up by 30 minutes to keep the same buffer.",
                    action = TurnAction.SetAlarm(time(6, 15)),
                ),
                Beat.Event(
                    0, time(2, 0), TriggerType.MidSleepActivity,
                    EventData.MidSleepActivity(activityType = ActivityType.Still, screenOn = false, durationSeconds = 30),
                ),
                Beat.Event(0, time(6, 15), TriggerType.AlarmSnoozed, EventData.AlarmInteraction(snoozeDurationMinutes = 9, snoozeCount = 1)),
                Beat.Turn(
                    dayOffset = 0, time = time(6, 15),
                    thinking = "You snoozed once — re-arming a few minutes out rather than a full reschedule.",
                    summary = "Re-armed for 06:24",
                    body = "Re-armed the alarm to 06:24 after you snoozed at 06:15 with snooze count = 1.",
                    action = TurnAction.SetAlarm(time(6, 24)),
                ),
                Beat.Event(0, time(6, 25), TriggerType.AlarmDismissed, EventData.Empty),
                Beat.Event(0, time(6, 26), TriggerType.OutOfBedConfirmed, EventData.OutOfBedConfirmed(evidence = listOf("accelerometer"))),
            ),
            finalStatus = SessionStatus.Complete,
        ),
        // Day 3 (yesterday): the primary alarm gets missed and the safety alarm has to fire.
        DayScript(
            eveningPlanTime = time(22, 30),
            manualBase = false,
            hardLatest = time(8, 0),
            wakeWindowStart = time(6, 45),
            wakeWindowEnd = time(7, 15),
            beats = listOf(
                Beat.Turn(
                    dayOffset = -1, time = time(22, 31),
                    thinking = "Standard evening check — nothing on the calendar before 9am tomorrow.",
                    summary = "Wake at 07:00, no early meetings",
                    body = "No early commitments tomorrow, so I set a relaxed 07:00 wake time with a full 90-minute sleep-cycle buffer.",
                    action = TurnAction.SetAlarm(time(7, 0)),
                ),
                Beat.Event(0, time(0, 10), TriggerType.SleepOnset, EventData.SleepOnset(screenOffSince = Instant.DISTANT_PAST, rearm = false)),
                Beat.Event(
                    0, time(3, 45), TriggerType.MidSleepActivity,
                    EventData.MidSleepActivity(activityType = ActivityType.Still, screenOn = false, durationSeconds = 60),
                ),
                Beat.Event(0, time(8, 0), TriggerType.HardLatestFired, EventData.Empty),
                Beat.Turn(
                    dayOffset = 0, time = time(8, 0),
                    thinking = "The safety alarm already fired — nothing further to schedule right now.",
                    summary = "Safety alarm handled it",
                    body = "The primary alarm was missed, so the safety alarm fired at the hard-latest time. No further action needed until tonight's plan.",
                    action = TurnAction.DoNothing(reason = "Safety alarm already fired; day has started"),
                ),
                Beat.Event(0, time(8, 4), TriggerType.AlarmDismissed, EventData.Empty),
                Beat.Event(0, time(8, 5), TriggerType.OutOfBedConfirmed, EventData.OutOfBedConfirmed(evidence = listOf("screen_on"))),
            ),
            finalStatus = SessionStatus.Complete,
        ),
        // Day 4 (today): a freshly-settled morning — the current/live session.
        DayScript(
            eveningPlanTime = time(22, 0),
            manualBase = false,
            hardLatest = time(7, 45),
            wakeWindowStart = time(6, 45),
            wakeWindowEnd = time(7, 15),
            beats = listOf(
                Beat.Turn(
                    dayOffset = -1, time = time(22, 1),
                    thinking = "Checking tomorrow's calendar — first meeting is an 08:15 standup.",
                    summary = "Wake at 07:15 for your 08:15 standup",
                    body = "Set a 07:15 alarm — light commute, so I kept prep time standard.",
                    action = TurnAction.SetAlarm(time(7, 15)),
                ),
                Beat.Event(0, time(23, 20), TriggerType.SleepOnset, EventData.SleepOnset(screenOffSince = Instant.DISTANT_PAST, rearm = false)),
                Beat.Event(
                    0, time(7, 0), TriggerType.WakeWindowOpportunity,
                    EventData.WakeWindowOpportunity(currentStage = SleepStage.Light, windowStart = time(7, 0), windowEnd = time(7, 30)),
                ),
                Beat.Event(0, time(7, 17), TriggerType.AlarmDismissed, EventData.Empty),
                Beat.Event(0, time(7, 18), TriggerType.OutOfBedConfirmed, EventData.OutOfBedConfirmed(evidence = listOf("accelerometer", "screen_on"))),
            ),
            finalStatus = SessionStatus.Awake,
        ),
    )
}
