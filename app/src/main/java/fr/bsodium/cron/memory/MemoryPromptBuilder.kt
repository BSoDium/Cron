package fr.bsodium.cron.memory

/** Builds the user-turn prompt for a memory-mutation turn. Pure string construction over the
 *  current memory list and the user's typed instruction, so it's testable without Android. */
object MemoryPromptBuilder {

    fun buildMutationPrompt(memories: List<MemoryEntry>, instruction: String): String = buildString {
        appendLine("## Current memory")
        if (memories.isEmpty()) {
            appendLine("(empty)")
        } else {
            memories.forEach { entry ->
                val category = entry.category?.let { " [$it]" }.orEmpty()
                appendLine("${entry.id}: ${entry.text}$category")
            }
        }
        appendLine()
        appendLine("## Instruction")
        appendLine(instruction)
    }
}
