package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import kotlin.math.max

// A tool step costs one reveal unit so it pops in *in order*, right after the preceding text.
private const val TOOL_COST = 1
// Proportional catch-up: each frame advance the cursor by backlog/divisor (min MIN_STEP). Tracks
// generation speed but smooths bursts and bounds lag to ~divisor frames. Feel-tuned.
private const val REVEAL_CATCHUP_DIVISOR = 8
private const val MIN_REVEAL_STEP = 1

/** Total revealable units of a thread: process text (+ a unit per tool) plus the answer. */
internal fun AiThreadUi.revealableLength(): Int =
    process.sumOf {
        when (it) {
            is ProcessItem.Reasoning -> it.text.length
            is ProcessItem.Narration -> it.text.length
            is ProcessItem.Tool -> TOOL_COST
        }
    } + (response?.length ?: 0)

/** Order-preserving truncation of [thread] to a [revealed] unit budget — the typewriter's current frame. */
internal fun revealThread(thread: AiThreadUi, revealed: Int): AiThreadUi {
    var budget = revealed
    val revealedProcess = buildList {
        for (item in thread.process) {
            val text = when (item) {
                is ProcessItem.Reasoning -> item.text
                is ProcessItem.Narration -> item.text
                is ProcessItem.Tool -> null // atomic, costs TOOL_COST
            }
            if (text == null) {
                if (budget < TOOL_COST) return@buildList
                budget -= TOOL_COST
                add(item)
            } else {
                if (budget <= 0) return@buildList
                if (budget >= text.length) {
                    add(item)
                    budget -= text.length
                } else {
                    add(item.withText(text.take(budget)))
                    budget = 0
                    return@buildList
                }
            }
        }
    }
    val revealedResponse = thread.response?.takeIf { budget > 0 }?.take(budget)?.takeIf { it.isNotEmpty() }
    // Auto-close a half-typed **/` at the streaming edge so a partial bold/code span renders live
    // instead of flashing literal markers. The edge is the response if present, else the last text item.
    return if (revealedResponse != null) {
        thread.copy(process = revealedProcess, response = balanceInlineMarkers(revealedResponse))
    } else {
        thread.copy(process = revealedProcess.balanceLastText(), response = null)
    }
}

/** Balance the inline markers of the last Reasoning/Narration item — the typewriter's streaming edge. */
private fun List<ProcessItem>.balanceLastText(): List<ProcessItem> {
    val idx = indexOfLast { it is ProcessItem.Reasoning || it is ProcessItem.Narration }
    if (idx < 0) return this
    val balanced = when (val item = this[idx]) {
        is ProcessItem.Reasoning -> item.copy(text = balanceInlineMarkers(item.text))
        is ProcessItem.Narration -> item.copy(text = balanceInlineMarkers(item.text))
        is ProcessItem.Tool -> return this
    }
    return toMutableList().also { it[idx] = balanced }
}

/**
 * Closes a dangling inline-markdown span at the reveal edge so the renderer never shows a half-typed
 * `**`/`` ` `` as literal characters: an unclosed inline code gets a closing backtick; a trailing lone
 * `*` (a half-typed bold closer) is dropped, then an unmatched `**` gets closed. Idempotent on complete,
 * balanced text. Covers the dominant bold + inline-code cases; rarer `_`/`***`/`*`-in-math is tolerated.
 */
internal fun balanceInlineMarkers(text: String): String {
    var t = text
    if (t.count { it == '`' } % 2 == 1) t += "`"
    if (t.takeLastWhile { it == '*' }.length % 2 == 1) t = t.dropLast(1)
    if (BOLD_TOKEN.findAll(t).count() % 2 == 1) t += "**"
    return t
}

private val BOLD_TOKEN = Regex("\\*\\*")

/** Replace a text process item's text (Reasoning/Narration); no-op for Tool. */
private fun ProcessItem.withText(text: String): ProcessItem = when (this) {
    is ProcessItem.Reasoning -> copy(text = text)
    is ProcessItem.Narration -> copy(text = text)
    is ProcessItem.Tool -> this
}

/**
 * Reveals a streaming [thread] progressively (typewriter), decoupling the display from the chunky,
 * network-paced source. Settled threads (and loaded past sessions) return whole — no typewriter on load.
 */
@Composable
fun rememberRevealedThread(thread: AiThreadUi?): AiThreadUi? {
    if (thread == null || !thread.isStreaming) return thread

    val target = rememberUpdatedState(thread.revealableLength())
    var revealed by remember(thread.turnIndex) { mutableIntStateOf(0) }
    LaunchedEffect(thread.turnIndex) {
        while (true) {
            withFrameMillis { }
            val t = target.value
            if (revealed < t) {
                revealed = (revealed + max(MIN_REVEAL_STEP, (t - revealed) / REVEAL_CATCHUP_DIVISOR)).coerceAtMost(t)
            }
        }
    }
    return revealThread(thread, revealed)
}
