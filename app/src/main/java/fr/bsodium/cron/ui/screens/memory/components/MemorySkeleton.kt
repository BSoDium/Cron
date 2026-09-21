package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.textShimmer
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

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
            CategoryHeaderSkeleton(
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    top = if (sectionIndex == 0) 0.dp else Spacing.lg,
                    bottom = Spacing.xs
                )
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
            .textShimmer()
    )
}

@Composable
private fun MemoryEntrySkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(CronColors.elementSurface)
            .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.Start,
    ) {
        // Mimic CronTypography.timelineRowTitle height
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(20.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .textShimmer()
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(14.dp)
                    .clip(Radius.full)
                    .textShimmer()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MemorySkeletonPreview() {
    CronTheme {
        Box(modifier = Modifier.padding(Spacing.lg)) {
            MemorySkeleton()
        }
    }
}
