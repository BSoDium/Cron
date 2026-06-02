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
 * spend reaches the configured cap, [AiTurnWorker] refuses to start a new turn
 * until the next day. The cap is a user setting (see SettingsRepository); this
 * store just accounts usage and answers [hasHeadroom] for whatever cap it's given.
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

    /** True when the daily cap hasn't been reached. A [maxDailyTokens] of 0 or less means
     *  unlimited (the user disabled the cap), so headroom is always available. */
    fun hasHeadroom(maxDailyTokens: Int = DEFAULT_DAILY_TOKEN_LIMIT): Boolean =
        maxDailyTokens <= 0 || usedToday() < maxDailyTokens

    private fun currentLocalDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    companion object {
        private const val FILE = "cron_ai_budget"
        private const val KEY_DATE = "date"
        private const val KEY_USED = "tokens_used"

        /**
         * Default daily cap, used until the user picks one. Sized to comfortably cover a normal
         * day — the evening Sonnet plan plus its tool calls, a handful of overnight Haiku replans,
         * and a few manual retries — so a single busy day never trips it. The user can raise it
         * further, or disable the cap entirely, in Settings.
         */
        const val DEFAULT_DAILY_TOKEN_LIMIT = 250_000
    }
}
