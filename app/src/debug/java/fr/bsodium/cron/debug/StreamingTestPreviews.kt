package fr.bsodium.cron.debug

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.home.AiThreadMapper
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ai.wire.ContentBlock
import kotlinx.serialization.json.JsonObject

/**
 * DEBUG-ONLY previews for iterating on the streaming render (flicker, trailing fade, classification)
 * in Android Studio — zero device, zero API spend.
 *
 * Run [StreamingInteractivePreview] in the preview pane's *interactive* mode to watch tokens stream.
 * [StreamingMidFramePreview] is a static frame for quick visual checks (the fade snaps in since
 * isStreaming = true). Haptics can't fire in a preview — use StreamTestActivity on a device for those.
 */
@Preview(showBackground = true, name = "Streaming — interactive (run interactive mode)")
@Composable
private fun StreamingInteractivePreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var thread by remember { mutableStateOf<AiThreadUi?>(null) }
            LaunchedEffect(Unit) { StreamingSimulator.run { thread = it } }
            thread?.let { AiThinkingThread(it, isRunning = true, modifier = Modifier.padding(Spacing.xl)) }
        }
    }
}

@Preview(showBackground = true, name = "Streaming — mid-answer frame (fade)")
@Composable
private fun StreamingMidFramePreview() {
    val thread = AiThreadMapper.buildFromBlocks(
        turnIndex = 0,
        blocks = listOf(
            ContentBlock.Thinking(thinking = "Reading the calendar and finding the first anchor."),
            ContentBlock.Text("I can see tomorrow's picture clearly — a packed morning."),
            ContentBlock.ToolUse(id = "sim", name = "read_calendar", input = JsonObject(emptyMap())),
            ContentBlock.ToolResult(tool_use_id = "sim", content = """{"events":[0,0,0]}""", is_error = false),
            ContentBlock.Text("SUMMARY: Set a 6:40 alarm\n\nSet a **6:40** alarm so you make your 9:00 stand-"),
        ),
    )
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiThinkingThread(thread, isRunning = true, modifier = Modifier.padding(Spacing.xl))
        }
    }
}
