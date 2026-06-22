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
        val totalCount = db.sessionDao().count()
        val sessions = db.sessionDao().findPaginated(limit = limit + 1, offset = offset)
            .filter { it.id != excludeSessionId }

        val result = sessions.take(limit).map { session ->
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

        val loadedSoFar = offset + sessions.size
        return HistoryPage(
            sessions = result,
            hasMore = loadedSoFar < totalCount,
        )
    }
}

data class HistoryPage(
    val sessions: List<TimelineSession>,
    val hasMore: Boolean,
)
