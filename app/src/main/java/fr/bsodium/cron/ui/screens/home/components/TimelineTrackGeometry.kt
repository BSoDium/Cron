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
    val bgTop = if (roundTop) top.cy - halfTrack else 0f
    val bgBottom = if (roundBottom) bottom.cy + halfTrack else viewportHeight
    return SegmentCapDecision(roundTop, roundBottom, bgTop, bgBottom)
}

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
