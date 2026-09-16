package fr.bsodium.cron.memory

import fr.bsodium.cron.testutil.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptBuilderTest {

    @Test
    fun empty_memory_says_so_explicitly() {
        val prompt = MemoryPromptBuilder.buildMutationPrompt(emptyList(), "wake me earlier on Fridays")
        assertTrue(prompt.contains("(empty)"))
        assertTrue(prompt.contains("## Instruction"))
        assertTrue(prompt.contains("wake me earlier on Fridays"))
    }

    @Test
    fun lists_each_entry_with_its_id_and_category() {
        val prompt = MemoryPromptBuilder.buildMutationPrompt(
            memories = listOf(
                Fixtures.memoryEntry(id = 1, text = "prefers earlier wake-ups on gym days", category = "schedule"),
                Fixtures.memoryEntry(id = 2, text = "commutes by bike", category = null),
            ),
            instruction = "forget the gym one",
        )
        assertTrue(prompt.contains("1: prefers earlier wake-ups on gym days [schedule]"))
        assertTrue(prompt.contains("2: commutes by bike"))
        assertFalse(prompt.contains("2: commutes by bike ["))
    }
}
