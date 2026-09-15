package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private val APPEND_SPINNER_SIZE = 20.dp
private val APPEND_SPINNER_STROKE = 2.dp

/** Home timeline's trailing row while [androidx.paging.LoadState.Loading] is fetching the next page of
 *  history — replaces the old "View full history" dead-end button (#187/#231): scrolling further now
 *  loads more in place instead of punting to a separate screen. */
@Composable
internal fun AppendLoadingRow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(APPEND_SPINNER_SIZE),
            strokeWidth = APPEND_SPINNER_STROKE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Home timeline's trailing row while [androidx.paging.LoadState.Error] has the next page of history
 *  failed to load — same shape as the button it replaced, so it reads as a natural continuation of the
 *  timeline rather than a new kind of error surface. */
@Composable
internal fun AppendErrorRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onRetry,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        shape = Radius.full,
    ) {
        Text("Couldn't load more — Retry")
    }
}

@Preview(showBackground = true)
@Composable
private fun AppendLoadingRowPreview() {
    CronTheme { AppendLoadingRow() }
}

@Preview(showBackground = true)
@Composable
private fun AppendErrorRowPreview() {
    CronTheme { AppendErrorRow(onRetry = {}) }
}
