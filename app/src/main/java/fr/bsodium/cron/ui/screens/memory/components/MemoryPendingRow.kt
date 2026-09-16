package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private val SPINNER_SIZE = 20.dp
private val SPINNER_STROKE = 2.dp

/** Stand-in for the entry the assistant is about to add/edit/delete — shown while a memory-mutation
 *  turn is in flight, in the row's own place (append position, since entries sort oldest-first) rather
 *  than a plain "Updating…" caption below the list. A silently-appearing entry once the turn finishes
 *  reads as dropped input on a slow connection; a spinner row in place from the moment the user hits
 *  send makes the in-flight state visible immediately. Same [CircularProgressIndicator] treatment as
 *  [fr.bsodium.cron.ui.screens.home.components.TimelineLoadStateRow]'s append spinner — plain and
 *  small rather than a full loading card, since this is a brief, one-row wait. */
@Composable
internal fun MemoryPendingRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(CronColors.elementSurface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(SPINNER_SIZE),
            strokeWidth = SPINNER_STROKE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Updating memory…",
            style = CronTypography.timelineRowTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoryPendingRowPreview() {
    CronTheme {
        MemoryPendingRow()
    }
}
