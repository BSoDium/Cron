package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import kotlinx.coroutines.delay

// Feel-tuned. We drain newly-streamed characters into ticks at a steady cadence so the rhythm is
// decoupled from lumpy SSE delta arrival: ~one tick per CHARS_PER_TICK characters, paced no faster
// than TICK_INTERVAL_MS, and never trailing the visible text by more than MAX_BACKLOG_CHARS.
private const val TICK_INTERVAL_MS = 55L
private const val CHARS_PER_TICK = 10
private const val MAX_BACKLOG_CHARS = 40

/**
 * Fires subtle, rhythmic haptic ticks as the streamed [thread] grows. UI-less — it only runs the
 * drain pump. Ticks are time-windowed (not per-character), gated by the [enabled] preference, and
 * fire only while the thread is actively streaming (silent during thinking pauses).
 */
@Composable
fun StreamingHaptics(thread: AiThreadUi?, enabled: Boolean) {
    val haptics = rememberCronHaptics(enabled = enabled)
    val streaming = thread?.isStreaming == true
    // Live char count, read inside the pump without restarting it as the text grows.
    val charCount = rememberUpdatedState(if (streaming) thread.streamedLength() else 0)

    LaunchedEffect(streaming, enabled) {
        if (!streaming || !enabled) return@LaunchedEffect
        // Start abreast of whatever's already on screen (prior round-trips) so we don't machine-gun it.
        var ticked = charCount.value
        while (true) {
            delay(TICK_INTERVAL_MS)
            val now = charCount.value
            when {
                now < ticked -> ticked = now // a retry dropped the half-streamed tail — resync down
                now - ticked > MAX_BACKLOG_CHARS -> ticked = now - MAX_BACKLOG_CHARS // don't trail the text
            }
            if (now - ticked >= CHARS_PER_TICK) {
                ticked += CHARS_PER_TICK
                haptics.tick()
            }
        }
    }
}

/** Total visible character count of the thinking process + response — what grows as tokens stream. */
private fun AiThreadUi.streamedLength(): Int {
    val processChars = process.sumOf { item ->
        when (item) {
            is ProcessItem.Reasoning -> item.text.length
            is ProcessItem.Narration -> item.text.length
            is ProcessItem.Tool -> 0
        }
    }
    return processChars + (response?.length ?: 0)
}
