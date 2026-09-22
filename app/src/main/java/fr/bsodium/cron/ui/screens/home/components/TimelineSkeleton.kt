package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.bsodium.cron.ui.components.skeletonPulse
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

/** Placeholder row title widths, hand-varied so the stack doesn't read as one repeated block —
 *  mirrors the natural mix of short event labels and longer AI-run titles in the real timeline. */
private val TITLE_WIDTH_FRACTIONS = listOf(0.62f, 0.4f, 0.78f, 0.5f, 0.34f)

private val TITLE_BAR_HEIGHT = 14.dp
private val TIME_BAR_WIDTH = 34.dp
private val TIME_BAR_HEIGHT = 12.dp

/** Matches [TimelineNode]'s own `verticalPadding` — each row reserves this both above (as top
 *  padding) and below (as a trailing spacer), the same two-sided pattern the real row uses, so
 *  consecutive rows land the same distance apart as real ones do. */
private val ROW_VERTICAL_PADDING = Spacing.md

/** A row's own layout height with the fixed anchor-slot height below: top padding, the anchor slot,
 *  bottom padding. Real [TimelineNode] rows vary this with content (the hero row, multi-line
 *  subtext…) — every placeholder row is deliberately the same simple one-line shape, so this is exact
 *  rather than an approximation, and the track box below can be sized off it directly. */
private val ROW_HEIGHT = ROW_VERTICAL_PADDING * 2 + FLUSH_ANCHOR_SIZE

/** Every anchor slot reserves this fixed height regardless of which shape it draws — the larger,
 *  cap-sized footprint — so a pill row (visually shorter than a cap circle) doesn't shrink its own
 *  row and throw off [ROW_HEIGHT]'s otherwise-uniform math. The shape itself still centers within it
 *  at its own real size. */
private val ANCHOR_SLOT_HEIGHT = FLUSH_ANCHOR_SIZE

/** How many of the trailing rows the fade-out gradient sweeps across, expressed as a count of
 *  [ROW_HEIGHT] units measured up from the very bottom of the track (which itself runs
 *  [TRAILING_ROW_UNITS] past the last real row) — capped so a short stack (e.g. the 2-row append
 *  placeholder) still gets a visible taper instead of the gradient collapsing to nothing. */
private const val FADE_ROW_UNITS = 2f
private const val TRAILING_ROW_UNITS = 1f

/** How far the append placeholder's track reaches up past its own top edge — enough to clear the
 *  real anchor directly above it (its own [ROW_VERTICAL_PADDING] trailing gap, plus half that
 *  anchor's [TRACK_WIDTH] footprint to reach its vertical center) — so the skeleton's own rounded
 *  top end tucks fully behind that anchor instead of reading as a second, separate track starting
 *  fresh below a gap. Only applied when [TimelineRowsSkeleton]'s `topCapped` is false: the initial
 *  load has no real content above it to reach into. */
private val TRACK_CONNECTOR_OVERLAP = ROW_VERTICAL_PADDING + TRACK_WIDTH / 2

/**
 * A skeleton loader for the Home timeline's row list — [rowCount] pulsing placeholders shaped like
 * [TimelineNode]'s real row (gutter, anchor socket, title, trailing time), with a static track spine
 * behind them mirroring [TimelineTrackOverlay]. Only the very first row can get a circular "cap"
 * anchor, and only when [topCapped] is true — every other row, including the last, gets the same
 * short horizontal pill real interior (non-cap) anchors use (see [TimelineNode]'s `AnchorShape.Pill`
 * branch), because the stack never visually terminates: it either fades into not-yet-loaded history
 * ([topCapped] false, chained below already-loaded real rows) or into not-yet-loaded newest content
 * ([topCapped] true, the placeholder for a fresh timeline with nothing loaded yet). A trailing cap
 * circle would wrongly claim "the timeline ends here." Every shape pulses via the shared
 * [fr.bsodium.cron.ui.components.skeletonPulse], staggered top-to-bottom so the stack reads as one
 * wave rather than a single flat blink — the track itself pulses one stagger step behind row 0
 * rather than in lockstep with it, so a cap circle drawn directly over the track never lands on the
 * exact same instantaneous color and silently disappears into it.
 *
 * The whole stack — track and rows alike, not just the empty space past them — fades to the page
 * background over its last couple of rows, via a plain [Brush.verticalGradient] scrim drawn on top
 * rather than a `BlendMode`-masked one: Layoutlib's Compose Preview renderer doesn't apply
 * `drawWithContent` + `BlendMode` content masking (confirmed live — the same code renders correctly
 * under Roborazzi and would on a real device, but shows no fade at all in Android Studio's Preview
 * panel), while a plain alpha-blended overlay needs no special compositing and renders identically
 * everywhere. See docs/preview-quirks.md.
 *
 * Used both for the timeline's own initial load ([topCapped] true — see
 * [TimelineRowsSkeletonInitialPreview]) and, at a smaller [rowCount], as the trailing placeholder
 * while Paging fetches the next page of history during a scroll ([topCapped] false, chained directly
 * below the last real row already on screen — see [TimelineRowsSkeletonAppendPreview] for it in
 * isolation, and `SessionTimelinePreviews.kt`'s `SessionTimelineAppendSkeletonPreview` for it chained
 * onto real rows the way `sessionTimelineItems` actually places it). It draws its own self-contained
 * track rather than registering into [TimelineTrackOverlay]'s live anchor registry: that overlay only
 * exists to track real, positioned rows, and a placeholder has nothing worth animating a socket onto.
 * Because both skeleton and real rows share [NODE_GUTTER]/[TRACK_WIDTH], the two tracks still line up
 * exactly where they meet.
 *
 * When [topCapped] is false, the track also reaches [TRACK_CONNECTOR_OVERLAP] past its own top edge
 * to tuck behind the real row directly above it, at a lower [androidx.compose.ui.zIndex] than that
 * real content so it paints underneath wherever the two overlap — the two tracks read as one
 * continuous line with the seam hidden, rather than two separate pieces meeting end to end. Only the
 * track moves; the rows/title/time placeholders stay exactly where their own top/bottom padding
 * already puts them, which is already the same spacing real consecutive rows use — nothing about the
 * text needs to shift to land on that same rhythm.
 */
