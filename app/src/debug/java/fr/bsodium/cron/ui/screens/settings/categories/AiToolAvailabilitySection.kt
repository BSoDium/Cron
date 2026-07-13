package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.bsodium.cron.ai.tools.aiToolAvailability
import fr.bsodium.cron.ui.theme.Spacing

/**
 * DEBUG-ONLY. Lists every AI tool `AiTurnWorker.buildToolRegistry` can register and whether it's
 * currently available — e.g. the location tools (geocode/commute) are silently skipped rather than
 * erroring when `GOOGLE_ROUTES_API_KEY` isn't configured, which otherwise reads as the model simply
 * never calling them for no visible reason.
 */
@Composable
internal fun AiToolAvailabilitySection() {
    Column {
        Text(
            text = "AI tools",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "What the model can call this turn, and why not if it can't",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (status in aiToolAvailability()) {
            Column(modifier = Modifier.padding(top = Spacing.sm)) {
                Text(
                    text = if (status.available) "${status.name} — available" else "${status.name} — unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.available) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
                )
                status.reason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
