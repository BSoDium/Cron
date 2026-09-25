package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect

/**
 * Pure decision/geometry logic for [TimelineTrackOverlay], deliberately decoupled from `DrawScope`
 * and `LayoutCoordinates` so it's unit-testable in plain JUnit (no Robolectric, no device) — see
 * `TimelineTrackGeometryTest.kt`. Every function here takes plain data in and returns plain data
 * out; the overlay's own draw functions call these first, then paint the result.
 */

internal class PlacedAnchor(val id: String, val descriptor: AnchorDescriptor, val cx: Float, val cy: Float)

internal class SleepPill(val top: Float, val bottom: Float, val roundTop: Boolean, val roundBottom: Boolean)

/** Which anchor id is currently confirmed as the segment's top/bottom cap — see
 *  [TimelineTrackOverlay]'s `TrackEndState` for why this is remembered across frames rather than
 *  re-derived from scratch each time. */
internal data class TrackEnds(val topId: String?, val bottomId: String?)

/** A non-Latest row's anchor center Y, computed from its `LazyListItemInfo.offset` instead of a live
 *  per-child `onGloballyPositioned` measurement. **Phase 8 (docs/color-roles.md) update: this is now a
 *  staleness fallback, not the default source** — [resolveAnchorYSource] decides when it's used.
 *
 *  Phase 7 made this the *default* for every non-Latest row, fixing a confirmed race during fast
 *  scrolling under load (some rows' `onGloballyPositioned` callbacks lagged a frame behind their
 *  siblings', leaving the overlay painting a stale, frozen socket) — `LazyListState.layoutInfo` is
 *  written once per measure pass as a single atomic snapshot (verified against the actual AndroidX
 *  Compose Foundation source for this project's pinned version — `LazyListState.kt`'s
 *  `applyMeasureResult`), so there is no per-item torn read possible here, unlike the callback model.
 *  But that made it the default for `animateItem`-driven reflows too — and `LazyListItemInfo.offset` is
 *  fixed during the *measure* phase (`LazyListMeasuredItem.position()`), strictly before `animateItem`'s
 *  `placementDelta` is folded into the actual placement call (`LazyListMeasuredItem.place()` computes
 *  `targetOffset + animation.placementDelta`, verified against the same pinned Compose Foundation
 *  source) — so this function always returns the *final target* slot, never the currently-interpolated
 *  one. Using it as the default made every non-Latest anchor's socket snap to its final position
 *  instantly on every insertion/removal, while the row's own content kept sliding smoothly via
 *  `placementDelta` — a live-confirmed regression. `onGloballyPositioned` (the live [AnchorPosition]
 *  mechanism) fires with the true interpolated position on every tick `animateItem`'s spring is active
 *  (`place()` re-runs and re-reports position each tick), so live is the correct default; this function
 *  is now reserved for the specific case live has gone stale — see [resolveAnchorYSource].
 *
 *  Derivation (unchanged): the anchor sits centered (`Alignment.CenterVertically`) in a `Row` padded
 *  `top = verticalPaddingPx` from the item's own top edge (`TimelineNode.kt`). That Row's own height is
 *  always exactly [anchorDiamPx] — never taller — because every non-Latest row's title is `maxLines = 1`
 *  (`EventNode.kt`, the non-latest branch in `SessionTimeline.kt`) and `footprintDiameter()`
 *  (`TimelineNode.kt`) is a single fixed constant for every anchor shape/kind, so the anchor itself is
 *  always the tallest sibling in that Row regardless of any optional `content` block rendered below it
 *  (a separate Column child, outside the centering Row). The Latest AI-run row is a separate, deliberate
 *  exception — its anchor aligns to a variable-height hero headline via `alignBy(HeroHeadlineCenter)`,
 *  which genuinely needs live measurement, so it stays on the live mechanism unconditionally. */
internal fun nonLatestAnchorCenterY(
    itemOffset: Int,
    viewportStartOffset: Int,
    verticalPaddingPx: Float,
    anchorDiamPx: Float,
): Float = (itemOffset - viewportStartOffset) + verticalPaddingPx + anchorDiamPx / 2f

