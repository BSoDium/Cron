package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.delay

// Feel-tuned spread drain for the answer: tick once per interval while there's any un-ticked text,
// advancing by up to CHARS_PER_TICK each tick — so a burst stretches into a steady rhythm that bridges
// the gap to the next burst instead of machine-gunning then idling. MAX_BACKLOG_CHARS caps the trail.
private const val TICK_INTERVAL_MS = 50L
private const val CHARS_PER_TICK = 6
private const val MAX_BACKLOG_CHARS = 120

/**
 * Subtle haptic feedback while a turn streams. UI-less. Reads the **revealed** [thread] (the typewriter's
 * current frame), so the rhythm matches the on-screen animation rather than the raw network cadence:
 *
 *  - **Thinking process** → one gentle tick each time a new timeline item appears (a reasoning step or
 *    tool call), never per token.
 *  - **Answer** → a per-token spread drain over the SUMMARY-gated response, so it fires only once the
 *    model is writing its answer — automatically silent during thinking.
 *
 * Gated by the [enabled] preference.
 */
@Composable
fun StreamingHaptics(thread: AiThreadUi?, enabled: Boolean) {
    val haptics = rememberCronHaptics(enabled = enabled)
    val streaming = thread?.isStreaming == true
    // Read inside the pump without restarting it as the revealed thread grows.
    val bulletCount = rememberUpdatedState(thread?.process?.size ?: 0)
    val answerChars = rememberUpdatedState(thread?.response?.length ?: 0)

    LaunchedEffect(streaming, enabled) {
        if (!streaming || !enabled) return@LaunchedEffect
        // Start abreast of what's already on screen (prior round-trips) so we don't machine-gun it.
        var lastBullets = bulletCount.value
        var ticked = answerChars.value
        while (true) {
            delay(TICK_INTERVAL_MS)

            // Thinking: one tick per new timeline item; resync silently if it shrinks (e.g. the answer
            // block leaves the process once its SUMMARY line is detected).
            val bullets = bulletCount.value
            when {
                bullets > lastBullets -> { lastBullets = bullets; haptics.tick() }
                bullets < lastBullets -> lastBullets = bullets
            }

            // Answer: per-token spread drain over the revealed response.
            val now = answerChars.value
            if (now < ticked) ticked = now
            if (now - ticked > MAX_BACKLOG_CHARS) ticked = now - MAX_BACKLOG_CHARS
            if (now > ticked) {
                ticked = (ticked + CHARS_PER_TICK).coerceAtMost(now)
                haptics.tick()
            }
        }
    }
}
