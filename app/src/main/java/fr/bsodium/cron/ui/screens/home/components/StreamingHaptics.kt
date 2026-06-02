package fr.bsodium.cron.ui.screens.home.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem

/** Minimum gap between streaming ticks — chunks arrive faster than this, so we time-window them. */
private const val MIN_TICK_INTERVAL_MS = 45L

/**
 * Fires a subtle haptic tick as the streamed [thread] grows. UI-less: it only runs the throttled
 * effect. Ticks are time-windowed (not per-character), gated by the [enabled] user preference, and
 * only fire while the thread is actively streaming.
 */
@Composable
fun StreamingHaptics(thread: AiThreadUi?, enabled: Boolean) {
    val haptics = rememberCronHaptics(enabled = enabled)
    val streaming = thread?.isStreaming == true
    val charCount = if (streaming) thread.streamedLength() else 0

    val lastCount = remember { mutableIntStateOf(0) }
    val lastTickAt = remember { mutableLongStateOf(0L) }
    LaunchedEffect(charCount, streaming) {
        if (!streaming) {
            lastCount.intValue = 0
            return@LaunchedEffect
        }
        val grew = charCount > lastCount.intValue
        lastCount.intValue = charCount
        if (!grew) return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        if (now - lastTickAt.longValue >= MIN_TICK_INTERVAL_MS) {
            lastTickAt.longValue = now
            haptics.tick()
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
