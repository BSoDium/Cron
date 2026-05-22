package fr.bsodium.cron.ai

import android.content.Context
import fr.bsodium.cron.ai.tools.CancelAlarmTool
import fr.bsodium.cron.ai.tools.DoNothingTool
import fr.bsodium.cron.ai.tools.NotifyWarningTool
import fr.bsodium.cron.ai.tools.ReadCalendarTool
import fr.bsodium.cron.ai.tools.SendBriefTool
import fr.bsodium.cron.ai.tools.SetAlarmTool
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.calendar.CalendarReader
import fr.bsodium.cron.location.LocationProvider
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.settings.SecureKeyStore
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import java.util.UUID

/**
 * Single-shot validator for the full evening planning flow. Creates a throwaway
 * session for tomorrow, runs one TurnRunner pass with the complete evening tool
 * set, and returns the final assistant text + tool round-trip count.
 *
 * Intended for the "AI diagnostics" debug card on Home. Any alarm scheduled
 * during the run is cancelled before this function returns.
 */
class SmokeTest(private val context: Context) {

    data class Outcome(
        val ok: Boolean,
        val assistantText: String?,
        val roundTrips: Int,
        val error: String? = null,
    )

    suspend fun run(): Outcome {
        val secureStore = SecureKeyStore(context)
        val apiKey = secureStore.anthropicApiKey
            ?: return Outcome(ok = false, assistantText = null, roundTrips = 0, error = "API key not set")

        val database = CronDatabase.get(context)
        val sessionId = "smoke-${UUID.randomUUID()}"
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val localNow = now.toLocalDateTime(tz)
        val tomorrow = localNow.date.plus(1, DateTimeUnit.DAY)

        seedSession(database, sessionId, tomorrow, tz)
        val repository = SessionRepository(context)
        val scheduler = AlarmScheduler(context)

        val cleanup: suspend () -> Unit = {
            markComplete(database, sessionId)
            scheduler.cancel(tomorrow)
        }

        val tools = ToolRegistry(listOf(
            ReadCalendarTool(CalendarReader(context.contentResolver)),
            SetAlarmTool(
                scheduler = scheduler,
                repository = repository,
                sessionId = sessionId,
                sessionDate = tomorrow,
                timezone = tz,
                hardLatest = LocalTime(10, 0),
            ),
            DoNothingTool(repository, sessionId),
            CancelAlarmTool(scheduler, repository, sessionId, tomorrow),
            SendBriefTool(context, repository, sessionId),
            NotifyWarningTool(context, repository, sessionId),
        ))

        val client = AnthropicClient(apiKeyProvider = { apiKey })
        val runner = TurnRunner(
            client = client,
            aiMessageDao = database.aiMessageDao(),
            model = TurnRunner.MODEL_SONNET,
            systemPrompt = SystemPrompts.EVENING_PLAN,
            tools = tools,
            maxRoundTrips = 8,
        )

        val location = LocationProvider(context).acquireForEveningPlan()
        val locationSection = if (location.source == LocationSource.Unavailable) {
            "## Location\n- Source: unavailable — apply a flat +30 min buffer and mention it in the reason"
        } else {
            "## Current location (captured at session start)\n" +
                "- Latitude: ${location.lat}, Longitude: ${location.lng}\n" +
                "- Source: ${location.source.name.lowercase()}\n" +
                "- Captured at: ${location.capturedAt}"
        }

        val userMessage = """
            Smoke test. It is now $localNow ($tz).

            ## Session context
            - Morning date: $tomorrow
            - Hard latest (never exceed): 10:00
            - Wake window: 07:00–09:30
            - Minimum commute buffer: 30 min

            $locationSection

            Read the calendar for the next 24 hours and plan tomorrow's alarm.
            Follow the process in your system prompt: read calendar, identify anchor event, then call set_alarm (or do_nothing if there are no actionable events tomorrow).
        """.trimIndent()

        return try {
            val outcome = runner.run(sessionId = sessionId, turnIndex = 0, initialUserMessage = userMessage)
            val (text, rounds) = when (outcome) {
                is TurnRunner.Outcome.Completed -> {
                    outcome.response.content.filterIsInstance<ContentBlock.Text>()
                        .joinToString("\n") { it.text } to roundTripCount(database, sessionId)
                }
                is TurnRunner.Outcome.BudgetExhausted -> {
                    "(budget exhausted)" to outcome.roundTrips
                }
            }
            Outcome(ok = true, assistantText = text.ifBlank { "(no text content)" }, roundTrips = rounds)
        } catch (t: Throwable) {
            Outcome(ok = false, assistantText = null, roundTrips = 0, error = t.message ?: t::class.simpleName)
        } finally {
            cleanup()
        }
    }

    private suspend fun seedSession(
        db: CronDatabase,
        sessionId: String,
        tomorrow: kotlinx.datetime.LocalDate,
        tz: TimeZone,
    ) {
        val now = Clock.System.now()
        val plan = DayPlan(
            hardLatest = LocalTime(10, 0),
            wakeWindowStart = LocalTime(7, 0),
            wakeWindowEnd = LocalTime(9, 30),
            commuteBufferMinutes = 30,
            isFreeDayFallback = true,
            generatedAt = now,
        )
        val instruction = Instruction(action = ActionType.DoNothing, reason = "smoke seed", issuedAt = now)
        val entity = SessionEntity(
            id = sessionId,
            date = tomorrow.toString(),
            status = SessionStatus.Planning.name,
            planJson = SessionJson.encodeToString(plan),
            currentInstructionJson = SessionJson.encodeToString(instruction),
            lastAiCallAt = null,
            snoozeCount = 0,
            timezone = tz.id,
            cachedFirstEventSig = null,
            createdAt = now.toEpochMilliseconds(),
            updatedAt = now.toEpochMilliseconds(),
        )
        db.sessionDao().insertOrReplace(entity)
    }

    private suspend fun markComplete(db: CronDatabase, sessionId: String) {
        val entity = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().update(
            entity.copy(
                status = SessionStatus.Complete.name,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    private suspend fun roundTripCount(db: CronDatabase, sessionId: String): Int {
        val all = db.aiMessageDao().findBySession(sessionId)
        return all.count { it.role == "assistant" }
    }
}