/** Which source a non-Latest anchor's Y should be painted from this frame. [Live] (the
 *  `onGloballyPositioned`-reported position) is the default: it correctly reflects `animateItem`'s
 *  real interpolated placement, since `LazyListMeasuredItem.place()` re-runs and re-reports position on
 *  every tick a placement spring is active. [LayoutInfo] is used only when the live position has gone
 *  genuinely stale *while the list is actively scrolling* — the live callback has stopped firing
 *  altogether (Round 40's actual failure mode: a large single-frame scroll jump under jank can skip a
 *  row's callback for a frame), not merely because a spring is mid-flight, which keeps the callback
 *  firing every tick and so never reads as stale here.
 *
 *  Round 42 (docs/color-roles.md) found `nonLatestAnchorCenterY`'s formula can be measurably wrong
 *  (confirmed live, ~11.5px, via direct instrumentation comparing it against the true
 *  `onGloballyPositioned` position) for a row that's simply settled and at rest — nothing about it is
 *  animating, so its callback has no reason to fire again, and elapsed time alone doesn't mean the last
 *  value it reported is wrong. Gating the fallback on [isScrollInProgress] fixes this without touching
 *  Round 40's actual danger zone: that failure mode is specifically a fast **fling**, so it only exists
 *  while genuinely scrolling. At rest, a stale-by-time-alone live handle is still trustworthy — there's
 *  nothing in flight that could have moved it out from under a correct value.
 *
 *  [Excluded] means: stale during active scroll, and there's nothing safe to paint with — a real
 *  `LazyColumn` genuinely doesn't have this id visible right now (better to skip a frame than guess; see
 *  [nonLatestAnchorCenterY]'s history for why an earlier, less careful fallback here reintroduced the
 *  Round 40 race). */
internal enum class AnchorYSource { Live, LayoutInfo, Excluded }

/** Pure decision behind [AnchorYSource] — see that enum's KDoc for the reasoning. [staleMillis] must be
 *  wall-clock elapsed time since the anchor's live position last actually updated, not a draw-call
 *  count: `computePlacedAnchors` runs once per `drawBehind` invocation, which isn't strictly 1:1 with
 *  vsync frames, so a call count is only a proxy for elapsed time, not the thing itself — especially
 *  under the exact multi-row-`animateItem` load this function exists to handle correctly. [isScrollInProgress]
 *  is `LazyListState`'s own flag — Round 40's freeze-then-jump failure mode only exists during a fling;
 *  at rest, staleness alone never demotes Live (Round 42).
 *  [noRealLazyColumn] mirrors Phase 7's compatibility path: true only when `listState.layoutInfo
 *  .visibleItemsInfo` is entirely empty (an isolated screenshot test/`@Preview` with no real
 *  `LazyColumn` behind `listState` at all), in which case falling back to a stale live position is still
 *  strictly better than painting nothing. */
internal fun resolveAnchorYSource(
    isLatest: Boolean,
    hasLayoutInfoEntry: Boolean,
    staleMillis: Long,
    staleThresholdMillis: Long,
    isScrollInProgress: Boolean,
    noRealLazyColumn: Boolean,
): AnchorYSource = when {
    isLatest -> AnchorYSource.Live
    !isScrollInProgress -> AnchorYSource.Live
    staleMillis < staleThresholdMillis -> AnchorYSource.Live
    hasLayoutInfoEntry -> AnchorYSource.LayoutInfo
    noRealLazyColumn -> AnchorYSource.Live
    else -> AnchorYSource.Excluded
}

/** The pure filter behind `computePlacedAnchors`: which ids survive the "has both a descriptor AND a
 *  resolved position" requirement, in insertion order of [descriptors]. The caller resolves each
 *  registered [fr.bsodium.cron.ui.screens.home.components.AnchorPosition]'s live
 *  [androidx.compose.ui.layout.LayoutCoordinates] into a plain [Offset] first (that part genuinely
 *  needs a real layout tree, so it stays in `computePlacedAnchors`) — this function only decides
 *  inclusion and builds the resulting list. */
internal fun resolvePlacedAnchors(
    descriptors: Map<String, AnchorDescriptor>,
    resolvedPositions: Map<String, Offset>,
): List<PlacedAnchor> = descriptors.keys.mapNotNull { id ->
    val descriptor = descriptors[id] ?: return@mapNotNull null
    val position = resolvedPositions[id] ?: return@mapNotNull null
    PlacedAnchor(id, descriptor, position.x, position.y)
}

/**
 * Resolves which anchor id the top/bottom cap should track this frame, given the previously
 * confirmed ids. A fresh claim requires BOTH a descriptor claiming `isSegmentTop`/`isSegmentBottom`
 * AND that same anchor being the topmost/bottommost by CURRENT position (`cy`) among [placed] —
 * checking descriptor+placement alone isn't enough, since an anchor can register a position
 * (surviving [resolvePlacedAnchors]'s filter) before that position has animated to its final,
 * topmost spot. See [TimelineTrackOverlay]'s `drawSegment` KDoc (Round 37/38) for the live capture
 * that found both gaps this guards against, and why an absent claim falls back to the previous id
 * instead of ever producing no cap at all.
 */
