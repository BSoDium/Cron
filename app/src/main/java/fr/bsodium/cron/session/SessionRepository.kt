package fr.bsodium.cron.session

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.db.toModel
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.worker.AiTurnWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import java.util.UUID

/**
 * Single source of truth for sleep session state.
 *
 * The append-then-trigger pattern: every [appendEventAndTriggerAi] call
 * inserts the event to Room first, then enqueues [AiTurnWorker] as unique
 * work keyed by session id. If the AI turn is already running, REPLACE
 * restarts it so it picks up the latest event log.
 */
class SessionRepository(private val context: Context) {

    private val db get() = CronDatabase.get(context)

    suspend fun createSession(plan: DayPlan, date: LocalDate, timezone: String): SleepSession {
        val now = Clock.System.now()
        val session = SleepSession(
            id = UUID.randomUUID().toString(),
            date = date,
            status = SessionStatus.Planning,
            plan = plan,
            currentInstruction = Instruction.doNothing("Session created", now),
            events = emptyList(),
            lastAiCallAt = null,
            snoozeCount = 0,
            timezone = timezone,
            createdAt = now,
            updatedAt = now,
        )
        // insertOrReplace so a completed session already on this date (e.g. a same-day re-plan)
        // doesn't trip the UNIQUE(date) ABORT constraint.
        db.sessionDao().insertOrReplace(session.toEntity())
        return session
    }

    suspend fun findCurrent(): SleepSession? {
        val entity = db.sessionDao().findCurrent() ?: return null
        val events = db.eventDao().findBySession(entity.id).map { it.toModel() }
        return entity.toModel(events)
    }

    suspend fun findById(sessionId: String): SleepSession? {
        val entity = db.sessionDao().findById(sessionId) ?: return null
        val events = db.eventDao().findBySession(sessionId).map { it.toModel() }
        return entity.toModel(events)
    }

    suspend fun appendEvent(sessionId: String, event: SessionEvent) {
        db.eventDao().insert(event.toEntity(sessionId))
    }

    /** Appends the event then enqueues an AI replan, replacing any in-flight turn. */
    suspend fun appendEventAndTriggerAi(sessionId: String, event: SessionEvent) {
        db.eventDao().insert(event.toEntity(sessionId))
        triggerAiTurn(sessionId)
    }

    fun triggerAiTurn(sessionId: String) {
        val data = Data.Builder()
            .putString(AiTurnWorker.KEY_SESSION_ID, sessionId)
            .build()
        val request = OneTimeWorkRequestBuilder<AiTurnWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${AiTurnWorker.WORK_PREFIX}$sessionId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Cancels the in-flight AI turn for [sessionId], if any. */
    fun cancelAiTurn(sessionId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("${AiTurnWorker.WORK_PREFIX}$sessionId")
    }

    /** Live WorkInfo for the session's AI turn — drives the home "working" indicator. */
    fun observeAiTurnWork(sessionId: String): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("${AiTurnWorker.WORK_PREFIX}$sessionId")

    suspend fun updateStatus(sessionId: String, status: SessionStatus) {
        val entity = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().update(entity.copy(
            status = status.name,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        ))
    }

    suspend fun updatePlan(sessionId: String, plan: DayPlan) {
        val entity = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().update(entity.copy(
            planJson = SessionJson.encodeToString(plan),
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        ))
    }

    suspend fun updateInstruction(sessionId: String, instruction: Instruction) {
        val entity = db.sessionDao().findById(sessionId) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        db.sessionDao().update(entity.copy(
            currentInstructionJson = SessionJson.encodeToString(instruction),
            lastAiCallAt = now,
            updatedAt = now,
        ))
    }

    /** Atomically increments snooze count; returns the new value. */
    suspend fun incrementSnoozeCount(sessionId: String): Int {
        val entity = db.sessionDao().findById(sessionId) ?: return 0
        val newCount = entity.snoozeCount + 1
        db.sessionDao().update(entity.copy(
            snoozeCount = newCount,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        ))
        return newCount
    }

    suspend fun updateCachedFirstEventSig(sessionId: String, sig: String?) {
        val entity = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().update(entity.copy(
            cachedFirstEventSig = sig,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        ))
    }

    companion object {
        /**
         * The "morning" date the session targets.
         *
         * Rule: if we're before 04:00 local time, we're in the tail of last night's
         * session → date = today. Otherwise, we're planning ahead for tomorrow → date = tomorrow.
         */
        fun morningDate(now: Instant, tz: TimeZone): LocalDate {
            val local = now.toLocalDateTime(tz)
            return if (local.hour < 4) local.date else local.date.plus(1, DateTimeUnit.DAY)
        }
    }
}
