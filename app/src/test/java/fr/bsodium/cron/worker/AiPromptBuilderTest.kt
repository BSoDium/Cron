package fr.bsodium.cron.worker

import fr.bsodium.cron.session.model.CommuteMode
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.testutil.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptBuilderTest {

    @Test
    fun evening_plan_includes_session_context_and_instructions() {
        val prompt = AiPromptBuilder.build(
            session = Fixtures.session(),
            isEveningPlan = true,
            instructions = "wake me gently",
        )
        assertTrue(prompt.contains("## Session context"))
        assertTrue(prompt.contains("Hard latest"))
        assertTrue(prompt.contains("## User instructions"))
        assertTrue(prompt.contains("wake me gently"))
        assertTrue(prompt.contains("Plan tomorrow's alarm."))
    }

    @Test
    fun evening_plan_without_location_notes_it_is_unavailable_and_omits_instructions() {
        val prompt = AiPromptBuilder.build(Fixtures.session(), isEveningPlan = true, instructions = null)
        assertTrue(prompt.contains("## Location"))
        assertTrue(prompt.contains("unavailable"))
        assertFalse(prompt.contains("## User instructions"))
    }

    @Test
    fun overnight_replan_lists_the_event_log_and_current_instruction() {
        val session = Fixtures.session(
            events = listOf(
                Fixtures.hcEvent(
                    SleepStage.Light,
                    Fixtures.at("2026-05-22T01:00:00Z"),
                    Fixtures.at("2026-05-22T02:00:00Z"),
                ),
            ),
        )
        val prompt = AiPromptBuilder.build(session, isEveningPlan = false, instructions = null)
        assertTrue(prompt.contains("## Day plan"))
        assertTrue(prompt.contains("## Current instruction"))
        assertTrue(prompt.contains("## Event log"))
        assertTrue(prompt.contains("HcStageUpdate"))
        assertTrue(prompt.contains("Decide what the alarm system should do."))
    }

    @Test
    fun overnight_replan_includes_the_captured_location_so_origin_is_never_guessed() {
        val session = Fixtures.session(events = listOf(Fixtures.eveningEvent(lat = 46.624, lng = 14.308)))
        val prompt = AiPromptBuilder.build(session, isEveningPlan = false, instructions = null)
        assertTrue(prompt.contains("## Current location"))
        assertTrue(prompt.contains("46.624"))
        assertTrue(prompt.contains("14.308"))
    }

    @Test
    fun both_messages_list_allowed_commute_modes_defaulting_to_all() {
        val evening = AiPromptBuilder.build(Fixtures.session(), isEveningPlan = true, instructions = null)
        val replan = AiPromptBuilder.build(Fixtures.session(), isEveningPlan = false, instructions = null)
        assertTrue(evening.contains("Allowed commute modes"))
        assertTrue(replan.contains("Allowed commute modes"))
        assertTrue(evening.contains("DRIVE"))
        assertTrue(evening.contains("TRANSIT"))
    }

    @Test
    fun excluding_a_mode_omits_it_from_the_allowed_list() {
        val plan = Fixtures.dayPlan().copy(
            allowedCommuteModes = setOf(CommuteMode.Transit, CommuteMode.Bike, CommuteMode.Walk),
        )
        val prompt = AiPromptBuilder.build(Fixtures.session(plan = plan), isEveningPlan = true, instructions = null)
        assertTrue(prompt.contains("Allowed commute modes"))
        assertTrue(prompt.contains("TRANSIT"))
        assertFalse(prompt.contains("DRIVE"))
    }
}
