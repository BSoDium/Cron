package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.session.model.CommuteMode
import fr.bsodium.cron.ui.screens.settings.components.CheckboxRow
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol

private fun CommuteMode.subtitle(): String = when (this) {
    CommuteMode.Drive -> "Estimate by car"
    CommuteMode.Transit -> "Estimate by bus, tram, or train"
    CommuteMode.Bike -> "Estimate by bicycle"
    CommuteMode.Walk -> "Estimate on foot"
}

private fun CommuteMode.icon(): MaterialSymbol = when (this) {
    CommuteMode.Drive -> MaterialSymbol.DirectionsCar
    CommuteMode.Transit -> MaterialSymbol.DirectionsTransit
    CommuteMode.Bike -> MaterialSymbol.DirectionsBike
    CommuteMode.Walk -> MaterialSymbol.DirectionsWalk
}

private const val COMMUTE_SUBTITLE =
    "Unchecking a mode blocks the planner from using it, regardless of any saved location data."

/** Which transport modes the planner may use. It still auto-picks the fastest reasonable one — these
 *  toggles just let the user exclude modes they don't use (e.g. no car). */
@Composable
fun CommuteSettingsScreen(
    allowedModes: Set<CommuteMode>,
    onAllowedModes: (Set<CommuteMode>) -> Unit,
    onBack: () -> Unit,
    hapticsEnabled: Boolean = true,
) {
    SettingsDetailScaffold(title = "Commute", subtitle = COMMUTE_SUBTITLE, onBack = onBack) {
        CommuteMode.entries.forEach { mode ->
            CheckboxRow(
                icon = mode.icon(),
                title = mode.label,
                subtitle = mode.subtitle(),
                checked = mode in allowedModes,
                onCheckedChange = { checked ->
                    val next = if (checked) allowedModes + mode else allowedModes - mode
                    // Keep at least one mode on — the planner needs something to estimate with.
                    if (next.isNotEmpty()) onAllowedModes(next)
                },
                hapticsEnabled = hapticsEnabled,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CommuteSettingsScreenPreview() {
    CronTheme {
        CommuteSettingsScreen(
            allowedModes = setOf(CommuteMode.Transit, CommuteMode.Bike, CommuteMode.Walk),
            onAllowedModes = {},
            onBack = {},
        )
    }
}
