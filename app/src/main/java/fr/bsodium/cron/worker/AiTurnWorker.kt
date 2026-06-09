package fr.bsodium.cron.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
import fr.bsodium.cron.ai.wire.ThinkingConfig
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
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.latestEveningPlanLocation
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.travel.GeocodingClient
import fr.bsodium.cron.travel.LatLng
import fr.bsodium.cron.travel.RoutesClient
import kotlinx.datetime.TimeZone

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
    private val settingsRepository = SettingsRepository(applicationContext)

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
            return Result.failure(workDataOf(KEY_REASON to REASON_NO_API_KEY))
        }

        val budget = BudgetStore(applicationContext)
        val limit = settingsRepository.currentDailyTokenLimit()
        if (!budget.hasHeadroom(limit)) {
            val used = budget.usedToday()
            Log.w(TAG, "Daily token budget exhausted ($used / $limit); skipping turn for $sessionId")
            return Result.failure(workDataOf(KEY_REASON to REASON_BUDGET, KEY_USED to used, KEY_LIMIT to limit))
        }

        val turnIndex = (db.aiMessageDao().maxTurnIndex(sessionId) ?: -1) + 1
        // A manual replan re-appends an EveningPlan event (→ Sonnet); sensor-driven overnight
        // replans leave the latest trigger ≠ EveningPlan, so they stay on Haiku.
        val isEveningPlan = session.events.lastOrNull()?.trigger == TriggerType.EveningPlan

        val model = if (isEveningPlan) TurnRunner.MODEL_SONNET else TurnRunner.MODEL_HAIKU
        val systemPrompt = if (isEveningPlan) SystemPrompts.EVENING_PLAN else SystemPrompts.OVERNIGHT_REPLAN

        val tools = buildToolRegistry(session, apiKey)
        val client = AnthropicClient(apiKeyProvider = { apiKey })
        // Anthropic requires max_tokens > thinking budget, so widen the ceiling on evening_plan turns.
        val thinking = if (isEveningPlan) ThinkingConfig(budgetTokens = THINKING_BUDGET) else null
        val maxTokens = if (isEveningPlan) THINKING_BUDGET + 2048 else 2048
        val runner = TurnRunner(
            client = client,
            aiMessageDao = db.aiMessageDao(),
            model = model,
            systemPrompt = systemPrompt,
            tools = tools,
            maxTokens = maxTokens,
            thinking = thinking,
        )

        val instructions = settingsRepository.currentUserInstructions()
        val userMessage = AiPromptBuilder.build(session, isEveningPlan, instructions)

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
            Result.failure(workDataOf(KEY_REASON to REASON_NO_API_KEY))
        } catch (e: AnthropicClient.AnthropicHttpException) {
            Log.e(TAG, "Anthropic HTTP ${e.code} during turn for $sessionId", e)
            if (e.isRetryable) Result.retry() else Result.failure(workDataOf(KEY_REASON to REASON_HTTP))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during AI turn for $sessionId", e)
            Result.retry()
        }
    }

    private fun buildToolRegistry(session: SleepSession, apiKey: String): ToolRegistry {
        val tools = mutableListOf<Tool>()
        tools.add(ReadCalendarTool(CalendarReader(applicationContext.contentResolver)))

        val routesKey = BuildConfig.GOOGLE_ROUTES_API_KEY.takeIf { it.isNotBlank() }
        if (routesKey != null) {
            val sharedHttp = RoutesClient.defaultHttp()
            val routesClient = RoutesClient(routesKey, sharedHttp)
            val geocoder = GeocodingClient(routesKey, sharedHttp)
            // The device's captured location anchors geocoding + commute to the user's actual area, so
            // ambiguous destinations resolve nearby (not the capital) and the origin is never a bogus (0,0).
            // The LATEST fix, via the shared helper — the prompt reads the same one, so they can't diverge.
            val bias = session.latestEveningPlanLocation()
                ?.takeIf { it.source != LocationSource.Unavailable }
                ?.let { LatLng(it.lat, it.lng) }
            tools.add(GeocodeTool(geocoder, bias))
            // Enforce the user's allowed commute modes in the tools themselves — prompt prose alone still
            // hands the model an excluded mode's duration to plan around.
            tools.add(EstimateCommuteTool(routesClient, geocoder, bias, session.plan.allowedCommuteModes))
            tools.add(EstimateCommuteMultiModeTool(routesClient, geocoder, bias, session.plan.allowedCommuteModes))
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

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val WORK_PREFIX = "ai_turn_"

        /** Failure output: why the turn was skipped, plus budget figures when relevant. Read by HomeViewModel. */
        const val KEY_REASON = "reason"
        const val KEY_USED = "used"
        const val KEY_LIMIT = "limit"
        const val REASON_BUDGET = "budget_exhausted"
        const val REASON_NO_API_KEY = "no_api_key"
        const val REASON_HTTP = "http_error"

        private const val TAG = "AiTurnWorker"
        // Total across the turn; with interleaved thinking it's spread over a fresh think after each
        // tool result. Kept modest so reasoning stays on the anchor decision, not mechanical recompute.
        private const val THINKING_BUDGET = 2_560
    }
}
