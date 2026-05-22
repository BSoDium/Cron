package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ToolDefinition

/**
 * Lookup of tool name → executor. Construct once per AI turn and pass into
 * [TurnRunner]. Adding a new tool means appending to the [tools] list.
 */
class ToolRegistry(private val tools: List<Tool>) {

    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    val definitions: List<ToolDefinition> = tools.map { it.definition }

    operator fun get(name: String): Tool? = byName[name]
}
