package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.skeletonPulse
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private val GREETING_PREFIX_BAR_WIDTH = 90.dp
private val GREETING_PREFIX_BAR_HEIGHT = 16.dp
private val GREETING_NAME_BAR_WIDTH = 150.dp
private val GREETING_NAME_BAR_HEIGHT = 24.dp
private val GREETING_TOGGLE_WIDTH = 96.dp

/** Approximates [CollapsibleAlarmCard]'s expanded-state height — the real card's is intrinsic
 *  (measured live from its LCD clock content via `SubcomposeLayout`), so there's no fixed token to
 *  mirror exactly; this is a plausible resting height for the placeholder block. */
private val ALARM_CARD_SKELETON_HEIGHT = 160.dp

/** Placeholder row title widths, hand-varied so the stack doesn't read as one repeated block —
 *  mirrors the natural mix of short event labels and longer AI-run titles in the real timeline. */
private val TITLE_WIDTH_FRACTIONS = listOf(0.62f, 0.4f, 0.78f, 0.5f, 0.34f)

private val TITLE_BAR_HEIGHT = 14.dp
private val TIME_BAR_WIDTH = 34.dp
private val TIME_BAR_HEIGHT = 12.dp
private val ROW_VERTICAL_PADDING = Spacing.md

/** Placeholder rows stacked before the bottom fade takes over — enough to read as a real list under
 *  the alarm card without needing to fill (or scroll) the whole viewport. */
private const val ROW_COUNT = 6

/** Where the bottom fade-to-nothing mask starts, as a fraction of the row stack's own measured
 *  height — content above this line stays fully opaque; the empty track past the last row trails
 *  off into it, reading as "more timeline, not loaded yet" rather than a hard-edged placeholder. */
private const val FADE_START_FRACTION = 0.45f

/**
 * A skeleton loader for the whole Home "timeline" view — the greeting row, the sticky alarm card,
 * and the anchored row list below it — laid out exactly like [HomePlanContent], so the real content
 * fades in without a jump once it arrives. The row list mirrors [TimelineNode]'s row shape (gutter,
 * anchor socket, title, trailing time) and the continuous track spine [TimelineTrackOverlay] paints
 * behind real rows — a static pulsing capsule here, since there's no real anchor geometry yet to
 * trace — fading to fully transparent toward the bottom so it reads as a timeline trailing off into
 * data that hasn't arrived yet, not a hard-edged loading block.
 */
@Composable
internal fun TimelineSkeleton(
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = Spacing.md,
                end = Spacing.md,
                top = statusInsetTop + Spacing.md,
                bottom = navInsetBottom + Spacing.navBarClearance,
            ),
    ) {
        GreetingSkeleton(modifier = Modifier.padding(bottom = Spacing.md))
        AlarmCardSkeleton(modifier = Modifier.padding(bottom = Spacing.xxl))
        TimelineRowsSkeleton(modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun GreetingSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(GREETING_PREFIX_BAR_WIDTH)
                    .height(GREETING_PREFIX_BAR_HEIGHT)
                    .clip(Radius.full)
                    .skeletonPulse(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .width(GREETING_NAME_BAR_WIDTH)
                    .height(GREETING_NAME_BAR_HEIGHT)
                    .clip(Radius.full)
                    .skeletonPulse(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(GREETING_TOGGLE_WIDTH)
                .clip(Radius.full)
                .skeletonPulse(),
        )
    }
}

@Composable
private fun AlarmCardSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ALARM_CARD_SKELETON_HEIGHT)
            .clip(RoundedCornerShape(Radius.xl))
            .skeletonPulse(),
    )
}

@Composable
private fun TimelineRowsSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Forces an offscreen compositing layer so the DstIn mask below applies to the whole
            // subtree's already-drawn pixels, not just this Box's own background (see TextShimmer.kt).
            .graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        FADE_START_FRACTION to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (NODE_GUTTER - TRACK_WIDTH) / 2)
                .width(TRACK_WIDTH)
                .fillMaxHeight()
                .clip(Radius.full)
                .skeletonPulse(),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(ROW_COUNT) { index ->
                TimelineSkeletonRow(titleWidthFraction = TITLE_WIDTH_FRACTIONS[index % TITLE_WIDTH_FRACTIONS.size])
            }
        }
    }
}

@Composable
private fun TimelineSkeletonRow(titleWidthFraction: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ROW_VERTICAL_PADDING, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(NODE_GUTTER),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(INTERIOR_ANCHOR_SIZE)
                    .clip(CircleShape)
                    .skeletonPulse(),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(titleWidthFraction)
                    .height(TITLE_BAR_HEIGHT)
                    .clip(Radius.full)
                    .skeletonPulse(),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Box(
            modifier = Modifier
                .width(TIME_BAR_WIDTH)
                .height(TIME_BAR_HEIGHT)
                .clip(Radius.full)
                .skeletonPulse(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineSkeletonPreview() {
    CronPreview {
        TimelineSkeleton(statusInsetTop = 0.dp, navInsetBottom = 0.dp)
    }
}
