package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.skeletonPulse
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

/** Placeholder row title widths, hand-varied so the stack doesn't read as one repeated block —
 *  mirrors the natural mix of short event labels and longer AI-run titles in the real timeline. */
private val TITLE_WIDTH_FRACTIONS = listOf(0.62f, 0.4f, 0.78f, 0.5f, 0.34f)

private val TITLE_BAR_HEIGHT = 14.dp
private val TIME_BAR_WIDTH = 34.dp
private val TIME_BAR_HEIGHT = 12.dp
private val ROW_VERTICAL_PADDING = Spacing.md

/** Each row's own layout height — top padding plus the anchor circle, its tallest child — matching
 *  [TimelineSkeletonRow]'s real measured height so the track box below can be sized deterministically
 *  instead of needing to measure the row stack it sits behind. */
private val ROW_HEIGHT = ROW_VERTICAL_PADDING + INTERIOR_ANCHOR_SIZE

/** Extra track length, in [ROW_HEIGHT] units, appended past the last real row — purely empty space
 *  with no row content, so the placeholder rows themselves stay fully legible and only the bare track
 *  trails off into it. This is what sells "more timeline, not loaded yet" rather than fading the
 *  content the user is meant to actually read. */
private const val TRAILING_FADE_ROW_UNITS = 2.5f

/**
 * A skeleton loader for the Home timeline's row list — [rowCount] pulsing placeholders shaped like
 * [TimelineNode]'s real row (gutter, anchor socket, title, trailing time), with a static track spine
 * behind them mirroring [TimelineTrackOverlay]. Every shape pulses via the shared
 * [fr.bsodium.cron.ui.components.skeletonPulse], staggered top-to-bottom so the stack reads as one
 * wave rather than a single flat blink. The track itself runs [TRAILING_FADE_ROW_UNITS] rows past the
 * last placeholder and fades to fully transparent over that stretch, so the stack reads as trailing
 * off into data that hasn't arrived yet rather than stopping at a hard edge — used both for the
 * timeline's own initial load and, at a smaller [rowCount], as the trailing placeholder while Paging
 * fetches the next page of history during a scroll.
 */
@Composable
internal fun TimelineRowsSkeleton(rowCount: Int, modifier: Modifier = Modifier) {
    val rowsHeight = ROW_HEIGHT * rowCount
    val totalTrackHeight = rowsHeight + ROW_HEIGHT * TRAILING_FADE_ROW_UNITS
    val fadeStartFraction = rowsHeight / totalTrackHeight
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (NODE_GUTTER - TRACK_WIDTH) / 2)
                .width(TRACK_WIDTH)
                .height(totalTrackHeight)
                .clip(Radius.full)
                .skeletonPulse(staggerIndex = 0)
                // Forces an offscreen compositing layer so the DstIn mask below applies to the
                // track's already-drawn pixels, not just this Box's own background (see
                // ui/components/TextShimmer.kt for the same pattern).
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            fadeStartFraction to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rowCount) { index ->
                TimelineSkeletonRow(
                    staggerIndex = index,
                    titleWidthFraction = TITLE_WIDTH_FRACTIONS[index % TITLE_WIDTH_FRACTIONS.size],
                )
            }
        }
    }
}

@Composable
private fun TimelineSkeletonRow(
    staggerIndex: Int,
    titleWidthFraction: Float,
    modifier: Modifier = Modifier,
) {
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
                    .skeletonPulse(staggerIndex),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(titleWidthFraction)
                    .height(TITLE_BAR_HEIGHT)
                    .clip(Radius.full)
                    .skeletonPulse(staggerIndex),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Box(
            modifier = Modifier
                .width(TIME_BAR_WIDTH)
                .height(TIME_BAR_HEIGHT)
                .clip(Radius.full)
                .skeletonPulse(staggerIndex),
        )
    }
}

@Preview(showBackground = true, name = "Timeline rows skeleton — initial load")
@Composable
private fun TimelineRowsSkeletonInitialPreview() {
    CronPreview {
        Box(modifier = Modifier.padding(top = Spacing.md)) {
            TimelineRowsSkeleton(rowCount = 6)
        }
    }
}

@Preview(showBackground = true, name = "Timeline rows skeleton — append placeholder")
@Composable
private fun TimelineRowsSkeletonAppendPreview() {
    CronPreview {
        Box(modifier = Modifier.padding(top = Spacing.md)) {
            TimelineRowsSkeleton(rowCount = 2)
        }
    }
}
