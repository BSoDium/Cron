package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock

/** Executes a single tool_use block via [tools], turning the result (or any thrown/unknown-tool
 *  failure) into a tool_result block. Shared by [TurnRunner] and [MemoryTurnRunner] — the only piece
 *  of the tool-use loop that's genuinely identical between a session-scoped planning turn and a
 *  session-independent memory-mutation turn. */
internal suspend fun executeToolCall(tools: ToolRegistry, call: ContentBlock.ToolUse): ContentBlock.ToolResult {
    val tool = tools[call.name] ?: return ContentBlock.ToolResult(
        tool_use_id = call.id,
        content = toolErrorResult("unknown tool '${call.name}'").payload,
        is_error = true,
    )
    val result = runCatching { tool.execute(call.input) }.getOrElse { thrown ->
        return ContentBlock.ToolResult(
            tool_use_id = call.id,
            content = toolErrorResult(
                "tool '${call.name}' threw: ${thrown.message?.take(200) ?: thrown::class.simpleName}"
            ).payload,
            is_error = true,
        )
    }
    return ContentBlock.ToolResult(
        tool_use_id = call.id,
        content = result.payload,
        is_error = result.isError.takeIf { it },
    )
}
