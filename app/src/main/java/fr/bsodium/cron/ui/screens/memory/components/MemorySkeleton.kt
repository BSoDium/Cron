package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.skeletonPulse
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

/** Approximates [MemoryEntryRow]'s single-line content height (title + chip + vertical padding). */
private val entryCardHeight = 72.dp

/**
 * A skeleton loader for the Memory screen, mimicking the categorized list of [MemoryEntryRow] items.
 */
@Composable
internal fun MemorySkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(2) { sectionIndex ->
            // Must match SectionHeader's (MemoryScreen.kt) top padding exactly, first section included.
            CategoryHeaderSkeleton(
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs)
            )
            repeat(if (sectionIndex == 0) 3 else 2) {
                MemoryEntrySkeleton()
            }
        }
    }
}

@Composable
private fun CategoryHeaderSkeleton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(80.dp)
            .height(16.dp)
            .clip(Radius.full)
            .skeletonPulse()
    )
}

@Composable
private fun MemoryEntrySkeleton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(entryCardHeight)
            .clip(RoundedCornerShape(Radius.lg))
            .skeletonPulse()
    )
}

@Preview(showBackground = true)
@Composable
private fun MemorySkeletonPreview() {
    CronPreview {
        Box(modifier = Modifier.padding(Spacing.lg)) {
            MemorySkeleton()
        }
    }
}
