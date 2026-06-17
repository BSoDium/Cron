package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.calendar.DEFAULT_RSVP_STATUSES
import fr.bsodium.cron.calendar.RsvpStatus
import fr.bsodium.cron.ui.screens.settings.components.CheckboxRow
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing

private fun RsvpStatus.subtitle(): String = when (this) {
    RsvpStatus.Accepted -> "Events you said yes to"
    RsvpStatus.NotResponded -> "Events you haven't responded to"
    RsvpStatus.Tentative -> "Events marked as maybe"
    RsvpStatus.Declined -> "Events you said no to"
}

private fun RsvpStatus.icon(): MaterialSymbol = when (this) {
    RsvpStatus.Accepted -> MaterialSymbol.Check
    RsvpStatus.NotResponded -> MaterialSymbol.Schedule
    RsvpStatus.Tentative -> MaterialSymbol.EventUpcoming
    RsvpStatus.Declined -> MaterialSymbol.Close
}

private const val CALENDAR_SUBTITLE =
    "The planner only sees events whose RSVP status is checked below. Declined events are hidden by default."

@Composable
fun CalendarSettingsScreen(
    allowedStatuses: Set<RsvpStatus>,
    onAllowedStatuses: (Set<RsvpStatus>) -> Unit,
    onBack: () -> Unit,
    hapticsEnabled: Boolean = true,
) {
    SettingsDetailScaffold(title = "Calendar", subtitle = CALENDAR_SUBTITLE, onBack = onBack, verticalSpacing = Spacing.sm) {
        RsvpStatus.entries.forEach { status ->
            val alwaysOn = status == RsvpStatus.Accepted
            CheckboxRow(
                icon = status.icon(),
                title = status.label,
                subtitle = status.subtitle(),
                checked = alwaysOn || status in allowedStatuses,
                onCheckedChange = { checked ->
                    if (alwaysOn) return@CheckboxRow
                    val next = if (checked) allowedStatuses + status else allowedStatuses - status
                    if (next.isNotEmpty()) onAllowedStatuses(next)
                },
                hapticsEnabled = hapticsEnabled,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892, fontScale = 1.0f)
@Composable
private fun CalendarSettingsScreenPreview() {
    CronTheme {
        CalendarSettingsScreen(
            allowedStatuses = DEFAULT_RSVP_STATUSES,
            onAllowedStatuses = {},
            onBack = {},
        )
    }
}
