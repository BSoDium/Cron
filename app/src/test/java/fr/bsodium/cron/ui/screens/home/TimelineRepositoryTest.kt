package fr.bsodium.cron.ui.screens.home

import android.app.Application
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers [TimelineRepository.historyFlow] -- the paging pipeline behind Home's infinite-scroll history
 *  feed (#187/#229). Seeded via [Fixtures.manySessions], each session on its own calendar date, so the
 *  worked example from `historyDaySeparator`'s own KDoc (one header per date, none dropped or doubled
 *  across a Pager page boundary) is exercised against the real, resolved Paging 3 library rather than
 *  just the pure helper functions ([TimelineMapperTest] already covers those in isolation). */
@RunWith(RobolectricTestRunner::class)
class TimelineRepositoryTest {

    private lateinit var app: Application
    private lateinit var db: CronDatabase
    private lateinit var repo: TimelineRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = CronDatabase.get(app)
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM --
        // wipe it (cascades to events + ai_messages) so each test starts from a clean slate.
        runBlocking { db.sessionDao().deleteOlderThan(Long.MAX_VALUE) }
        repo = TimelineRepository(db)
    }

    private suspend fun seed(sessions: List<fr.bsodium.cron.session.model.SleepSession>) {
        for (session in sessions) {
            db.sessionDao().insert(session.toEntity())
            for (event in session.events) {
                db.eventDao().insert(event.toEntity(session.id))
            }
        }
    }

    @Test
    fun historyFlow_returns_one_day_header_between_every_pair_of_adjacent_sessions() = runTest {
        // More than HISTORY_PAGE_SIZE (6) so this crosses at least one real Pager page boundary.
        val sessions = Fixtures.manySessions(8)
        seed(sessions)

        val items = repo.historyFlow(excludeSessionId = null).asSnapshot {
            appendScrollWhile { true }
        }

        val headers = items.filterIsInstance<TimelineItem.DayHeader>()
        val events = items.filterIsInstance<TimelineItem.Event>()
        assertEquals(sessions.size - 1, headers.size)
        assertEquals(sessions.size * 2, events.size)
        // Reverse-chronological, latest session first: no header before the very first session's own
        // items (that leading edge is deliberately the UI layer's seamDayHeader to own, not this feed's).
        assertTrue(items.first() is TimelineItem.Event)
        // The most recent session's own later event (wake) sorts ahead of its earlier one (onset).
        assertEquals(sessions.first().events.maxOf { it.timestamp }, items.first().timestamp)
    }

    @Test
    fun historyFlow_excludes_the_live_session_by_id() = runTest {
        val sessions = Fixtures.manySessions(4)
        seed(sessions)
        val excluded = sessions[1]

        val items = repo.historyFlow(excludeSessionId = excluded.id).asSnapshot {
            appendScrollWhile { true }
        }

        val excludedTimestamps = excluded.events.map { it.timestamp }.toSet()
        assertFalse(items.any { it.timestamp in excludedTimestamps })
        assertEquals((sessions.size - 1) * 2, items.count { it is TimelineItem.Event })
    }

    /** A [TimelineItem.DayHeader]'s own `timestamp` (local midnight) isn't guaranteed to sit in strict
     *  chronological position relative to neighboring items once a non-UTC zone offset is involved --
     *  that was never an invariant of `insertDayHeaders`/`buildTimeline` either, only their *content*
     *  rows are. This asserts the real guarantee: the actual AiRun/Event rows stay strictly
     *  latest-first across every session boundary, headers aside. */
    @Test
    fun historyFlow_content_items_are_sorted_latest_first_across_session_boundaries() = runTest {
        val sessions = Fixtures.manySessions(5)
        seed(sessions)

        val items = repo.historyFlow(excludeSessionId = null).asSnapshot {
            appendScrollWhile { true }
        }

        val contentTimestamps = items.filterNot { it is TimelineItem.DayHeader }.map { it.timestamp }
        assertEquals(contentTimestamps.sortedDescending(), contentTimestamps)
    }
}
