package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.AiTurnFailure
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import java.util.Locale

/** Dismissible error surface explaining why the latest AI turn didn't update the plan. */
@Composable
internal fun AiFailureBanner(
    failure: AiTurnFailure,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(Radius.lg),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, end = Spacing.xs)) {
            Text(
                text = failure.bannerMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onOpenSettings(); onDismiss() }) { Text("Open settings") }
                IconButton(onClick = onDismiss) {
                    Symbol(
                        symbol = MaterialSymbol.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

private fun AiTurnFailure.bannerMessage(): String = when (this) {
    is AiTurnFailure.BudgetExhausted -> String.format(
        Locale.US,
        "Daily AI budget reached (%,d / %,d tokens). Resets at midnight, or raise it in Settings.",
        used,
        limit,
    )
    AiTurnFailure.MissingApiKey -> "AI planning needs an Anthropic API key. Add one in Settings."
    is AiTurnFailure.Generic ->
        "Couldn't update your plan${reason?.let { " ($it)" }.orEmpty()}. Try again, or check Settings."
}

@Preview
@Composable
private fun AiFailureBannerPreview() {
    CronTheme {
        AiFailureBanner(
            failure = AiTurnFailure.BudgetExhausted(used = 80_802, limit = 250_000),
            onOpenSettings = {},
            onDismiss = {},
        )
    }
}
