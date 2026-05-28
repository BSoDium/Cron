package fr.bsodium.cron.ai

import android.content.Context
import fr.bsodium.cron.ai.wire.Usage
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Per-day token budget for AI calls.
 *
 * SharedPreferences-backed counter keyed by local date. If the user's daily
 * spend exceeds [BudgetStore.maxDailyTokens], [AiTurnWorker] refuses to
 * start a new turn until the next day.
 *
 * Counts both input + output tokens. Doesn't distinguish cached / uncached
 * input tokens — for a single-user app the precision isn't worth the
 * complexity.
 */
class BudgetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Total tokens used today (local time). Resets at local midnight. */
    fun usedToday(): Int {
        val today = currentLocalDate()
        if (prefs.getString(KEY_DATE, null) != today.toString()) return 0
        return prefs.getInt(KEY_USED, 0)
    }

    /** Add [tokens] to today's total. Idempotent across date rollover. */
    fun record(usage: Usage) {
        val today = currentLocalDate().toString()
        val stored = prefs.getString(KEY_DATE, null)
        val previous = if (stored == today) prefs.getInt(KEY_USED, 0) else 0
        val total = usage.input_tokens + usage.output_tokens +
            usage.cache_creation_input_tokens + usage.cache_read_input_tokens
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_USED, previous + total)
            .apply()
    }

    /** True when the daily limit hasn't been hit. Default 75k tokens/day. */
    fun hasHeadroom(maxDailyTokens: Int = DEFAULT_DAILY_TOKEN_LIMIT): Boolean =
        usedToday() < maxDailyTokens

    private fun currentLocalDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    companion object {
        private const val FILE = "cron_ai_budget"
        private const val KEY_DATE = "date"
        private const val KEY_USED = "tokens_used"

        /**
         * Default daily cap. With Haiku 4.5 input ~ $1/MTok, output ~ $5/MTok,
         * 75k mixed tokens is roughly $0.20/day worst-case. The evening Sonnet
         * call is the biggest single hit; overnight Haiku replans are cheap.
         */
        const val DEFAULT_DAILY_TOKEN_LIMIT = 75_000
    }
}
