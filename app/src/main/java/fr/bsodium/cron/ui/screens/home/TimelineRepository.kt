package fr.bsodium.cron.ui.screens.home

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime

private const val TAG = "TimelineRepository"

// Sessions per Pager page; each page fetches its own events + AI-message rows on top, so this stays
// small rather than matching a typical list page size.
private const val HISTORY_PAGE_SIZE = 6

// Matches the old loadHistory()'s fixed first load, so first paint isn't slower than before pagination.
private const val HISTORY_INITIAL_LOAD_SIZE = 10

// Sessions; tuned against HomeContent.kt's own TIMELINE_PREFETCH_AHEAD cache window once live-verified
// (#233) rather than guessed -- a fetch fired too eagerly can double up with that existing margin.
private const val HISTORY_PREFETCH_DISTANCE = 2

// Sessions; must be >= pageSize + 2*prefetchDistance (Paging's own invariant).
private const val HISTORY_MAX_SIZE = 24

/**
 * Loads historical sessions (with their events and AI turns) for the timeline, paginated. The
 * current/latest session is handled reactively by [HomeViewModel]'s own flows; this class only covers
 * the settled past.
 */
class TimelineRepository(private val db: CronDatabase) {

    fun historyFlow(excludeSessionId: String?): Flow<PagingData<TimelineItem>> =
        Pager(
            config = PagingConfig(
                pageSize = HISTORY_PAGE_SIZE,
                initialLoadSize = HISTORY_INITIAL_LOAD_SIZE,
                prefetchDistance = HISTORY_PREFETCH_DISTANCE,
                maxSize = HISTORY_MAX_SIZE,
                // Forced off by sessionToTimelineItems' 1-session-to-many-TimelineItems flatMap below --
                // placeholder counting is undefined once one input row can expand into several outputs.
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { db.sessionDao().historyPagingSource(excludeSessionId) },
        ).flow.map { pagingData ->
            pagingData
                .flatMap { session -> sessionToTimelineItems(session) }
                .insertSeparators { before, after -> historyDaySeparator(before, after) }
        }

    private suspend fun sessionToTimelineItems(session: SessionEntity): List<TimelineItem> {
        val events = runCatching { db.eventDao().findBySession(session.id).map { it.toModel() } }
            .onFailure { Log.w(TAG, "Failed to load events for session ${session.id}", it) }
            .getOrDefault(emptyList())
        val aiRows = db.aiMessageDao().findBySession(session.id)
        val plan = AiPlanMapper.buildPlan(aiRows, streaming = null, events = events)
        return buildSessionItems(
            TimelineSession(
                sessionId = session.id,
                iterations = plan?.iterations.orEmpty(),
                events = events,
                streamingTurnIndex = null,
            ),
        )
    }

    /** The most recent OLDER session's own last resolved alarm time, for [HomeViewModel]'s
     *  carry-over-into-turn-0 patch — a fresh session's own turn 0 never has an intra-session previous
     *  time to compare against. A single-row read + the same per-session [AiPlanMapper.buildPlan]
     *  derivation [sessionToTimelineItems] already does, not a second data path. */
    suspend fun mostRecentOlderAlarmTime(excludeSessionId: String?): LocalTime? {
        val session = db.sessionDao().findMostRecentExcluding(excludeSessionId) ?: return null
        val events = runCatching { db.eventDao().findBySession(session.id).map { it.toModel() } }
            .onFailure { Log.w(TAG, "Failed to load events for session ${session.id}", it) }
            .getOrDefault(emptyList())
        val aiRows = db.aiMessageDao().findBySession(session.id)
        val plan = AiPlanMapper.buildPlan(aiRows, streaming = null, events = events)
        return plan?.iterations?.lastOrNull { it.thread.newAlarmTime != null }?.thread?.newAlarmTime
    }
}
