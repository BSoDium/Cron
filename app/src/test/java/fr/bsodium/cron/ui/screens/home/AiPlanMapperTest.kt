package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AiPlanMapperTest {

    private fun row(turn: Int, role: String, createdAt: Long = 0L, vararg blocks: ContentBlock) =
        AiMessageEntity(
            sessionId = "s",
            turnIndex = turn,
            role = role,
            contentJson = SessionJson.encodeToString<List<ContentBlock>>(blocks.toList()),
            createdAt = createdAt,
        )

    private fun assistant(turn: Int, createdAt: Long = 0L, text: String = "SUMMARY: x\n\nbody") =
        row(turn, "assistant", createdAt, ContentBlock.Text(text))

    private fun event(trigger: TriggerType, tsMs: Long, data: EventData = EventData.Empty) =
        SessionEvent(trigger = trigger, timestamp = Instant.fromEpochMilliseconds(tsMs), data = data)

    private fun localHhMm(iso: String): String {
        val t = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
        return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    }

    @Test
    fun empty_and_no_streaming_is_null() {
        assertNull(AiPlanMapper.buildPlan(emptyList(), null, emptyList()))
    }

    @Test
    fun single_turn_is_one_iteration_labelled_planned() {
        val plan = requireNotNull(AiPlanMapper.buildPlan(listOf(assistant(0)), null, emptyList()))
        assertEquals(1, plan.iterations.size)
        assertEquals(0, plan.iterations.single().turnIndex)
        assertEquals("Planned", plan.iterations.single().systemMessage)
    }

    @Test
    fun replan_iteration_names_its_triggering_event() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L))
        val events = listOf(event(TriggerType.CalendarChange, 500L, EventData.CalendarChange("modified", "e1", true)))
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null, events))
        assertEquals(listOf("Planned", "Your schedule changed"), plan.iterations.map { it.systemMessage })
        assertEquals(1, plan.iterations.last().turnIndex)
    }

    @Test
    fun a_replan_with_no_matching_event_falls_back_to_replanned() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L))
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null, emptyList()))
        assertEquals("Re-planned", plan.iterations.last().systemMessage)
    }

    @Test
    fun the_trigger_is_the_latest_event_at_or_before_the_turn_start() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L))
        val events = listOf(
            event(TriggerType.SleepOnset, 100L),
            event(TriggerType.CalendarChange, 800L, EventData.CalendarChange("added", "e2", true)),
            event(TriggerType.AlarmSnoozed, 1500L), // after the turn started — must be ignored
        )
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null, events))
        assertEquals("Your schedule changed", plan.iterations.last().systemMessage)
    }

    @Test
    fun streaming_partial_is_the_last_iteration() {
        val rows = listOf(assistant(0, createdAt = 0L))
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 1, blocks = listOf(ContentBlock.Text("SUMMARY: y\n\nz")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming, emptyList()))
        assertEquals(2, plan.iterations.size)
        assertTrue(plan.iterations.last().thread.isStreaming)
    }

    @Test
    fun streaming_partial_overrides_its_persisted_turn_without_duplicating() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L, text = "old"))
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 1, blocks = listOf(ContentBlock.Text("SUMMARY: live\n\nnow")), startedAtMs = 1000L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming, emptyList()))
        assertEquals(2, plan.iterations.size)
        assertTrue(plan.iterations.last().thread.isStreaming)
    }

    @Test
    fun streaming_first_plan_is_a_single_streaming_iteration() {
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 0, blocks = listOf(ContentBlock.Thinking(thinking = "deciding")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(emptyList(), streaming, emptyList()))
        assertEquals(1, plan.iterations.size)
        assertTrue(plan.iterations.single().thread.isStreaming)
        assertEquals("Planned", plan.iterations.single().systemMessage)
    }

    @Test
    fun iteration_time_label_formats_hh_mm() {
        val iso = "2026-05-22T08:35:00Z"
        val createdAt = Instant.parse(iso).toEpochMilliseconds()
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = createdAt))
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null, emptyList()))
        assertEquals(localHhMm(iso), plan.iterations.last().timeLabel)
    }

    @Test
    fun manual_bootstrap_labels_the_base_plan_planned_manually() {
        val rows = listOf(assistant(0, createdAt = 1000L))
        val events = listOf(event(TriggerType.EveningPlan, 500L, Fixtures.eveningEvent(isManual = true).data))
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null, events))
        assertEquals("Planned manually", plan.iterations.single().systemMessage)
        assertEquals(RunKind.ManualBase, plan.iterations.single().kind)
    }

    @Test
    fun scheduled_bootstrap_stays_planned_even_with_an_evening_event() {
        val rows = listOf(assistant(0, createdAt = 1000L))
        val events = listOf(event(TriggerType.EveningPlan, 500L, Fixtures.eveningEvent(isManual = false).data))
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null, events))
        assertEquals("Planned", plan.iterations.single().systemMessage)
        assertEquals(RunKind.ScheduledBase, plan.iterations.single().kind)
    }

    @Test
    fun seeded_streaming_trigger_beats_a_stale_persisted_event() {
        // A manual replan seeds its turn with EveningPlan BEFORE the event is persisted; the latest
        // persisted event is still the prior CalendarChange — the seed must win or the tab mislabels.
        val rows = listOf(assistant(0, createdAt = 0L))
        val events = listOf(event(TriggerType.CalendarChange, 500L, EventData.CalendarChange("modified", "e1", true)))
        val streaming = StreamingTurn(
            sessionId = "s", turnIndex = 1, blocks = emptyList(), startedAtMs = 1000L,
            trigger = TriggerType.EveningPlan,
        )
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming, events))
        assertEquals("Re-planned", plan.iterations.last().systemMessage)
        assertEquals(RunKind.Replan(TriggerType.EveningPlan), plan.iterations.last().kind)
    }

}
