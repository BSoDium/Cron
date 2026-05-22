package fr.bsodium.cron.alarm

import kotlinx.datetime.LocalDate

/**
 * Shared constants for the two-alarm model.
 *
 * - **AI alarm**: mutable, may be rescheduled any number of times during the
 *   night. Request code is [AI_REQUEST_CODE_BASE] + epochDay so it's stable
 *   per session date.
 * - **Hard-latest alarm**: immutable safety floor. Once armed at session
 *   start, only [HardLatestScheduler.clear] removes it. Request code is
 *   [HARD_LATEST_REQUEST_CODE_BASE] + epochDay.
 * - **Evening plan alarm**: daily trigger that starts a sleep session.
 *   Request code is the constant [EVENING_PLAN_REQUEST_CODE].
 */
object AlarmConstants {
    const val AI_REQUEST_CODE_BASE = 100_000
    const val HARD_LATEST_REQUEST_CODE_BASE = 200_000
    const val EVENING_PLAN_REQUEST_CODE = 300_001

    const val EXTRA_KIND = "fr.bsodium.cron.alarm.KIND"
    const val EXTRA_LABEL = "fr.bsodium.cron.alarm.LABEL"
    const val EXTRA_SESSION_ID = "fr.bsodium.cron.alarm.SESSION_ID"

    const val KIND_AI = "ai"
    const val KIND_HARD_LATEST = "hard_latest"
    const val KIND_SNOOZE = "snooze"

    fun aiRequestCode(date: LocalDate): Int = AI_REQUEST_CODE_BASE + date.toEpochDays()
    fun hardLatestRequestCode(date: LocalDate): Int = HARD_LATEST_REQUEST_CODE_BASE + date.toEpochDays()
}
