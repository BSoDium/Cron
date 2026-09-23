package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

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
private fun AppendErrorRowPreview() {
    CronPreview { AppendErrorRow(onRetry = {}) }
}
