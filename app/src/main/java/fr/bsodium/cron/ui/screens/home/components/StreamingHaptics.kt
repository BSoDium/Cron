package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.delay

// Feel-tuned spread drain: tick once per interval while there's any un-ticked text, advancing by up to
// CHARS_PER_TICK each tick — so a burst stretches into a steady rhythm that bridges the gap to the next
// burst instead of machine-gunning then idling. MAX_BACKLOG_CHARS caps how far ticks may trail the text.
private const val TICK_INTERVAL_MS = 50L
private const val CHARS_PER_TICK = 6
private const val MAX_BACKLOG_CHARS = 120

/**
 * Fires subtle, rhythmic haptic ticks as the active streaming turn grows. UI-less. Sourced from
 * [StreamingTurnStore] directly — the raw per-delta stream — rather than the rendered thread, so the
 * cadence isn't gated by markdown re-parse / recomposition batching. Gated by the [enabled] preference.
 */
@Composable
fun StreamingHaptics(enabled: Boolean) {
    val haptics = rememberCronHaptics(enabled = enabled)
    val active by StreamingTurnStore.active.collectAsState()
    val streaming = active != null
    // Live char count, read inside the pump without restarting it as the text grows.
    val charCount = rememberUpdatedState(active?.streamedLength() ?: 0)

    LaunchedEffect(streaming, enabled) {
        if (!streaming || !enabled) return@LaunchedEffect
        // Start abreast of whatever's already on screen (prior round-trips) so we don't machine-gun it.
        var ticked = charCount.value
        while (true) {
            delay(TICK_INTERVAL_MS)
            val now = charCount.value
            if (now < ticked) ticked = now // a retry dropped the half-streamed tail — resync down
            if (now - ticked > MAX_BACKLOG_CHARS) ticked = now - MAX_BACKLOG_CHARS // don't trail the text
            if (now > ticked) {
                ticked = (ticked + CHARS_PER_TICK).coerceAtMost(now)
                haptics.tick()
            }
        }
    }
}

/** Raw streamed character count of the in-flight blocks (text + thinking; tool blocks don't grow text). */
private fun StreamingTurn.streamedLength(): Int = blocks.sumOf { block ->
    when (block) {
        is ContentBlock.Text -> block.text.length
        is ContentBlock.Thinking -> block.thinking.length
        is ContentBlock.ToolUse -> 0
        is ContentBlock.ToolResult -> 0
    }
}
