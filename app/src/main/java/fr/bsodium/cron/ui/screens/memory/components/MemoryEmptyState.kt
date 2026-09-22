package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.CronIllustratedMessage
import fr.bsodium.cron.ui.components.CronIllustrationType
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Spacing

private val IllustrationSize = 200.dp

// This Box's own bottom edge already excludes Spacing.navBarClearance (reserved for the floating
// nav pill below), so bias 0 still reads top-heavy: the eye counts that reserved strip as part of
// the gap under the subtitle. This bias cancels it out — measured pixel-equal top/bottom margins
// at the default (no-permission-banner) empty state.
private val VerticalBias = BiasAlignment(0f, 0.28f)

/**
 * Empty state for the Memory screen when no entries have been created yet.
 */
@Composable
internal fun MemoryEmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl),
        contentAlignment = VerticalBias,
    ) {
        CronIllustratedMessage(
            type = CronIllustrationType.Flowers1,
            title = "Your memories are empty",
            subtitle = "Tell Cron something to remember and it will appear here.",
            illustrationSize = IllustrationSize,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoryEmptyStatePreview() {
    CronPreview {
        MemoryEmptyState()
    }
}
