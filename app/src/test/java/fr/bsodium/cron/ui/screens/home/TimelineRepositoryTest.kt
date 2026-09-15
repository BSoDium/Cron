package fr.bsodium.cron.ui.screens.home

import android.app.Application
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** Mirrors HistorySeeder.insertTurn's shape -- a set_alarm ToolUse/ToolResult pair is what
     *  AiThreadMapper.resolveNewAlarmTime actually looks for, not a plain text turn. */
    private suspend fun seedResolvedAlarmTurn(sessionId: String, turnIndex: Int, atMs: Long, alarmAtIso: String) {
        val toolId = "test-$sessionId-$turnIndex"
        db.aiMessageDao().insert(
            AiMessageEntity(
                sessionId = sessionId,
                turnIndex = turnIndex,
                role = "assistant",
                contentJson = SessionJson.encodeToString<List<ContentBlock>>(
                    listOf(ContentBlock.ToolUse(id = toolId, name = "set_alarm", input = buildJsonObject {})),
                ),
                createdAt = atMs,
            ),
        )
        db.aiMessageDao().insert(
            AiMessageEntity(
                sessionId = sessionId,
                turnIndex = turnIndex,
                role = "user",
                contentJson = SessionJson.encodeToString<List<ContentBlock>>(
                    listOf(ContentBlock.ToolResult(tool_use_id = toolId, content = "{\"alarm_time\":\"$alarmAtIso\"}")),
                ),
                createdAt = atMs + 1_000L,
            ),
        )
    }

    /** Regression coverage for the carry-over-alarm-time patch (#230): the immediately preceding
     *  session never resolving an alarm (cancelled, auto-alarms off, an errored turn -- simulated
     *  here by simply giving it no turns at all) must not stop the scan from finding an older
     *  session that did. */
    @Test
    fun mostRecentOlderAlarmTime_skips_a_predecessor_with_no_resolved_alarm() = runTest {
        val sessions = Fixtures.manySessions(3) // [0]=newest/live, [1]=no alarm, [2]=has one
        seed(sessions)
        val resolvedIso = "2026-05-18T07:30:00Z"
        seedResolvedAlarmTurn(sessions[2].id, turnIndex = 0, atMs = 1_000L, alarmAtIso = resolvedIso)

        val result = repo.mostRecentOlderAlarmTime(excludeSessionId = sessions[0].id)

        val expected = Instant.parse(resolvedIso).toLocalDateTime(TimeZone.currentSystemDefault()).time
        assertEquals(expected, result)
    }

    @Test
    fun mostRecentOlderAlarmTime_prefers_the_nearer_session_when_both_resolved_one() = runTest {
        val sessions = Fixtures.manySessions(3)
        seed(sessions)
        seedResolvedAlarmTurn(sessions[1].id, turnIndex = 0, atMs = 1_000L, alarmAtIso = "2026-05-20T07:00:00Z")
        seedResolvedAlarmTurn(sessions[2].id, turnIndex = 0, atMs = 1_000L, alarmAtIso = "2026-05-18T06:00:00Z")

        val result = repo.mostRecentOlderAlarmTime(excludeSessionId = sessions[0].id)

        val expected = Instant.parse("2026-05-20T07:00:00Z").toLocalDateTime(TimeZone.currentSystemDefault()).time
        assertEquals(expected, result)
    }

    @Test
    fun mostRecentOlderAlarmTime_returns_null_when_nothing_ever_resolved_an_alarm() = runTest {
        val sessions = Fixtures.manySessions(3)
        seed(sessions)

        assertNull(repo.mostRecentOlderAlarmTime(excludeSessionId = sessions[0].id))
    }
}
