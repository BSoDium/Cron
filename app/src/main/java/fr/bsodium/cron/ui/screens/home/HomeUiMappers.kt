package fr.bsodium.cron.ui.screens.home

import android.util.Log
import androidx.work.Data
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.SessionEventEntity
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.worker.AiTurnWorker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

private const val TAG = "HomeUiMappers"

/** Pure DB-row → UI-state mapping for the home screen, kept out of [HomeViewModel] so it stays testable
 *  in isolation (and the ViewModel under the file cap). Tolerant at the DB boundary: a corrupt row maps
 *  to null/fallback, always logged. */
internal fun SessionEntity.toDisplayState(): SessionDisplayState? {
    val instruction = runCatching {
        SessionJson.decodeFromString<Instruction>(currentInstructionJson)
    }
        .onFailure { Log.w(TAG, "decode instruction failed for session $id", it) }
        .getOrNull() ?: return null
    val sessionDate = runCatching { LocalDate.parse(date) }
        .onFailure { Log.w(TAG, "parse session date failed for session $id", it) }
        .getOrNull() ?: return null
    val plan = runCatching { SessionJson.decodeFromString<DayPlan>(planJson) }
        .onFailure { Log.w(TAG, "decode plan failed for session $id", it) }
        .getOrNull()
    return SessionDisplayState(
        status = runCatching { SessionStatus.valueOf(status) }
            .onFailure { Log.w(TAG, "unknown session status '$status' for $id", it) }
            .getOrElse { SessionStatus.Complete },
        action = instruction.action,
        alarmTime = instruction.alarmTime,
        reason = instruction.reason,
        sessionDate = sessionDate,
        snoozeCount = snoozeCount,
        hardLatest = plan?.hardLatest,
    )
}

internal fun buildSleepStats(rows: List<SessionEventEntity>): SleepStatsUi? {
    val segments = rows
        .filter { it.trigger == TriggerType.HcStageUpdate.name }
        .mapNotNull { row ->
            val data = runCatching {
                SessionJson.decodeFromString<EventData>(row.dataJson)
            }
                .onFailure { Log.w(TAG, "decode HcStageUpdate failed for event ${row.id}", it) }
                .getOrNull() as? EventData.HcStageUpdate ?: return@mapNotNull null
            SleepSegment(
                stage = data.stage,
                start = data.recordStart,
                end = data.recordEnd,
            )
        }
        .sortedBy { it.start }
    if (segments.isEmpty()) return null
    val totalSpan = segments.last().end - segments.first().start
    return SleepStatsUi(
        durationLabel = formatDuration(totalSpan),
        segments = segments,
    )
}

/** Maps an [AiTurnWorker] failure's output data to a UI failure. Unknown/absent reasons (open input
 *  — future worker reasons) fall through to [AiTurnFailure.Generic]. */
internal fun Data.toAiTurnFailure(): AiTurnFailure = when (getString(AiTurnWorker.KEY_REASON)) {
    AiTurnWorker.REASON_BUDGET -> AiTurnFailure.BudgetExhausted(
        used = getInt(AiTurnWorker.KEY_USED, 0),
        limit = getInt(AiTurnWorker.KEY_LIMIT, 0),
    )
    AiTurnWorker.REASON_NO_API_KEY -> AiTurnFailure.MissingApiKey
    else -> AiTurnFailure.Generic(getString(AiTurnWorker.KEY_REASON))
}

/** The card header reads the date the alarm fires (the session's morning), falling back to today
 *  when idle. Relative when near — "Today" / "Tomorrow" — else "EEEE d" (e.g. "Tuesday 2");
 *  locale-default for the human-language weekday name. */
internal fun formatDateLabel(session: SessionDisplayState?): String {
    val today = JavaLocalDate.now()
    val date = session?.sessionDate?.toJavaLocalDate() ?: today
    return when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault()))
    }
}

private fun formatDuration(d: Duration): String {
    if (d <= ZERO) return "0H 0M"
    val total = d.inWholeMinutes
    val h = total / 60
    val m = total % 60
    return "${h}H ${m}M"
}
