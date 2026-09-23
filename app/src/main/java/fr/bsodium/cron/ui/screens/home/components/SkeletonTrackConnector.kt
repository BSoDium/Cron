package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import fr.bsodium.cron.ui.components.rememberSkeletonPulseColor
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Bridges the small visual gap between the last already-loaded real row and
 * [TimelineRowsSkeleton]'s own append-placeholder track (`topCapped` false) directly below it, so
 * the two read as one continuous line rather than two separate pieces meeting at a seam.
 *
 * This has to live here, genuinely behind the *entire* `LazyColumn`, rather than inside
 * [TimelineRowsSkeleton] itself (which tried three different self-contained approaches — a wider
 * overlap hidden via a lower `zIndex`, a flat connector kept within the real row's own guaranteed-
 * empty trailing padding, then a tapering wedge — see that file's git history). All three approaches
 * were bounded by the same wall: `zIndex` only reorders paint among siblings under the same direct
 * parent, and this skeleton's own track is a `LazyColumn` item, several levels *inside* the
 * `LazyColumn`, while the real anchor's colored background disc is painted by
 * [TimelineTrackOverlay]'s own `Canvas` — a *sibling of the LazyColumn itself*, composed first and
 * therefore always behind it. No `zIndex` on anything inside the `LazyColumn` can out-rank a layer
 * further up the tree, so anything drawn from inside it — no matter how it's shaped or tuned — always
 * painted on top of that disc wherever the two happened to overlap, and staying safely below that
 * boundary left a real (if shrinking) seam no shape could fully close, because a `LazyColumn` item has
 * no reliable way to know exactly where the real anchor above it actually is.
 *
 * This composable sidesteps both problems by not being a `LazyColumn` item at all: like
 * [TimelineTrackOverlay], it's a sibling of the `LazyColumn` in the same outer `Box`, composed before
 * it — genuinely behind everything the list draws, real rows and the skeleton's own rows alike, by
 * construction rather than by z-order trickery.
 *
 * It doesn't touch [TimelineTrackRegistry] — it reads the same source [TimelineTrackOverlay] itself
 * falls back to for a non-Latest row's Y, `LazyListState.layoutInfo` (see `nonLatestAnchorCenterY`'s
 * KDoc), for **both** endpoints: [APPEND_LOADING_ITEM_KEY]'s own top (where this hands off to
 * [TimelineRowsSkeleton]'s own track) and the row immediately above it in `visibleItemsInfo` — not a
 * generous guessed distance, which either falls short of a tall row (a hero row's expandable response
 * block) or, for a short-enough timeline, reaches *past* the topmost composed content into genuinely
 * empty space above it and paints a visible floating patch there. Reusing `nonLatestAnchorCenterY`'s
 * exact formula plus [TimelineTrackOverlay]'s own `halfTrack` reach lands this on the identical `Y`
 * [TimelineTrackOverlay] itself stops painting at (`bgBottom = anchor.cy + halfTrack`), so the two
 * tracks hand off at the same boundary the real one already uses — not a separate estimate of it.
 *
 * Draws nothing at all once [APPEND_LOADING_ITEM_KEY] isn't present, or nothing is composed above it
 * this frame (both read fresh from `visibleItemsInfo` every draw, no stale caching).
 *
 * The join is faded, not a crisp filled edge. [TimelineTrackOverlay]'s own segment fill rounds its
 * bottom cap with a corner radius equal to half [TRACK_WIDTH] (`drawSegment`'s `corner`), which —
 * because that radius equals half the rect's own width — collapses to a literal semicircle whose
 * pole sits exactly at `anchor.cy + halfTrack`, the same Y this composable hands off from. Rounding
 * this composable's own top edge by that same radius (the obvious first attempt) builds the mirror
 * image directly below: two circles of equal radius, tangent to each other at that single point. Two
 * externally tangent equal circles genuinely pinch to zero width right at the point they touch —
 * mathematically smooth on each side individually, but visibly a seam once you consider it's two
 * independently painted, independently colored fills meeting there rather than one continuous shape.
 * A flat (unrounded) top trades that pinch for a hard step instead, since the real content immediately
 * above is genuinely zero-width at that exact Y — there's no shape whose crisp edge can match it.
 * Neither can be fixed by reaching higher and painting more of the anchor's own circle from here:
 * this composable is composed after (so paints on top of) [TimelineTrackOverlay] in the shared outer
 * `Box`, so any fill above `anchor.cy + halfTrack` would cover the real anchor's own disc and rim —
 * the exact Round-B regression this file's own git history already ruled out once.
 *
 * Fading the fill in from [Color.Transparent] at the seam up to full opacity over the same
 * half-[TRACK_WIDTH] span the corner rounding already spans sidesteps the problem rather than solving
 * it geometrically: there's no Y at which two differently-colored, fully-opaque regions are adjacent,
 * so there's nothing for the eye to read as a boundary.
 *
 * That still left the rectangular corners flanking the real anchor's own circle unpainted: the real
 * cap's fill only exists *inside* its own governing circle (radius `halfTrack`, centered on the
 * anchor — the same circle `bgBottom = anchor.cy + halfTrack`'s pole sits on), so for any Y between
 * the anchor's center and that pole, the real fill occupies a shrinking disc while this composable's
 * own [TRACK_WIDTH]-wide footprint is wider than that disc — the difference between the two shows as
 * raw page background either side of the circle, not covered by anything. This composable now paints
 * from the anchor's own center downward and `clipPath`s out that exact same circle (identical radius
 * and center as the real cap's own) via [ClipOp.Difference], so its fill can only ever land strictly
 * outside where the real content already painted — by construction, not by staying a safe guessed
 * distance away from it. Using the anchor's smaller accent-socket radius here instead of `halfTrack`
 * would under-exclude and reopen the exact covering regression described above; `halfTrack` is the
 * radius that actually bounds the real track's own fill, not the smaller shape drawn on top of it. If
 * anti-aliasing between this clip and the real content's independently-rasterized circle ever leaves a
 * visible ring at the boundary, the safe direction to nudge the exclude radius is *larger*, never
 * smaller — larger only shrinks the covered area by a harmless sliver of page background, while
 * smaller reopens the same real-content-covering bug this whole shape exists to avoid.
 */
@Composable
internal fun SkeletonTrackConnector(
    listState: LazyListState,
    contentStartPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val color = rememberSkeletonPulseColor(staggerIndex = 0)
    val density = LocalDensity.current
    val trackWidthPx = with(density) { TRACK_WIDTH.toPx() }
    val trackStartXPx = with(density) { contentStartPadding.toPx() + (NODE_GUTTER - TRACK_WIDTH).toPx() / 2 }
    val verticalPaddingPx = with(density) { Spacing.md.toPx() }
    val halfTrack = trackWidthPx / 2
    val path = remember { Path() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val layoutInfo = listState.layoutInfo
                val items = layoutInfo.visibleItemsInfo
                val skeletonIndex = items.indexOfFirst { it.key == APPEND_LOADING_ITEM_KEY }
                if (skeletonIndex <= 0) return@drawBehind
                val skeletonItem = items[skeletonIndex]
                val aboveItem = items[skeletonIndex - 1]
                val bottom = (skeletonItem.offset - layoutInfo.viewportStartOffset).toFloat()
                val cy = nonLatestAnchorCenterY(
                    itemOffset = aboveItem.offset,
                    viewportStartOffset = layoutInfo.viewportStartOffset,
                    verticalPaddingPx = verticalPaddingPx,
                    anchorDiamPx = trackWidthPx,
                )
                if (cy >= bottom) return@drawBehind
                val cx = trackStartXPx + halfTrack
                path.reset()
                path.addOval(Rect(center = Offset(cx, cy), radius = halfTrack))
                val fadeEnd = minOf(bottom, cy + halfTrack)
                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            (fadeEnd - cy) / (bottom - cy) to color,
                            1f to color,
                            startY = cy,
                            endY = bottom,
                        ),
                        topLeft = Offset(trackStartXPx, cy),
                        size = Size(trackWidthPx, bottom - cy),
                    )
                }
            },
    )
}
