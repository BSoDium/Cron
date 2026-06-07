package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun setAlarm(iso: String) =
        ContentBlock.ToolUse(id = "t", name = "set_alarm", input = SessionJson.parseToJsonElement("{\"time_iso\":\"$iso\"}"))

    private fun doNothing(reason: String) =
        ContentBlock.ToolUse(id = "t", name = "do_nothing", input = SessionJson.parseToJsonElement("{\"reason\":\"$reason\"}"))

    /** The local HH:mm the mapper will render for an ISO instant — TZ-robust expectation. */
    private fun localHhMm(iso: String): String {
        val t = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault()).time
        return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    }

    @Test
    fun empty_and_no_streaming_is_null() {
        assertNull(AiPlanMapper.buildPlan(emptyList(), null))
    }

    @Test
    fun single_turn_has_no_edits() {
        val plan = AiPlanMapper.buildPlan(
            listOf(row(0, "assistant", blocks = arrayOf(setAlarm("2026-05-22T06:40:00Z"), ContentBlock.Text("SUMMARY: Set\n\nbody")))),
            null,
        )
        assertEquals(0, plan?.plan?.turnIndex)
        assertTrue(plan?.edits.orEmpty().isEmpty())
    }

    @Test
    fun two_turns_plan_plus_one_edit() {
        val rows = listOf(
            row(0, "assistant", blocks = arrayOf(setAlarm("2026-05-22T06:40:00Z"), ContentBlock.Text("SUMMARY: Set\n\nbody"))),
            row(1, "assistant", createdAt = 1000L, blocks = arrayOf(doNothing("still on track"))),
        )
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, null))
        assertEquals(0, plan.plan.turnIndex)
        assertEquals(1, plan.edits.size)
        val edit = plan.edits.single()
        assertEquals(1, edit.turnIndex)
        assertEquals("No change · still on track", edit.summary)
        assertFalse(edit.changedAlarm)
    }

    @Test
    fun set_alarm_edit_reads_moved_when_it_differs_from_the_plan() {
        val rows = listOf(
            row(0, "assistant", blocks = arrayOf(setAlarm("2026-05-22T06:40:00Z"))),
            row(1, "assistant", blocks = arrayOf(setAlarm("2026-05-22T08:35:00Z"))),
        )
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null)).edits.single()
        assertEquals("Moved to ${localHhMm("2026-05-22T08:35:00Z")}", edit.summary)
        assertTrue(edit.changedAlarm)
    }

    @Test
    fun set_alarm_edit_reads_set_when_the_plan_had_no_alarm() {
        val rows = listOf(
            row(0, "assistant", blocks = arrayOf(doNothing("nothing to do yet"))),
            row(1, "assistant", blocks = arrayOf(setAlarm("2026-05-22T08:35:00Z"))),
        )
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null)).edits.single()
        assertEquals("Set ${localHhMm("2026-05-22T08:35:00Z")}", edit.summary)
    }

    @Test
    fun streaming_partial_is_the_last_edit() {
        val rows = listOf(row(0, "assistant", blocks = arrayOf(setAlarm("2026-05-22T06:40:00Z"))))
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 1, blocks = listOf(doNothing("checking")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming))
        assertEquals(1, plan.edits.size)
        assertTrue(plan.edits.single().thread.isStreaming)
    }

    @Test
    fun streaming_partial_overrides_its_persisted_turn_without_duplicating() {
        val rows = listOf(
            row(0, "assistant", blocks = arrayOf(setAlarm("2026-05-22T06:40:00Z"))),
            row(1, "assistant", blocks = arrayOf(doNothing("partial"))),
        )
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 1, blocks = listOf(doNothing("live view")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(rows, streaming))
        assertEquals(1, plan.edits.size)
        assertTrue(plan.edits.single().thread.isStreaming)
        assertEquals("No change · live view", plan.edits.single().summary)
    }

    @Test
    fun streaming_first_plan_has_no_edits() {
        val streaming = StreamingTurn(sessionId = "s", turnIndex = 0, blocks = listOf(ContentBlock.Thinking(thinking = "deciding")), startedAtMs = 0L)
        val plan = requireNotNull(AiPlanMapper.buildPlan(emptyList(), streaming))
        assertTrue(plan.plan.isStreaming)
        assertTrue(plan.edits.isEmpty())
    }

    @Test
    fun edit_time_label_formats_hh_mm() {
        val iso = "2026-05-22T08:35:00Z"
        val createdAt = Instant.parse(iso).toEpochMilliseconds()
        val rows = listOf(
            row(0, "assistant", blocks = arrayOf(setAlarm("2026-05-22T06:40:00Z"))),
            row(1, "assistant", createdAt = createdAt, blocks = arrayOf(doNothing("ok"))),
        )
        val edit = requireNotNull(AiPlanMapper.buildPlan(rows, null)).edits.single()
        assertEquals(localHhMm(iso), edit.timeLabel)
    }
}