internal fun resolveTrackEnds(placed: List<PlacedAnchor>, previous: TrackEnds): TrackEnds {
    val topmostByPosition = placed.minByOrNull { it.cy }
    val bottommostByPosition = placed.maxByOrNull { it.cy }
    val freshTopClaim = topmostByPosition?.takeIf { it.descriptor.isSegmentTop }
    val freshBottomClaim = bottommostByPosition?.takeIf { it.descriptor.isSegmentBottom }
    return TrackEnds(
        topId = freshTopClaim?.id ?: previous.topId,
        bottomId = freshBottomClaim?.id ?: previous.bottomId,
    )
}

/** The background rect a segment's top/bottom cap should paint to, and whether each end rounds.
 *  A cap only rounds when [ends] confirms that end's anchor is the current [anchors] boundary;
 *  otherwise the track runs flush to the viewport edge, implying it continues off-screen.
 *
 *  [ends] holds [resolveTrackEnds]'s remembered anchor ids, not a same-frame
 *  `descriptor.isSegmentTop`/`isSegmentBottom` read: a brand-new anchor's descriptor (written by its
 *  own `SideEffect`, pre-layout) and its position (written by `onGloballyPositioned`, post-layout)
 *  can both still be missing for more than one frame after it's prepended (Round 37, confirmed live
 *  by logging the registry at the exact frame the track flushed to the screen edge), and even once
 *  placed, an anchor can register a position before that position has animated to its final,
 *  topmost spot (Round 38, confirmed the same way — grepping verbose logs for `roundTop=false` at
 *  the exact draw call found `top.id != topId` for one frame right after a new anchor's id first
 *  appeared in `placedIds`). [resolveTrackEnds] guards against both by requiring a fresh claim to be
 *  genuinely topmost/bottommost by current position, not merely placed.
 *
 *  A row genuinely disposed by ordinary scrolling clears the id naturally: once it's gone from
 *  `anchors`, `top.id == ends.topId` stops matching (`ends.topId` still names the disposed id, but
 *  nothing in `anchors` does), so the track correctly resumes flushing to the edge instead of a
 *  stale claim. See docs/color-roles.md Round 35/36 for why an `anchors`-only, same-frame fallback
 *  was tried twice and rejected both times before Round 37/38's remembered-state approach. */
internal data class SegmentCapDecision(val roundTop: Boolean, val roundBottom: Boolean, val bgTop: Float, val bgBottom: Float)

internal fun segmentCapDecision(anchors: List<PlacedAnchor>, ends: TrackEnds, viewportHeight: Float, halfTrack: Float): SegmentCapDecision {
    val top = anchors.first()
    val bottom = anchors.last()
    val roundTop = top.id == ends.topId
    val roundBottom = bottom.id == ends.bottomId
    // Cap edge sits a full halfTrack beyond the terminal anchor's center (not at it) so the anchor nests concentrically inside the cap's rounded corner.
    val bgTop = if (roundTop) top.cy - capCircleRadius(halfTrack) else 0f
    val bgBottom = if (roundBottom) bottom.cy + capCircleRadius(halfTrack) else viewportHeight
    return SegmentCapDecision(roundTop, roundBottom, bgTop, bgBottom)
}

/** The radius of the circle a segment-top/bottom cap's own rounded fill occupies, centered on that
 *  cap's anchor — [TimelineTrackOverlay]'s `drawSegment` rounds the cap end with a corner radius equal
 *  to [halfTrack] on a rect exactly [halfTrack] * 2 wide, which collapses to a literal semicircle of
 *  this radius. Named and shared rather than left as an inline `halfTrack` reference so anything that
 *  must exclude or match that exact circle from outside `TimelineTrackOverlay` (see
 *  [SkeletonTrackConnector]'s own KDoc) has one place to call instead of independently re-deriving the
 *  same fact — a future change to the cap's own rounding shows up here as a compile-time-traceable
 *  call site instead of silently desyncing a second, unrelated `halfTrack` literal elsewhere. */
internal fun capCircleRadius(halfTrack: Float): Float = halfTrack

/** The complement of every in-[top]..[bottom]-range anchor's [gap]-buffered gap-range, as
 *  `(start, end)` Y ranges to draw spine line segments for — so the spine never runs through a
 *  socket. Extraction of `drawSpine`'s gap-walking loop. */
