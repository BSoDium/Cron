package fr.bsodium.cron.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bsodium.cron.BuildConfig
import fr.bsodium.cron.CronApplication
import fr.bsodium.cron.MainActivity
import fr.bsodium.cron.R
import fr.bsodium.cron.ai.AnthropicClient
import fr.bsodium.cron.ai.BudgetStore
import fr.bsodium.cron.ai.SystemPrompts
import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolRegistry
import fr.bsodium.cron.ai.TurnRunner
import fr.bsodium.cron.ai.tools.CancelAlarmTool
import fr.bsodium.cron.ai.tools.DoNothingTool
import fr.bsodium.cron.ai.tools.EstimateCommuteMultiModeTool
import fr.bsodium.cron.ai.tools.EstimateCommuteTool
import fr.bsodium.cron.ai.tools.GeocodeTool
import fr.bsodium.cron.ai.tools.NotifyWarningTool
import fr.bsodium.cron.ai.tools.ReadCalendarTool
import fr.bsodium.cron.ai.tools.SendBriefTool
import fr.bsodium.cron.ai.tools.SetAlarmTool
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.calendar.CalendarReader
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.travel.GeocodingClient
import fr.bsodium.cron.travel.RoutesClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Resumable WorkManager worker that drives one AI tool-use turn for a session.
 *
 * The turn index is resolved at startup by reading the max already-persisted
 * index from Room (+1). This means: if the OS kills the worker mid-turn and
 * WorkManager re-runs it, [TurnRunner.loadOrSeed] finds the existing messages
 * for that turn and resumes from the last persisted block rather than starting
 * over.
 *
 * Only one instance per session can run at a time (UNIQUE_WORK with REPLACE).
 */
class AiTurnWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val repository = SessionRepository(applicationContext)
    private val db = CronDatabase.get(applicationContext)

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID)
            ?: return Result.failure()

        val session = repository.findById(sessionId)
        if (session == null) {
            Log.w(TAG, "Session $sessionId not found — skipping turn")
            return Result.failure()
        }

        val apiKey = SecureKeyStore(applicationContext).anthropicApiKey
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Anthropic API key not set — skipping AI turn for $sessionId")
            return Result.failure()
        }

        val budget = BudgetStore(applicationContext)
        if (!budget.hasHeadroom()) {
            Log.w(TAG, "Daily token budget exhausted (${budget.usedToday()} used); skipping turn for $sessionId")
            return Result.failure()
        }

        val turnIndex = (db.aiMessageDao().maxTurnIndex(sessionId) ?: -1) + 1
        val isEveningPlan = session.events.lastOrNull()?.trigger == TriggerType.EveningPlan

        val model = if (isEveningPlan) TurnRunner.MODEL_SONNET else TurnRunner.MODEL_HAIKU
        val systemPrompt = if (isEveningPlan) SystemPrompts.EVENING_PLAN else SystemPrompts.OVERNIGHT_REPLAN

        val tools = buildToolRegistry(session, apiKey)
        val client = AnthropicClient(apiKeyProvider = { apiKey })
        val runner = TurnRunner(
            client = client,
            aiMessageDao = db.aiMessageDao(),
            model = model,
            systemPrompt = systemPrompt,
            tools = tools,
        )

        val userMessage = buildUserMessage(session, isEveningPlan)

        return try {
            val outcome = runner.run(sessionId, turnIndex, userMessage)
            budget.record(outcome.totalUsage)
            when (outcome) {
                is TurnRunner.Outcome.Completed ->
                    Log.i(TAG, "Turn $turnIndex complete for $sessionId (stop=${outcome.response.stop_reason}, tokens=${outcome.totalUsage.input_tokens + outcome.totalUsage.output_tokens})")
                is TurnRunner.Outcome.BudgetExhausted ->
                    Log.w(TAG, "Turn $turnIndex round-trip budget exhausted after ${outcome.roundTrips} round-trips for $sessionId")
            }
            if (BuildConfig.DEBUG && isEveningPlan) {
                postPlanningNotification(sessionId)
            }
            Result.success()
        } catch (e: AnthropicClient.MissingApiKeyException) {
            Log.e(TAG, "Missing API key during turn", e)
            Result.failure()
        } catch (e: AnthropicClient.AnthropicHttpException) {
            Log.e(TAG, "Anthropic HTTP ${e.code} during turn for $sessionId", e)
            if (e.isRetryable) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during AI turn for $sessionId", e)
            Result.retry()
        }
    }

    // ---------------------------------------------------------------------------
    // Tool registry
    // ---------------------------------------------------------------------------

    private fun buildToolRegistry(session: SleepSession, apiKey: String): ToolRegistry {
        val tools = mutableListOf<Tool>()
        tools.add(ReadCalendarTool(CalendarReader(applicationContext.contentResolver)))

        val routesKey = BuildConfig.GOOGLE_ROUTES_API_KEY.takeIf { it.isNotBlank() }
        if (routesKey != null) {
            val sharedHttp = RoutesClient.defaultHttp()
            val routesClient = RoutesClient(routesKey, sharedHttp)
            tools.add(GeocodeTool(GeocodingClient(routesKey, sharedHttp)))
            tools.add(EstimateCommuteTool(routesClient))
            tools.add(EstimateCommuteMultiModeTool(routesClient))
        }

        tools.add(SetAlarmTool(
            scheduler = AlarmScheduler(applicationContext),
            repository = repository,
            sessionId = session.id,
            sessionDate = session.date,
            timezone = TimeZone.of(session.timezone),
            hardLatest = session.plan.hardLatest,
        ))
        tools.add(DoNothingTool(repository, session.id))
        tools.add(CancelAlarmTool(AlarmScheduler(applicationContext), repository, session.id, session.date))
        tools.add(SendBriefTool(applicationContext, repository, session.id))
        tools.add(NotifyWarningTool(applicationContext, repository, session.id))

        return ToolRegistry(tools)
    }

    // ---------------------------------------------------------------------------
    // User message construction
    // ---------------------------------------------------------------------------

    private fun buildUserMessage(session: SleepSession, isEveningPlan: Boolean): String {
        val now = Clock.System.now()
        val tz = TimeZone.of(session.timezone)
        val localNow = now.toLocalDateTime(tz)
        val plan = session.plan

        return if (isEveningPlan) buildEveningPlanMessage(session, localNow, tz)
        else buildOvernightReplanMessage(session, localNow)
    }

    private fun buildEveningPlanMessage(
        session: SleepSession,
        localNow: kotlinx.datetime.LocalDateTime,
        tz: TimeZone,
    ): String {
        val plan = session.plan
        val eveningEvent = session.events.firstOrNull { it.trigger == TriggerType.EveningPlan }
        val location = (eveningEvent?.data as? EventData.EveningPlan)?.location

        return buildString {
            appendLine("It is now $localNow (${session.timezone}).")
            appendLine()
            appendLine("## Session context")
            appendLine("- Morning date: ${session.date}")
            appendLine("- Hard latest (never exceed): ${plan.hardLatest}")
            appendLine("- Wake window: ${plan.wakeWindowStart} – ${plan.wakeWindowEnd}")
            appendLine("- Minimum commute buffer: ${plan.commuteBufferMinutes} min")
            appendLine("- Personal preparation buffer: ${plan.preparationBufferMinutes} min")
            appendLine("- Free day wake window: ${plan.wakeWindowStart} – ${plan.wakeWindowEnd}")
            appendLine()
            if (location != null) {
                appendLine("## Current location (captured at session start)")
                appendLine("- Latitude: ${location.lat}, Longitude: ${location.lng}")
                appendLine("- Source: ${location.source.name.lowercase()}")
                appendLine("- Accuracy: ±${location.accuracyMeters?.let { "${it.toInt()} m" } ?: "unknown"}")
                appendLine("- Captured at: ${location.capturedAt}")
            } else {
                appendLine("## Location")
                appendLine("- Source: unavailable — apply a flat +30 min buffer and mention it in the reason")
            }
            appendLine()
            appendLine("Plan tomorrow's alarm. Follow the process in your system prompt: read calendar, identify anchor event, estimate commute if applicable, then call set_alarm.")
        }
    }

    private fun isPhoneOnlyMode(session: SleepSession): Boolean {
        val sessionAge = Clock.System.now() - session.createdAt
        if (sessionAge < PHONE_ONLY_THRESHOLD) return false
        return session.events.none {
            it.trigger == TriggerType.HcStageUpdate &&
                (it.data as? EventData.HcStageUpdate)?.confidence == SignalConfidence.High
        }
    }

    private fun buildOvernightReplanMessage(
        session: SleepSession,
        localNow: kotlinx.datetime.LocalDateTime,
    ): String {
        val plan = session.plan
        val instr = session.currentInstruction

        return buildString {
            appendLine("It is now $localNow (${session.timezone}).")
            appendLine()
            if (isPhoneOnlyMode(session)) {
                appendLine("**Note: phone-only mode is active.** No high-confidence Health Connect data has arrived in the past 90 minutes. Rely exclusively on screen-state and activity signals. Do not request or expect sleep stage updates; treat any stage signals as low-confidence approximations.")
                appendLine()
            }
            appendLine("## Day plan")
            appendLine("- Morning date: ${session.date}")
            appendLine("- Hard latest (never exceed): ${plan.hardLatest}")
            appendLine("- Wake window: ${plan.wakeWindowStart} – ${plan.wakeWindowEnd}")
            appendLine("- Snooze count so far: ${session.snoozeCount}")
            appendLine()
            appendLine("## Current instruction")
            appendLine("- Action: ${instr.action.name}")
            if (instr.alarmTime != null) appendLine("- Alarm set for: ${instr.alarmTime}")
            appendLine("- Reason: ${instr.reason}")
            appendLine()
            appendLine("## Event log (oldest first)")
            session.events.forEachIndexed { i, ev ->
                appendLine("${i + 1}. [${ev.timestamp}] ${ev.trigger.name}: ${summarizeEventData(ev)}")
            }
            appendLine()
            val last = session.events.lastOrNull()
            if (last != null) {
                appendLine("## Triggering event")
                appendLine("[${last.timestamp}] ${last.trigger.name}: ${summarizeEventData(last)}")
            }
            appendLine()
            appendLine("Decide what the alarm system should do. Call set_alarm if you want to adjust the wake time. If the current alarm is already optimal, respond with a brief explanation and do not call any tool.")
        }
    }

    // ---------------------------------------------------------------------------
    // Debug-only planning notification
    // ---------------------------------------------------------------------------

    private suspend fun postPlanningNotification(sessionId: String) {
        val updated = repository.findById(sessionId) ?: return
        val instruction = updated.currentInstruction
        val title = when (instruction.action) {
            ActionType.SetAlarm ->
                instruction.alarmTime?.let { "Alarm set for $it" } ?: "Alarm set (time unknown)"
            ActionType.CancelAlarm -> "Alarm cancelled"
            ActionType.DoNothing -> "No alarm scheduled"
            ActionType.SendBrief -> "Brief sent"
            ActionType.NotifyWarning -> "Warning posted"
        }
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CronApplication.PLANNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(instruction.reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(instruction.reason))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(CronApplication.PLANNING_NOTIFICATION_ID, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied; planning notification dropped", se)
        }
    }

    private fun summarizeEventData(event: SessionEvent): String = when (val d = event.data) {
        is EventData.EveningPlan -> "timezone=${d.timezone}, location_source=${d.location.source}"
        is EventData.SleepOnset -> "screen_off_since=${d.screenOffSince}, rearm=${d.rearm}"
        is EventData.HcStageUpdate -> "stage=${d.stage}, source=${d.source}, confidence=${d.confidence}"
        is EventData.MidSleepActivity -> "activity=${d.activityType}, screen_on=${d.screenOn}, duration=${d.durationSeconds}s"
        is EventData.OutOfBedConfirmed -> "evidence=${d.evidence}"
        is EventData.WakeWindowOpportunity -> "stage=${d.currentStage}, window=${d.windowStart}–${d.windowEnd}"
        is EventData.AlarmInteraction -> "snooze_duration=${d.snoozeDurationMinutes}min, count=${d.snoozeCount}"
        is EventData.CalendarChange -> "type=${d.changeType}, affects_first=${d.affectsFirstEvent}"
        EventData.Empty -> "(empty)"
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val WORK_PREFIX = "ai_turn_"
        private const val TAG = "AiTurnWorker"
        private val PHONE_ONLY_THRESHOLD = 90.minutes
    }
}
