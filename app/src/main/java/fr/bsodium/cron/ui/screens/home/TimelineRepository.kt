package fr.bsodium.cron.ui.screens.home

import android.util.Log
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toModel

private const val TAG = "TimelineRepository"

/**
 * Loads historical sessions (with their events and AI turns) for the timeline. The current/latest
 * session is handled reactively by [HomeViewModel]'s existing flows; this class only covers the
 * settled past.
 */
class TimelineRepository(private val db: CronDatabase) {

    suspend fun loadHistory(
        excludeSessionId: String?,
        limit: Int,
        offset: Int,
    ): HistoryPage {
        // Fetch one extra row to detect whether more sessions exist beyond this page, independent of
        // how many display items they end up producing.
        val fetched = db.sessionDao().findPaginated(limit = limit + 1, offset = offset)
            .filter { it.id != excludeSessionId }
        val hasMore = fetched.size > limit
        val sessions = fetched.take(limit)

        val result = sessions.map { session ->
            val events = runCatching { db.eventDao().findBySession(session.id).map { it.toModel() } }
                .onFailure { Log.w(TAG, "Failed to load events for session ${session.id}", it) }
                .getOrDefault(emptyList())
            val aiRows = db.aiMessageDao().findBySession(session.id)
            val plan = AiPlanMapper.buildPlan(aiRows, streaming = null, events = events)

            TimelineSession(
                sessionId = session.id,
                iterations = plan?.iterations.orEmpty(),
                events = events,
                streamingTurnIndex = null,
            )
        }

        return HistoryPage(sessions = result, hasMore = hasMore)
    }
}

data class HistoryPage(val sessions: List<TimelineSession>, val hasMore: Boolean)
