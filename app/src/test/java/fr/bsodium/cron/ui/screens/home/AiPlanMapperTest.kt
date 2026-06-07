package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
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
    fun single_turn_has_no_edits() {
        val plan = AiPlanMapper.buildPlan(listOf(assistant(0)), null, emptyList())
        assertEquals(0, plan?.plan?.turnIndex)
        assertTrue(plan?.edits.orEmpty().isEmpty())
    }

    @Test
    fun replan_round_names_its_triggering_event() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L))
        val events = listOf(event(TriggerType.CalendarChange, 500L, EventData.CalendarChange("modified", "e1", true)))
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null, events)).edits.single()
        assertEquals(1, edit.turnIndex)
        assertEquals("Your schedule changed", edit.systemMessage)
    }

    @Test
    fun a_replan_with_no_matching_event_falls_back_to_replanned() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L))
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null, emptyList())).edits.single()
        assertEquals("Re-planned", edit.systemMessage)
    }

    @Test
    fun the_trigger_is_the_latest_event_at_or_before_the_turn_start() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L))
        val events = listOf(
            event(TriggerType.SleepOnset, 100L),
            event(TriggerType.CalendarChange, 800L, EventData.CalendarChange("added", "e2", true)),
            event(TriggerType.AlarmSnoozed, 1500L), // after the turn started — must be ignored
        )
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null, events)).edits.single()
        assertEquals("Your schedule changed", edit.systemMessage)
    }

    @Test
    fun streaming_partial_is_the_last_edit() {
        val rows = listOf(assistant(0, createdAt = 0L))
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 1, blocks = listOf(ContentBlock.Text("SUMMARY: y\n\nz")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming, emptyList()))
        assertEquals(1, plan.edits.size)
        assertTrue(plan.edits.single().thread.isStreaming)
    }

    @Test
    fun streaming_partial_overrides_its_persisted_turn_without_duplicating() {
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = 1000L, text = "old"))
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 1, blocks = listOf(ContentBlock.Text("SUMMARY: live\n\nnow")), startedAtMs = 1000L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming, emptyList()))
        assertEquals(1, plan.edits.size)
        assertTrue(plan.edits.single().thread.isStreaming)
    }

    @Test
    fun streaming_first_plan_has_no_edits() {
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 0, blocks = listOf(ContentBlock.Thinking(thinking = "deciding")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(emptyList(), streaming, emptyList()))
        assertTrue(plan.plan.isStreaming)
        assertTrue(plan.edits.isEmpty())
    }

    @Test
    fun edit_time_label_formats_hh_mm() {
        val iso = "2026-05-22T08:35:00Z"
        val createdAt = Instant.parse(iso).toEpochMilliseconds()
        val rows = listOf(assistant(0, createdAt = 0L), assistant(1, createdAt = createdAt))
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null, emptyList())).edits.single()
        assertEquals(localHhMm(iso), edit.timeLabel)
    }
}
