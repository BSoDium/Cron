package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.CronIllustratedMessage
import fr.bsodium.cron.ui.components.CronIllustrationType
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

private val IllustrationSize = 200.dp
private val TextMaxWidth = 240.dp

/**
 * Empty state for the Memory screen when no entries have been created yet.
 */
@Composable
internal fun MemoryEmptyState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CronIllustratedMessage(
            type = CronIllustrationType.Flowers1,
            title = "Your memories are empty",
            subtitle = "Tell Cron something to remember and it will appear here.",
            illustrationSize = IllustrationSize,
            textMaxWidth = TextMaxWidth,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoryEmptyStatePreview() {
    CronTheme {
        MemoryEmptyState()
    }
}
