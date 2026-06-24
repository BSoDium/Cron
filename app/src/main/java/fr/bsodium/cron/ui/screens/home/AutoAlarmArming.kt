package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SleepSession
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * Re-applies a session's already-decided alarm + hard-latest floor to AlarmManager when auto-plan is
 * toggled back on (symmetry with the OFF teardown). Each target is gated on still being AHEAD of now,
 * so re-enabling after the wake window never rings instantly (a past setAlarmClock fires at once, and
 * the AI alarm would clamp to now + a minute).
 */
internal fun rearmSessionAlarms(
    session: SleepSession,
    alarmScheduler: AlarmScheduler,
    hardLatestScheduler: HardLatestScheduler,
) {
    val tz = TimeZone.of(session.timezone)
    val now = Clock.System.now()
    if (session.date.atTime(session.plan.hardLatest).toInstant(tz) > now) {
        hardLatestScheduler.arm(session.plan.hardLatest, session.date, tz, session.id)
    }
    val instr = session.currentInstruction
    val alarmTime = instr.alarmTime
    if (instr.action == ActionType.SetAlarm && alarmTime != null) {
        val requested = session.date.atTime(alarmTime).toInstant(tz)
        if (requested > now) {
            alarmScheduler.schedule(
                requested = requested,
                hardLatest = session.plan.hardLatest,
                sessionDate = session.date,
                timezone = tz,
                label = instr.reason.ifBlank { "Cron Alarm" },
                sessionId = session.id,
            )
        }
    }
}
