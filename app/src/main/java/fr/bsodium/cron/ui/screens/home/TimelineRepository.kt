package fr.bsodium.cron.ui.screens.home

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime

private const val TAG = "TimelineRepository"

/** Sessions per Pager page; each page fetches its own events + AI-message rows on top, so this stays
 *  small rather than matching a typical list page size. */
private const val HISTORY_PAGE_SIZE = 6

// Matches the old loadHistory()'s fixed first load, so first paint isn't slower than before pagination.
private const val HISTORY_INITIAL_LOAD_SIZE = 10

/** Sessions; tuned against HomeContent.kt's own `TIMELINE_PREFETCH_AHEAD` cache window once
 *  live-verified (#233) rather than guessed — a fetch fired too eagerly can double up with that
 *  existing margin. */
private const val HISTORY_PREFETCH_DISTANCE = 2

// Sessions; must be >= pageSize + 2*prefetchDistance (Paging's own invariant).
private const val HISTORY_MAX_SIZE = 24

/** How many older sessions [TimelineRepository.mostRecentOlderAlarmTime] is willing to walk back
 *  through looking for one that resolved an alarm — matches the old (pre-Paging) `loadRecentHistory()`'s
 *  `HISTORY_LOAD_SIZE` bound, not a fresh guess. */
private const val CARRY_OVER_SCAN_LIMIT = 10

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
                // Forced off: sessionToTimelineItems' 1-session-to-many-TimelineItems flatMap below makes placeholder counting undefined.
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { db.sessionDao().historyPagingSource(excludeSessionId) },
        ).flow.map { pagingData ->
            /** Fresh per Pager generation (this whole `map` block re-runs on every new `PagingData`), so
             *  this never leaks state across a Pager rebuild — mirrors [buildTimeline]'s own defensive
             *  `distinctBy` (TimelineMapper.kt), which a data-layer race guards against for the live
             *  session; the paged historical path needs the same guard, since [sessionToTimelineItems]
             *  below never runs through [buildTimeline] itself. */
            val seenIds = mutableSetOf<String>()
            pagingData
                .flatMap { session -> sessionToTimelineItems(session) }
                .filter { seenIds.add(it.id) }
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

    /** The most recent OLDER session that actually resolved an alarm, for [HomeViewModel]'s
     *  carry-over-into-turn-0 patch — a fresh session's own turn 0 never has an intra-session previous
     *  time to compare against. Walks up to [CARRY_OVER_SCAN_LIMIT] older sessions, most-recent-first,
     *  not just the single most recent one: that one alone might never have resolved an alarm (the AI
     *  cancelled it, auto-alarms were off, the turn errored) even though an older one did — matching
     *  this codebase's old (pre-Paging) history-scan behavior, not narrowing it. Reuses the same
     *  per-session [AiPlanMapper.buildPlan] derivation [sessionToTimelineItems] already does, not a
     *  second data path. */
    suspend fun mostRecentOlderAlarmTime(excludeSessionId: String?): LocalTime? {
        val candidates = db.sessionDao().findRecentExcluding(excludeSessionId, CARRY_OVER_SCAN_LIMIT)
        for (session in candidates) {
            val events = runCatching { db.eventDao().findBySession(session.id).map { it.toModel() } }
                .onFailure { Log.w(TAG, "Failed to load events for session ${session.id}", it) }
                .getOrDefault(emptyList())
            val aiRows = db.aiMessageDao().findBySession(session.id)
            val plan = AiPlanMapper.buildPlan(aiRows, streaming = null, events = events)
            val alarmTime = plan?.iterations?.lastOrNull { it.thread.newAlarmTime != null }?.thread?.newAlarmTime
            if (alarmTime != null) return alarmTime
        }
        return null
    }
}
