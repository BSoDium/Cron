package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing

@PreviewLightDark
@Composable
private fun TimelineNodeAnchorsPreview() {
    CronTheme {
        val registry = rememberTimelineTrackRegistry()
        Box(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            TimelineTrackOverlay(registry = registry)
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                TimelineNode(
                    id = "loader",
                    registry = registry,
                    anchor = TimelineAnchor.Loader,
                    isSegmentTop = true,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    title = { Text("Replanning...", style = MaterialTheme.typography.bodyMedium) },
                    status = { Text("07:16", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                TimelineNode(
                    id = "snooze",
                    registry = registry,
                    // Negative valence → Triangle silhouette, interior (smaller) size.
                    anchor = TimelineAnchor.Icon(MaterialSymbol.Snooze, valence = TimelineValence.Negative),
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    title = { Text("Alarm snoozed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    status = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            MonoPill("9 min")
                            Text("07:15", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                TimelineNode(
                    id = "gotup",
                    registry = registry,
                    // Positive valence → Flower silhouette, interior size.
                    anchor = TimelineAnchor.Icon(MaterialSymbol.DirectionsWalk, valence = TimelineValence.Positive),
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    title = { Text("You got up", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    status = { Text("07:50", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                TimelineNode(
                    id = "planned",
                    registry = registry,
                    // Interior (isSegmentTop/Bottom both false) — TimelineNode's own normalization discards this tint/containerColor and substitutes the track-crossed pill treatment regardless, same as real production AiRunNode/EventNode rows.
                    anchor = TimelineAnchor.Icon(symbol = MaterialSymbol.Schedule),
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                    title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                    status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                // The onset row itself: awake above, asleep below — the sleep pill opens its own rounded cap right at this anchor.
                TimelineNode(
                    id = "onset",
                    registry = registry,
                    anchor = TimelineAnchor.Plain,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = true,
                    title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    status = { Text("23:40", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                TimelineNode(
                    id = "evening",
                    registry = registry,
                    anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                    isSegmentTop = false,
                    isSegmentBottom = true,
                    isAsleepAbove = true,
                    isAsleepBelow = true,
                    title = { Text("Evening plan", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    status = { Text("22:30", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TimelineNodeWithContentPreview() {
    CronTheme {
        val registry = rememberTimelineTrackRegistry()
        Box(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            TimelineTrackOverlay(registry = registry)
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                TimelineNode(
                    id = "planned",
                    registry = registry,
                    // Cap anchor (isSegmentTop = true) — mirrors AiRunNode's real solid-role treatment for a cap icon (primary/onPrimary), not the washed-out primaryContainer/onPrimaryContainer a *Container role reads as against the track (docs/color-roles.md).
                    anchor = TimelineAnchor.Icon(
                        symbol = MaterialSymbol.Schedule,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    isSegmentTop = true,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                    title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                    status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    content = {
                        Text(
                            "Set alarm for 07:45. You have an 08:30 standup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                TimelineNode(
                    id = "asleep",
                    registry = registry,
                    anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                    isSegmentTop = false,
                    isSegmentBottom = true,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    status = { Text("23:40", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}