internal fun spineGapRanges(anchors: List<PlacedAnchor>, top: Float, bottom: Float, gap: Float): List<Pair<Float, Float>> {
    val ranges = mutableListOf<Pair<Float, Float>>()
    var cursor = top
    anchors
        .filter { it.cy in top..bottom }
        .forEach { anchor ->
            val gapRadius = anchor.descriptor.contentRadiusPx + gap
            val gapStart = anchor.cy - gapRadius
            val gapEnd = anchor.cy + gapRadius
            if (gapStart > cursor) ranges += cursor to gapStart
            cursor = maxOf(cursor, gapEnd)
        }
    if (bottom > cursor) ranges += cursor to bottom
    return ranges
}

/** Walks a segment's anchors top→bottom and emits one rounded pill per contiguous asleep run. A run
 *  open above the topmost visible anchor (or still open below the bottom one) extends to [bgTop]/
 *  [bgBottom] and only rounds there if that edge is a real segment cap — so a sleep stretch continues
 *  seamlessly off the visible range instead of capping mid-scroll. */
internal fun buildSleepPills(
    anchors: List<PlacedAnchor>,
    bgTop: Float,
    bgBottom: Float,
    roundTop: Boolean,
    roundBottom: Boolean,
    halfTrack: Float,
): List<SleepPill> {
    val pills = mutableListOf<SleepPill>()
    val startsAsleep = anchors.first().descriptor.asleepAbove
    var openTop: Float? = if (startsAsleep) bgTop else null
    var openRounded = if (startsAsleep) roundTop else false
    for (anchor in anchors) {
        val above = anchor.descriptor.asleepAbove
        val below = anchor.descriptor.asleepBelow
        // Same concentric-cap rule as the background — see drawSegment's comment on cap-edge placement.
        if (above && !below) {
            pills += SleepPill(openTop ?: (anchor.cy - halfTrack), anchor.cy + halfTrack, openRounded, roundBottom = true)
            openTop = null
        } else if (!above && below) {
            openTop = anchor.cy - halfTrack
            openRounded = true
        }
    }
    val trailingOpen = openTop
    if (anchors.last().descriptor.asleepBelow && trailingOpen != null) {
        pills += SleepPill(trailingOpen, bgBottom, openRounded, roundBottom)
    }
    return pills
}

/** Pure geometry for one capped rect — the corner-radius assignment `cappedRect` (in
 *  `TimelineTrackOverlay.kt`) wraps into an actual `Path` for painting. Kept separate because
 *  `Path()` construction is native-backed on Android and needs Robolectric to run in a JVM test;
 *  `RoundRect`/`Rect`/`CornerRadius` are plain Kotlin data classes with no such dependency. */
internal fun cappedRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    roundTop: Boolean,
    roundBottom: Boolean,
    corner: CornerRadius,
): RoundRect {
    val zero = CornerRadius.Zero
    return RoundRect(
        Rect(left, top, right, bottom),
        topLeft = if (roundTop) corner else zero,
        topRight = if (roundTop) corner else zero,
        bottomLeft = if (roundBottom) corner else zero,
        bottomRight = if (roundBottom) corner else zero,
    )
}

/** Which [AnchorShape] a row's socket should crossfade *from*, and the shape it's currently settled
 *  on — the pure fold behind Phase 11's Circle→Pill (and any other shape identity change) crossfade
 *  fix (docs/color-roles.md). [AnchorShape] itself has no interpolation between variants (`Circle` is
 *  a literal `drawCircle`, `Pill` a literal `drawRoundRect` — mismatched geometry, no shared vertex
 *  topology to morph between), so a shape identity change needs a crossfade (draw both, blend alpha)
 *  instead of a geometric morph. [committedAtCap] is a plain state-carrying field the caller threads
 *  back in on the next call — mirrors [resolveTrackEnds]'s remembered-state pattern. */
internal data class ShapeCrossfadeState(
    val outgoingShape: AnchorShape?,
    val committedAtCap: Boolean,
    val committedShape: AnchorShape,
)

/** Advances [ShapeCrossfadeState] by one recomposition's worth of `(atCap, shape)`. A genuine `atCap`
 *  flip captures the previously-committed shape as [ShapeCrossfadeState.outgoingShape] so the caller
 *  can crossfade away from it; anything else (including the very first call for a row, or a call
 *  where `atCap` hasn't changed) just commits the latest shape with no outgoing shape to fade from. */
internal fun advanceShapeCrossfadeState(
    previous: ShapeCrossfadeState?,
    atCap: Boolean,
    shape: AnchorShape,
): ShapeCrossfadeState = when {
    previous == null -> ShapeCrossfadeState(outgoingShape = null, committedAtCap = atCap, committedShape = shape)
    atCap != previous.committedAtCap -> ShapeCrossfadeState(outgoingShape = previous.committedShape, committedAtCap = atCap, committedShape = shape)
    else -> previous.copy(committedShape = shape)
}
