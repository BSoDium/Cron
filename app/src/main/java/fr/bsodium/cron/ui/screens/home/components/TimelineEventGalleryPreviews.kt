package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant

/** Exhaustive galleries over every [TriggerType] as a real [EventNode] — the exact scenario that
 *  produced Rounds 18-20's cap/interior contrast bugs (a `*Container` role or a pill fill blending
 *  into whichever track it sat on) was only ever caught one event at a time, live on-device. These
 *  render the full matrix — every trigger, both tracks, both cap and interior sizing — in one place,
 *  in both themes via `@PreviewLightDark`, so a future color/shape change can be screened here first.
 *
 *  Each row independently sets `isSegmentTop` to force cap/interior sizing (`TimelineNode`'s `atCap`
 *  is a pure function of one row's own four flags, not its neighbors — no real segment continuity is
 *  needed for this). The awake and asleep examples each get their own [TimelineTrackRegistry] so
 *  `TimelineTrackOverlay`'s sleep-pill derivation (which infers the pill's open/close points from
 *  asleep-state transitions within *one* segment) sees a clean, uniformly-asleep or uniformly-awake
 *  block instead of a spurious half-open pill at the seam between two hand-built groups. */
@PreviewLightDark
@Composable
internal fun EventCapGalleryPreview() {
    CronTheme {
        Column(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            EventGalleryTrackBlock(sectionLabel = "Cap anchors — awake track", isAsleep = false, atCap = true)
            EventGalleryTrackBlock(sectionLabel = "Cap anchors — sleep track", isAsleep = true, atCap = true)
        }
    }
}

@PreviewLightDark
@Composable
internal fun EventInteriorGalleryPreview() {
    CronTheme {
        Column(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            EventGalleryTrackBlock(sectionLabel = "Interior pills — awake track", isAsleep = false, atCap = false)
            EventGalleryTrackBlock(sectionLabel = "Interior pills — sleep track", isAsleep = true, atCap = false)
        }
    }
}

@Composable
internal fun EventGalleryTrackBlock(sectionLabel: String, isAsleep: Boolean, atCap: Boolean) {
    val registry = rememberTimelineTrackRegistry()
    val listState = rememberLazyListState()
    GallerySectionLabel(sectionLabel)
    Box {
        TimelineTrackOverlay(registry = registry, listState = listState)
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            TriggerType.entries.forEach { trigger ->
                EventNode(
                    item = TimelineItem.Event(
                        timestamp = Instant.fromEpochMilliseconds(0L),
                        trigger = trigger,
                        label = trigger.name,
                        detail = null,
                    ),
                    registry = registry,
                    isSegmentTop = atCap,
                    isSegmentBottom = false,
                    isAsleepAbove = isAsleep,
                    isAsleepBelow = isAsleep,
                )
            }
        }
    }
}

@Composable
internal fun GallerySectionLabel(text: String) {
    Text(
        text = text,
        style = CronTypography.labelMonoSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs, start = NODE_GUTTER + Spacing.md),
    )
}
