package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
 * [APPEND_LOADING_ITEM_KEY]'s own top (where this hands off to [TimelineRowsSkeleton]'s own track)
 * comes from `LazyListState.layoutInfo` — not a generous guessed distance, which either falls short of
 * a tall row (a hero row's expandable response block) or, for a short-enough timeline, reaches *past*
 * the topmost composed content into genuinely empty space above it and paints a visible floating patch
 * there.
 *
 * The row immediately above it, though, reads its live position from [TimelineTrackRegistry] — the
 * same source [TimelineTrackOverlay] itself prefers for that anchor — falling back to
 * `nonLatestAnchorCenterY`'s `layoutInfo` formula only when the live handle isn't attached (an isolated
 * preview/test with no real [TimelineNode] registering into the registry). [TimelineTrackOverlay]'s own
 * KDoc documents that formula as measurably wrong (~11.5px) for a row that's simply settled and at
 * rest — exactly the state the append skeleton is shown in most of the time (scrolled to the bottom,
 * waiting on Paging, not actively flinging) — so unconditionally relying on it here, the way an earlier
 * version of this file did, would clip the excluded circle (see below) around a center that can
 * measurably disagree with where [TimelineTrackOverlay] actually painted the real cap, reopening the
 * exact covering-or-gap seam this file exists to avoid.
 *
 * Draws nothing at all once [APPEND_LOADING_ITEM_KEY] isn't present, or nothing is composed above it
 * this frame (both read fresh from `visibleItemsInfo` every draw, no stale caching).
 *
 * The join is a crisp filled edge, not a fade — deliberately. [TimelineTrackOverlay]'s own segment
 * fill rounds its bottom cap with a corner radius equal to half [TRACK_WIDTH] (`drawSegment`'s
 * `corner`), which — because that radius equals half the rect's own width — collapses to a literal
 * semicircle centered on the anchor itself, with its pole at `anchor.cy + halfTrack`. An earlier
 * version of this composable rounded its own top edge by that same radius, which builds the mirror
 * image directly below: two circles of equal radius, tangent to each other at a single point, which
 * pinches to zero width right at that point even though each side is individually smooth — and left
 * the rectangular corners flanking the real anchor's own circle unpainted besides (the real cap's
 * fill only exists *inside* its own circle, so for any Y between the anchor's center and that circle's
 * pole, this composable's [TRACK_WIDTH]-wide footprint was wider than the real fill's shrinking disc,
 * and the difference showed as raw page background either side of it).
 *
 * Both problems share one fix: paint from the anchor's own center downward and `clipPath` out
 * [capCircleRadius]'s exact same circle (identical radius and center to the real cap's own, not a
 * separately-tuned literal) via [ClipOp.Difference], so this composable's fill can only ever land
 * strictly outside where the real content already painted — by construction, not by staying a safe
 * guessed distance away from it, and not by fading to hide an approximate boundary. Using the anchor's
 * smaller accent-socket radius here instead would under-exclude and reopen the covering regression
 * this design avoids; [capCircleRadius] is the radius that actually bounds the real track's own fill,
 * not the smaller shape drawn on top of it. Reaching higher than the anchor's own center is still
 * off-limits for the same reason it always was: this composable is composed after (so paints on top
 * of) [TimelineTrackOverlay] in the shared outer `Box`, so any fill outside the excluded circle but
 * above it would cover real content that genuinely exists there. If anti-aliasing between this clip
 * and the real content's independently-rasterized circle ever leaves a visible ring at the boundary,
 * the safe direction to nudge the exclude radius is *larger*, never smaller — larger only shrinks the
 * covered area by a harmless sliver of page background, while smaller reopens the same real-content-
 * covering bug this whole shape exists to avoid.
 */
@Composable
internal fun SkeletonTrackConnector(
    listState: LazyListState,
    registry: TimelineTrackRegistry,
    isAppendLoading: Boolean,
    contentStartPadding: Dp,
    modifier: Modifier = Modifier,
) {
    // Composing nothing at all (not just skipping the draw work) whenever there's no append skeleton to bridge to skips even the layoutInfo scan below on every ordinary scroll frame.
    if (!isAppendLoading) return
    val color = rememberSkeletonPulseColor(staggerIndex = 0)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val trackWidthPx = with(density) { TRACK_WIDTH.toPx() }
    val trackStartXPx = with(density) { contentStartPadding.toPx() + (NODE_GUTTER - TRACK_WIDTH).toPx() / 2 }
    val verticalPaddingPx = with(density) { Spacing.md.toPx() }
    val halfTrack = trackWidthPx / 2
    val path = remember { Path() }
    var ownCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { ownCoordinates = it }
            .drawBehind {
                val layoutInfo = listState.layoutInfo
                val items = layoutInfo.visibleItemsInfo
                val skeletonIndex = items.indexOfFirst { it.key == APPEND_LOADING_ITEM_KEY }
                if (skeletonIndex <= 0) return@drawBehind
                val skeletonItem = items[skeletonIndex]
                val aboveItem = items[skeletonIndex - 1]
                val bottom = (skeletonItem.offset - layoutInfo.viewportStartOffset).toFloat()
                val cy = liveAnchorCenterY(registry, aboveItem.key, ownCoordinates) ?: nonLatestAnchorCenterY(
                    itemOffset = aboveItem.offset,
                    viewportStartOffset = layoutInfo.viewportStartOffset,
                    verticalPaddingPx = verticalPaddingPx,
                    anchorDiamPx = trackWidthPx,
                )
                if (cy >= bottom) return@drawBehind
                // Raw draw-scope X is always left-based; mirror it under RTL the way Modifier.padding(start = ...) would.
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - trackStartXPx - trackWidthPx else trackStartXPx
                val cx = left + halfTrack
                path.reset()
                path.addOval(Rect(center = Offset(cx, cy), radius = capCircleRadius(halfTrack)))
                clipPath(path, clipOp = ClipOp.Difference) {
                    drawRect(
                        color = color,
                        topLeft = Offset(left, cy),
                        size = Size(trackWidthPx, bottom - cy),
                    )
                }
            },
    )
}

/** The row directly above the append skeleton's own live center Y, mirroring how [TimelineTrackOverlay]
 *  resolves a fresh anchor's position from [TimelineTrackRegistry] — see this file's own KDoc for why
 *  that live handle, not `nonLatestAnchorCenterY`'s `layoutInfo` formula, is the trustworthy source for
 *  a row that's simply at rest. Returns `null` (letting the caller fall back to that formula) whenever
 *  either this composable's own coordinates or the registry's entry for [key] isn't attached yet. */
private fun liveAnchorCenterY(registry: TimelineTrackRegistry, key: Any?, ownCoordinates: LayoutCoordinates?): Float? {
    val overlay = ownCoordinates?.takeIf { it.isAttached } ?: return null
    val coords = (key as? String)?.let { registry.positions[it]?.coordinates }?.takeIf { it.isAttached } ?: return null
    val center = Offset(coords.size.width / 2f, coords.size.height / 2f)
    return overlay.localPositionOf(coords, center).y
}
