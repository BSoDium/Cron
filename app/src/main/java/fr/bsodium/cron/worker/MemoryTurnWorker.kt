package fr.bsodium.cron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.bsodium.cron.ai.AnthropicClient
import fr.bsodium.cron.ai.AnthropicClientFactory
import fr.bsodium.cron.ai.BudgetStore
import fr.bsodium.cron.ai.MemoryTurnRunner
import fr.bsodium.cron.ai.SystemPrompts
import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolRegistry
import fr.bsodium.cron.ai.ToolRegistryFactory
import fr.bsodium.cron.ai.TurnRunner
import fr.bsodium.cron.ai.tools.AddMemoryTool
import fr.bsodium.cron.ai.tools.DeleteMemoryTool
import fr.bsodium.cron.ai.tools.UpdateMemoryTool
import fr.bsodium.cron.memory.MemoryPromptBuilder
import fr.bsodium.cron.memory.MemoryRepository
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository

/**
 * One-shot WorkManager worker that drives the memory-mutation turn: given the user's typed
 * instruction, lets the model add/edit/delete memory entries via [AddMemoryTool]/[UpdateMemoryTool]/
 * [DeleteMemoryTool]. Unlike [AiTurnWorker], this has no session/turn to resolve or persist a
 * transcript against — see [MemoryTurnRunner]'s own KDoc for why.
 *
 * Only one instance can run at a time (UNIQUE_WORK with REPLACE).
 */
class MemoryTurnWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val repository = MemoryRepository(applicationContext)
    private val settingsRepository = SettingsRepository(applicationContext)

    override suspend fun doWork(): Result {
        val instruction = inputData.getString(KEY_INSTRUCTION)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        val useMock = ToolRegistryFactory.shouldUseMock(applicationContext)
        val apiKey = SecureKeyStore(applicationContext).anthropicApiKey
        if (!useMock && apiKey.isNullOrBlank()) {
            Log.w(TAG, "Anthropic API key not set — skipping memory turn")
            return Result.failure(workDataOf(KEY_REASON to REASON_NO_API_KEY))
        }

        val budget = BudgetStore(applicationContext)
        val limit = settingsRepository.currentDailyTokenLimit()
        if (!useMock && !budget.hasHeadroom(limit)) {
            val used = budget.usedToday()
            Log.w(TAG, "Daily token budget exhausted ($used / $limit); skipping memory turn")
            return Result.failure(workDataOf(KEY_REASON to REASON_BUDGET, KEY_USED to used, KEY_LIMIT to limit))
        }

        // Unlike AiTurnWorker, mock mode never swaps the tool registry here: ToolRegistryFactory's
        // mockOrNull() returns FakeToolRegistry, which is built for the planning turn's tools
        // (read_calendar, set_alarm, ...) and has no memory tools at all. The real Add/Update/
        // DeleteMemoryTool trio writes to Room regardless of mock mode — only the LLM client is faked,
        // matching FakeAnthropicClient's own contract ("tool calls still execute against real tools").
        val tools = buildToolRegistry()
        val client = AnthropicClientFactory.create(useMock, apiKeyProvider = { apiKey })
        val runner = MemoryTurnRunner(
            client = client,
            // A mutation turn is a structured, low-complexity task — same tier as an overnight replan.
            model = TurnRunner.MODEL_HAIKU,
            systemPrompt = SystemPrompts.MEMORY_MUTATION,
            tools = tools,
        )

        val prompt = MemoryPromptBuilder.buildMutationPrompt(repository.currentAll(), instruction)

        return try {
            val outcome = runner.run(prompt)
            when (outcome) {
                is MemoryTurnRunner.Outcome.Completed ->
                    Log.i(TAG, "Memory turn complete (stop=${outcome.response.stop_reason})")
                is MemoryTurnRunner.Outcome.BudgetExhausted ->
                    Log.w(TAG, "Memory turn round-trip budget exhausted after ${outcome.roundTrips} round-trips")
            }
            outcome.usage()?.let(budget::record)
            Result.success()
        } catch (e: AnthropicClient.MissingApiKeyException) {
            Log.e(TAG, "Missing API key during memory turn", e)
            Result.failure(workDataOf(KEY_REASON to REASON_NO_API_KEY))
        } catch (e: AnthropicClient.AnthropicHttpException) {
            Log.e(TAG, "Anthropic HTTP ${e.code} during memory turn", e)
            if (e.isRetryable && runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
            else Result.failure(workDataOf(KEY_REASON to REASON_HTTP))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during memory turn", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
            else Result.failure(workDataOf(KEY_REASON to REASON_MAX_RETRIES))
        }
    }

    private fun MemoryTurnRunner.Outcome.usage() = when (this) {
        is MemoryTurnRunner.Outcome.Completed -> response.usage
        is MemoryTurnRunner.Outcome.BudgetExhausted -> null
    }

    private fun buildToolRegistry(): ToolRegistry {
        val tools = mutableListOf<Tool>()
        tools.add(AddMemoryTool(repository))
        tools.add(UpdateMemoryTool(repository))
        tools.add(DeleteMemoryTool(repository))
        return ToolRegistry(tools)
    }

    companion object {
        const val KEY_INSTRUCTION = "instruction"
        const val WORK_NAME = "memory_turn"

        const val KEY_REASON = "reason"
        const val KEY_USED = "used"
        const val KEY_LIMIT = "limit"
        const val REASON_BUDGET = "budget_exhausted"
        const val REASON_NO_API_KEY = "no_api_key"
        const val REASON_HTTP = "http_error"
        const val REASON_MAX_RETRIES = "max_retries_exceeded"

        private const val TAG = "MemoryTurnWorker"
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
