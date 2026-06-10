package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

private val ARROW_SIZE = 16.dp

/**
 * Footer for an OLDER (non-latest) plan tab, where the live thinking shape is hidden — so the bottom
 * doesn't read as empty. A compact "See more"-style pill: how long ago the turn ran + a tap back to the
 * latest. Negative offset aligns the label with the response column and tucks it tight under the text.
 */
@Composable
internal fun OldPlanFooter(
    ranAtEpochMs: Long?,
    onJumpToLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ago = ranAtEpochMs?.let { rememberRelativeAgo(it) }
    val label = if (ago != null) "Ran $ago · Jump to latest" else "Jump to latest"
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .offset(x = -Spacing.sm, y = -Spacing.sm)
            .minimumInteractiveComponentSize()
            .clip(Radius.full)
            .clickable { onJumpToLatest() }
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
            Symbol(symbol = MaterialSymbol.ArrowForward, contentDescription = null, tint = color, size = ARROW_SIZE)
        }
    }
}

/** A coarse "X ago" string from [epochMs], re-ticked each minute so it stays current while viewed. */
@Composable
private fun rememberRelativeAgo(epochMs: Long): String {
    val now by produceState(initialValue = Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(60_000L)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
    val minutes = ((now - epochMs) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "${h}h ago" else "${h}h ${m}m ago"
        }
        else -> "${minutes / (60 * 24)}d ago"
    }
}

@Preview(showBackground = true)
@Composable
private fun OldPlanFooterPreview() {
    CronTheme {
        OldPlanFooter(ranAtEpochMs = Clock.System.now().toEpochMilliseconds() - 134 * 60_000L, onJumpToLatest = {})
    }
}
