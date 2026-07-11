package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.label
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

/** Tab glyph for a run: the clock for the nightly base, play for a user-started base, else its trigger icon. */
internal fun runSymbol(kind: RunKind): MaterialSymbol = when (kind) {
    RunKind.ScheduledBase -> MaterialSymbol.Schedule
    RunKind.ManualBase -> MaterialSymbol.PlayArrow
    is RunKind.Replan -> triggerSymbol(kind.trigger)
}

/** The Material Symbol for a replan trigger. `null` is the original scheduled plan; `EveningPlan` is a
 *  user-run manual replan. The exhaustive `when` makes a new [TriggerType] a compile error. */
internal fun triggerSymbol(trigger: TriggerType?): MaterialSymbol = when (trigger) {
    null -> MaterialSymbol.Schedule
    TriggerType.EveningPlan -> MaterialSymbol.Autoplay
    TriggerType.CalendarChange -> MaterialSymbol.EventUpcoming
    TriggerType.SleepOnset -> MaterialSymbol.Bedtime
    TriggerType.HcStageUpdate -> MaterialSymbol.VitalSigns
    TriggerType.MidSleepActivity -> MaterialSymbol.Vibration
    TriggerType.OutOfBedConfirmed -> MaterialSymbol.DirectionsWalk
    TriggerType.WakeWindowOpportunity -> MaterialSymbol.LightMode
    TriggerType.AlarmSnoozed -> MaterialSymbol.Snooze
    TriggerType.AlarmDismissed -> MaterialSymbol.AlarmOff
    TriggerType.HardLatestFired -> MaterialSymbol.NotificationImportant
}

/** Semantic color category for a timeline event — carries meaning instead of the flat neutral gray
 *  every row used to share (docs/expressive.md "apply varied accents"). AI runs use their own
 *  primary-based treatment in [AiRunNode], so this only covers non-AI [TriggerType] events. */
internal enum class TimelineAccent { Body, AlarmAction, Schedule }

/** Exhaustive so a new [TriggerType] forces a deliberate accent choice rather than falling through. */
internal fun TriggerType.timelineAccent(): TimelineAccent = when (this) {
    TriggerType.SleepOnset,
    TriggerType.OutOfBedConfirmed,
    TriggerType.MidSleepActivity,
    TriggerType.HcStageUpdate,
    -> TimelineAccent.Body
    TriggerType.AlarmSnoozed,
    TriggerType.AlarmDismissed,
    TriggerType.HardLatestFired,
    -> TimelineAccent.AlarmAction
    TriggerType.CalendarChange,
    TriggerType.WakeWindowOpportunity,
    TriggerType.EveningPlan,
    -> TimelineAccent.Schedule
}

/** Semantic silhouette dimension — orthogonal to [TimelineAccent] (hue). Positive events read as a
 *  soft `Flower`, negative ones as a sharp `Triangle`, neutral ones stay a plain circle (no shape
 *  change). Carries an at-a-glance "was this good or bad" read the color and icon alone don't. */
internal enum class TimelineValence { Positive, Negative, Neutral }

/** Exhaustive so a new [TriggerType] forces a deliberate valence choice rather than falling through. */
internal fun TriggerType.timelineValence(): TimelineValence = when (this) {
    TriggerType.OutOfBedConfirmed, TriggerType.WakeWindowOpportunity -> TimelineValence.Positive
    TriggerType.HardLatestFired, TriggerType.AlarmSnoozed -> TimelineValence.Negative
    TriggerType.SleepOnset, TriggerType.AlarmDismissed, TriggerType.CalendarChange,
    TriggerType.HcStageUpdate, TriggerType.MidSleepActivity, TriggerType.EveningPlan -> TimelineValence.Neutral
}

/** A replan inherits its trigger's valence (a safety-alarm replan reads as negative, same as the event
 *  would); a scheduled/manual base plan is neutral. Exhaustive over the sealed [RunKind]. */
internal fun RunKind.timelineValence(): TimelineValence = when (this) {
    RunKind.ScheduledBase, RunKind.ManualBase -> TimelineValence.Neutral
    is RunKind.Replan -> trigger?.timelineValence() ?: TimelineValence.Neutral
}

/** Track-aware — every cap anchor's fill matches whichever track it's on:
 *  [MaterialTheme.colorScheme.secondaryContainer] on the sleep track, [MaterialTheme.colorScheme.primary]
 *  on the awake one, with no exception for a "muted" tier either — every anchor on a given track is
 *  literally the same color, full stop, differentiated only by icon/shape. Both delegate to
 *  `SessionTimeline.kt`'s `trackAccentColor`/`trackOnAccentColor`, so a same-track collision is
 *  structurally impossible, unlike the per-accent/per-importance `*Container` pairing this replaced
 *  (which needed its own escape hatch per accent/track combination to avoid one). */
@Composable
internal fun TimelineAccent.capContainerColor(isAsleep: Boolean): Color = trackAccentColor(isAsleep)

/** [capContainerColor]'s paired foreground — always the matching `on*` role for whichever fill
 *  [capContainerColor] resolved to, so the pair can never drift apart. */
@Composable
internal fun TimelineAccent.capOnContainerColor(isAsleep: Boolean): Color = trackOnAccentColor(isAsleep)

@PreviewLightDark
@Composable
private fun TriggerIconsPreview() {
    // Real production kinds → the preview renders the exact icon/label pairs the app ships.
    val kinds: List<RunKind> = listOf(RunKind.ScheduledBase, RunKind.ManualBase) +
        TriggerType.entries.map { RunKind.Replan(it) }
    CronTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CronColors.pageBackground)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            kinds.forEach { kind ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Symbol(
                        symbol = runSymbol(kind),
                        contentDescription = null,
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