@Composable
internal fun TimelineRowsSkeleton(rowCount: Int, topCapped: Boolean = false, modifier: Modifier = Modifier) {
    val trackHeight = ROW_HEIGHT * (rowCount.toFloat() + TRAILING_ROW_UNITS)
    val fadeRowUnits = minOf(FADE_ROW_UNITS, rowCount.toFloat())
    val fadeStartFraction = 1f - ROW_HEIGHT * (fadeRowUnits + TRAILING_ROW_UNITS) / trackHeight
    val connectorOverlap = if (topCapped) 0.dp else TRACK_CONNECTOR_OVERLAP
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Pinned to the un-overlapped height: Box would otherwise grow to fit the track's own
            // taller (connectorOverlap-extended) measured size below, turning "reach up" into extra
            // blank space at the bottom instead. Box doesn't clip non-fillMaxSize children to this
            // explicit size, so the track drawing above it is unaffected.
            .height(trackHeight)
            .then(if (connectorOverlap > 0.dp) Modifier.zIndex(-1f) else Modifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (NODE_GUTTER - TRACK_WIDTH) / 2)
                .offset(y = -connectorOverlap)
                .width(TRACK_WIDTH)
                .height(trackHeight + connectorOverlap)
                .clip(Radius.full)
                .skeletonPulse(staggerIndex = 0),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rowCount) { index ->
                TimelineSkeletonRow(
                    // Offset by 1 from the track's own staggerIndex = 0 above — see the KDoc's cap-
                    // over-track note for why row 0 would otherwise be invisible against it.
                    staggerIndex = index + 1,
                    isCap = topCapped && index == 0,
                    titleWidthFraction = TITLE_WIDTH_FRACTIONS[index % TITLE_WIDTH_FRACTIONS.size],
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        fadeStartFraction to Color.Transparent,
                        1f to CronColors.pageBackground,
                    ),
                ),
        )
    }
}

@Composable
private fun TimelineSkeletonRow(
    staggerIndex: Int,
    isCap: Boolean,
    titleWidthFraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ROW_VERTICAL_PADDING, end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(NODE_GUTTER)
                    .height(ANCHOR_SLOT_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                if (isCap) {
                    Box(
                        modifier = Modifier
                            .size(FLUSH_ANCHOR_SIZE)
                            .clip(CircleShape)
                            .skeletonPulse(staggerIndex),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(FLUSH_ANCHOR_SIZE)
                            .height(INTERIOR_ANCHOR_SIZE)
                            .clip(Radius.full)
                            .skeletonPulse(staggerIndex),
                    )
                }
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
        Spacer(Modifier.height(ROW_VERTICAL_PADDING))
    }
}

@Preview(showBackground = true, name = "Timeline rows skeleton — initial load")
@Composable
private fun TimelineRowsSkeletonInitialPreview() {
    CronPreview {
        Box(modifier = Modifier.padding(top = Spacing.md)) {
            TimelineRowsSkeleton(rowCount = 6, topCapped = true)
        }
    }
}

@Preview(showBackground = true, name = "Timeline rows skeleton — append placeholder")
@Composable
private fun TimelineRowsSkeletonAppendPreview() {
    CronPreview {
        // Enough headroom to show the connector overlap in full, uncropped — in real use that space
        // is the real row above it (see SessionTimelinePreviews.kt's SessionTimelineAppendSkeletonPreview
        // for the connector against actual content); here it's just empty space above.
        Box(modifier = Modifier.padding(top = Spacing.md + TRACK_CONNECTOR_OVERLAP)) {
            TimelineRowsSkeleton(rowCount = 2)
        }
    }
}
