package fr.bsodium.cron.ui.screens.home.components

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.ui.theme.Spacing

/** Thickness of the center-line spine — thin, a detail line rather than another track. */
private val SPINE_WIDTH = 2.dp

/** Buffer added to each anchor's content radius to open a gap in the spine around that anchor. */
private val SPINE_GAP = 8.dp

/** How much of `onSurfaceVariant` blends into the awake spine — the rest is the track's own fill
 *  (Round 23: reverted the Round 21/22 outline-stroke track back to a plain fill, so the spine now
 *  needs to visibly stand out against that fill rather than against a hairline boundary. Blending
 *  toward `onSurfaceVariant` — the M3 role calibrated to read clearly on top of surface-family
 *  tones, which `surfaceContainerHigh` is — gives the line real presence without the earlier
 *  background-blend approach, which only worked when the track itself was a near-invisible stroke). */
private const val AWAKE_SPINE_BLEND = 0.45f

/** How much of `onSecondary` blends into the asleep spine — still a minority (subtle, "closer to
 *  the background" than a full-strength line), but more than the awake blend so it keeps a touch
 *  more presence ("elevated") against the bolder, darker sleep track. `onSecondary` (not `onPrimary`)
 *  since Round 21 moved the sleep track itself from `primary` to `secondary`. */
private const val ASLEEP_SPINE_BLEND = 0.4f

/** Corner radius (as a fraction of a fully-pressed [AnchorShape.Pill]'s square footprint width) a
 *  pressed Pill's `drawRoundRect` shrinks toward — matches `MaterialShapes.Square`'s own rounding
 *  (`CornerRounding(radius = 0.3f)` on a unit `RoundedPolygon.rectangle`, read from the M3 source),
 *  so the plain-geometry replacement (Round 27.10) still lands on a recognizably "rounded square"
 *  corner rather than an arbitrary guess. */
private const val SQUARE_CORNER_FRACTION = 0.3f

/** The whole timeline track, painted once behind every row. Reads live anchor geometry from
 *  [registry] and draws one continuous set of paths — a round-capped background stadium per segment,
 *  continuous sleep pills, and each anchor's accent socket on top. Because the track is a single
 *  painter rather than a slice per row, it can't gap or re-cap while rows glide/fade in and out: the
 *  caps simply track the top/bottom anchor's live position. See docs/color-roles.md Round 13.
 *
 *  Every draw call below fills at full, constant opacity — no scroll-derived fade-in, since elements
 *  must stay fully visible as long as any part of them is on screen. A row disposing while still
 *  visually on-screen can still cause a visible pop; see [computePlacedAnchors]' KDoc for the current
 *  state of that issue.
 *
 *  Overscroll stretch is rendered by a single `Modifier.overscroll` on the parent Box that wraps
 *  both this overlay and the LazyColumn, so the track and the row content it sits under stretch as
 *  one subtree, with no shear. See the AndroidX `OverscrollRenderedOnTopOfLazyListDecorations`
 *  sample for the canonical single-render pattern.
 *
 *  [registry]'s anchor positions are live [LayoutCoordinates] handles ([AnchorPosition]), queried
 *  fresh at draw time in [computePlacedAnchors] rather than cached — a cached `Offset` goes stale
 *  across any nav transition that scales/fades the subtree without retriggering layout (a
 *  draw-phase-only `graphicsLayer` transform, as `MainActivity.kt`'s tab transitions use), since each
 *  row's `onGloballyPositioned` only fires once, at its own placement frame. See docs/color-roles.md
 *  for the full history. The flush-to-screen-edge flash on a new arrival is covered in
 *  [drawSegment]'s KDoc.
 *
 *  Since Phase 7 (docs/color-roles.md), that per-child `onGloballyPositioned` model only still supplies
 *  the Latest row's Y (its anchor genuinely needs live measurement — see [nonLatestAnchorCenterY]'s
 *  KDoc) and every row's X (scroll-invariant, so staleness there is harmless). Every other row's Y comes
 *  from [listState]'s own atomic `layoutInfo` snapshot instead — the fix for a confirmed live bug where
 *  a fast fling under load could leave some rows' callbacks a frame behind their siblings', painting a
 *  frozen, disconnected socket. */
@Composable
internal fun TimelineTrackOverlay(
    registry: TimelineTrackRegistry,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val awakeColor = trackColorFor(isAsleep = false)
    val asleepColor = trackColorFor(isAsleep = true)
    // A minority blend toward a role that reads on the track's own fill — a hint of a line, not a drawn boundary; a by-eye starting point, adjust if it doesn't read live.
    val awakeSpineColor = lerp(awakeColor, MaterialTheme.colorScheme.onSurfaceVariant, AWAKE_SPINE_BLEND)
    val asleepSpineColor = lerp(asleepColor, MaterialTheme.colorScheme.onSecondary, ASLEEP_SPINE_BLEND)
    val scratch = remember { android.graphics.Path() }
    var overlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val endState = remember { TrackEndState() }
    val staleness = remember { AnchorStalenessTracker() }
    val context = LocalContext.current
    val density = LocalDensity.current
    // Every non-Latest anchor's Row-centered Y depends only on these two constants — see nonLatestAnchorCenterY's KDoc for the derivation. Computed once per composition, not per anchor per frame.
    val verticalPaddingPx = with(density) { Spacing.md.toPx() }
    val anchorDiamPx = with(density) { TRACK_WIDTH.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayCoordinates = it }
            .drawBehind {
                // Read directly in the draw phase (not via derivedStateOf) so a position change redraws the same frame with no recomposition round-trip; see computePlacedAnchors.
                if (visible) {
                    val placed = computePlacedAnchors(
                        registry = registry,
                        overlayCoordinates = overlayCoordinates,
                        listState = listState,
                        verticalPaddingPx = verticalPaddingPx,
                        anchorDiamPx = anchorDiamPx,
                        staleness = staleness,
                        context = context,
                    )
                    drawTrack(placed, endState, awakeColor, asleepColor, awakeSpineColor, asleepSpineColor, scratch, context)
                }
            },
    )
}

/** Remembers which anchor id last legitimately confirmed the segment's top/bottom cap — a plain
 *  draw-phase-mutated field, not snapshot state, since it only needs to survive across this
 *  composable's own draw calls, never trigger recomposition. See [drawTrack]'s KDoc for why this
 *  replaces a same-frame registry lookup. Wraps [TrackEnds] (the pure, testable value) as a mutable
 *  holder for the draw phase to update in place. */
private class TrackEndState {
    var ends = TrackEnds(topId = null, bottomId = null)
}

/** Phase 6 (docs/color-roles.md, scroll-jank "stuck pill" investigation): counts, per anchor id, how
 *  many consecutive [computePlacedAnchors] calls have passed since [TimelineTrackRegistry.setPosition]
 *  last actually ran for that id — i.e. since [TimelineTrackRegistry.positions]' entry for it was last
 *  replaced with a fresh [AnchorPosition] wrapper. Compares the *wrapper* by reference, not the
 *  [LayoutCoordinates] it holds: Compose may keep handing back the same `LayoutCoordinates` instance
 *  across layout passes while only mutating its internal position, so a coordinates-reference check
 *  would say nothing about whether this frame's layout pass actually touched this anchor — the wrapper
 *  is deliberately rebuilt on every `setPosition` call for exactly this reason (see [AnchorPosition]'s
 *  KDoc). A count that climbs for one id while its neighbors' reset to 0 on the same call is direct
 *  evidence that anchor's row stopped receiving layout passes while the rest of the visible list kept
 *  moving — the "stalled `animateItem` spring" hypothesis, distinct from ordinary whole-list jank
 *  (where every visible anchor's count would climb together, since the draw call itself is simply
 *  skipped rather than any one anchor being singled out). */
private class AnchorStalenessTracker {
    private val lastSeenWrapper = mutableMapOf<String, AnchorPosition>()
    private val staleFrameCounts = mutableMapOf<String, Int>()

    fun update(positions: Map<String, AnchorPosition>): Map<String, Int> {
        lastSeenWrapper.keys.retainAll(positions.keys)
        staleFrameCounts.keys.retainAll(positions.keys)
        positions.forEach { (id, wrapper) ->
            if (lastSeenWrapper[id] !== wrapper) {
                lastSeenWrapper[id] = wrapper
                staleFrameCounts[id] = 0
            } else {
                staleFrameCounts[id] = (staleFrameCounts[id] ?: 0) + 1
            }
        }
        return staleFrameCounts
    }
}

/** How many consecutive [computePlacedAnchors] calls an anchor must go without a fresh
 *  [TimelineTrackRegistry.setPosition] call before [AnchorStalenessTracker] flags it — high enough to
 *  skip the routine "only one id updates per call" case (see [AnchorPosition]'s KDoc), low enough to
 *  catch a stall lasting a meaningful fraction of a scroll gesture. */
private const val STALE_FRAME_THRESHOLD = 8

/** Reads every currently-registered anchor's live position and returns it. Returns an empty list
 *  while this overlay's own coordinates aren't attached yet (the first frame or two of any mount).
 *
 *  X always comes from the live [LayoutCoordinates] handle ([AnchorPosition]) — scroll-invariant
 *  (every anchor centers in the same fixed-width gutter), so per-frame staleness there is harmless.
 *  Y prefers [nonLatestAnchorCenterY] sourced from [listState]'s atomic `layoutInfo` snapshot for every
 *  anchor except the Latest row (see [nonLatestAnchorCenterY]'s KDoc and this file's own top-level KDoc
 *  for why — Phase 7, docs/color-roles.md), falling back to the live handle's Y when this id isn't in
 *  this frame's `visibleItemsInfo` at all — a row rendered outside a real `LazyColumn` (isolated
 *  screenshot tests, `@Preview`s) never populates `visibleItemsInfo`, and must keep resolving position
 *  exactly as before rather than losing its socket entirely. In production, `TimelineTrackOverlay` is
 *  always paired with the real `LazyColumn` its rows live in, so this fallback is a compatibility path,
 *  not the fix's actual safety net.
 *
 *  A disposed row's position is not cached or filtered against scroll state beyond that. This means a
 *  row disposing while still visually on-screen can flicker — an open issue, see docs/color-roles.md
 *  for prior attempts. A fix should control `LazyColumn`'s own beyond-viewport composition margin
 *  directly rather than caching or second-guessing its disposal decisions from outside.
 *
 *  Resolving each row's live [LayoutCoordinates] into a plain [Offset] genuinely needs a real layout
 *  tree, so it stays here; the actual inclusion filter is [resolvePlacedAnchors], a pure function
 *  unit-tested separately in `TimelineTrackGeometryTest.kt`. */
private fun computePlacedAnchors(
    registry: TimelineTrackRegistry,
    overlayCoordinates: LayoutCoordinates?,
    listState: LazyListState,
    verticalPaddingPx: Float,
    anchorDiamPx: Float,
    staleness: AnchorStalenessTracker,
    context: Context,
): List<PlacedAnchor> {
    val overlay = overlayCoordinates?.takeIf { it.isAttached } ?: return emptyList()
    val layoutInfo = listState.layoutInfo
    val viewportStartOffset = layoutInfo.viewportStartOffset
    val visibleByKey = layoutInfo.visibleItemsInfo.associateBy { it.key }
    val detachedIds = mutableListOf<String>()
    val resolvedPositions = registry.descriptors.mapNotNull { (id, descriptor) ->
        val position = registry.positions[id] ?: return@mapNotNull null
        val coords = position.coordinates.takeIf { it.isAttached } ?: run {
            detachedIds += id
            return@mapNotNull null
        }
        val center = Offset(coords.size.width / 2f, coords.size.height / 2f)
        val liveCenter = overlay.localPositionOf(coords, center)
        // Prefer layoutInfo (race-free) whenever this id is actually in it; fall back to the live
        // handle otherwise — a row rendered outside a real LazyColumn (isolated screenshot tests,
        // Previews) has no visibleItemsInfo entry at all, and must keep working exactly as before.
        val item = if (descriptor.isLatest) null else visibleByKey[id]
        val cy = if (item != null) {
            nonLatestAnchorCenterY(item.offset, viewportStartOffset, verticalPaddingPx, anchorDiamPx)
        } else {
            liveCenter.y
        }
        id to Offset(liveCenter.x, cy)
    }.toMap()
    val staleCounts = staleness.update(registry.positions)
    val stalledIds = staleCounts.filterValues { it >= STALE_FRAME_THRESHOLD }.keys
    if (detachedIds.isNotEmpty() || stalledIds.isNotEmpty()) {
        TimelineDebugLog.d(context) {
            "computePlacedAnchors detachedIds=$detachedIds stalledIds=$stalledIds staleCounts=$staleCounts"
        }
    }
    return resolvePlacedAnchors(registry.descriptors, resolvedPositions)
}

private fun DrawScope.drawTrack(
    placed: List<PlacedAnchor>,
    endState: TrackEndState,
    awakeColor: Color,
    asleepColor: Color,
    awakeSpineColor: Color,
    asleepSpineColor: Color,
    scratch: android.graphics.Path,
    context: Context,
) {
    if (placed.isEmpty()) return

    val halfTrack = TRACK_WIDTH.toPx() / 2
    // Every anchor centers in the same fixed-width gutter; averaging their x's is order-independent and self-corrects if one row's position lands a frame stale.
    val trackCenterX = placed.map { it.cx }.average().toFloat()
    val corner = CornerRadius(halfTrack)
    endState.ends = resolveTrackEnds(placed, endState.ends)
    TimelineDebugLog.d(context) {
        "drawTrack placed=${placed.map { "${it.id}@${it.cy}" }} resolvedTopId=${endState.ends.topId} resolvedBottomId=${endState.ends.bottomId}"
    }

    drawSegment(
        anchors = placed.sortedBy { it.cy },
        trackCenterX = trackCenterX,
        halfTrack = halfTrack,
        corner = corner,
        awakeColor = awakeColor,
        asleepColor = asleepColor,
        awakeSpineColor = awakeSpineColor,
        asleepSpineColor = asleepSpineColor,
        scratch = scratch,
        ends = endState.ends,
        context = context,
    )
}

/** Delegates the cap/rounding decision to [segmentCapDecision] (see its KDoc, and
 *  `TimelineTrackGeometryTest.kt`, for the full Round 37/38 history of why a cap only rounds when
 *  [ends] confirms that end's anchor, not a same-frame `descriptor.isSegmentTop` read). */
private fun DrawScope.drawSegment(
    anchors: List<PlacedAnchor>,
    trackCenterX: Float,
    halfTrack: Float,
    corner: CornerRadius,
    awakeColor: Color,
    asleepColor: Color,
    awakeSpineColor: Color,
    asleepSpineColor: Color,
    scratch: android.graphics.Path,
    ends: TrackEnds,
    context: Context,
) {
    val decision = segmentCapDecision(anchors, ends, size.height, halfTrack)
    val (roundTop, roundBottom, bgTop, bgBottom) = decision
    val top = anchors.first()
    val bottom = anchors.last()
    val left = trackCenterX - halfTrack
    val right = trackCenterX + halfTrack
    TimelineDebugLog.d(context) {
        "drawSegment top.id=${top.id} bottom.id=${bottom.id} roundTop=$roundTop roundBottom=$roundBottom bgTop=$bgTop bgBottom=$bgBottom"
    }

    // Plain fill; the sleep pills drawn after naturally cover their own range, no separate "subtract" geometry needed. See trackColorFor's KDoc for the fill-vs-outline history.
    drawPath(cappedRect(left, bgTop, right, bgBottom, roundTop, roundBottom, corner), awakeColor)

    val pills = buildSleepPills(anchors, bgTop, bgBottom, roundTop, roundBottom, halfTrack)
    pills.forEach { pill ->
        drawPath(cappedRect(left, pill.top, right, pill.bottom, pill.roundTop, pill.roundBottom, corner), asleepColor)
    }

    // Spine layered like the fills above: awake color across the whole segment, then each sleep pill's own stretch overpainted in the asleep color.
    drawSpine(anchors, bgTop, bgBottom, trackCenterX, awakeSpineColor)
    pills.forEach { pill ->
        drawSpine(anchors, pill.top, pill.bottom, trackCenterX, asleepSpineColor)
    }

    anchors.forEach { anchor ->
        drawSocket(anchor, scratch)
    }
}

/** Draws the thin center-line "spine" down [trackCenterX] across [top]..[bottom], leaving a
 *  [SPINE_GAP]-buffered gap around each anchor's own drawn shape so the line never runs through a
 *  socket. Called once per segment for the awake stretch, then once per sleep pill (with that pill's
 *  own extent) so the asleep color overpaints its sub-range — the same whole-then-overlay layering the
 *  background/sleep-pill fills already use. */
private fun DrawScope.drawSpine(
    anchors: List<PlacedAnchor>,
    top: Float,
    bottom: Float,
    trackCenterX: Float,
    color: Color,
) {
    val strokeWidth = SPINE_WIDTH.toPx()
    spineGapRanges(anchors, top, bottom, SPINE_GAP.toPx()).forEach { (start, end) ->
        drawLine(color, Offset(trackCenterX, start), Offset(trackCenterX, end), strokeWidth, StrokeCap.Round)
    }
}

/** Fills one anchor's socket with its accent. A cap anchor's content radius is already flush
 *  (`== halfTrack - CAP_ANCHOR_PADDING`), so a circular socket fills the track to the same thin rim
 *  everywhere — no cap-only inflation needed. The Latest morph is bounded within the same flush
 *  diameter by [buildMorphPath]'s scale-to-fit, so it nests just as flush without ever overflowing.
 *  An interior [AnchorShape.Pill] is full [TimelineNode]'s `FLUSH_ANCHOR_SIZE` wide (matching a cap
 *  anchor's own width, per spec) but only `2 × contentRadiusPx` tall by default — a plain
 *  `drawRoundRect` capsule whose height grows to meet that width and whose corner radius shrinks
 *  toward [SQUARE_CORNER_FRACTION] of that width as [AnchorShape.Pill.pressProgress] goes 0→1, so an
 *  unpressed/non-clickable Pill (always `pressProgress() == 0`) renders the exact same full capsule
 *  either way. */
private fun DrawScope.drawSocket(anchor: PlacedAnchor, scratch: android.graphics.Path) {
    val d = anchor.descriptor
    when (val shape = d.shape) {
        AnchorShape.Circle -> drawCircle(d.accentColor, radius = d.contentRadiusPx, center = Offset(anchor.cx, anchor.cy))
        is AnchorShape.Pill -> {
            val pillWidth = FLUSH_ANCHOR_SIZE.toPx()
            val pressed = shape.pressProgress().coerceIn(0f, 1f)
            val pillHeight = lerp(d.contentRadiusPx * 2, pillWidth, pressed)
            val cornerRadius = lerp(pillHeight / 2f, pillWidth * SQUARE_CORNER_FRACTION, pressed)
            drawRoundRect(
                color = d.accentColor,
                topLeft = Offset(anchor.cx - pillWidth / 2f, anchor.cy - pillHeight / 2f),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(cornerRadius),
            )
        }
        is AnchorShape.Polygon -> drawPath(
            buildPolygonPath(shape.polygon, anchor.cx, anchor.cy, d.contentRadiusPx * 2, scratch),
            d.accentColor,
        )
        is AnchorShape.MorphShape -> drawPath(
            buildMorphPath(shape.morph, shape.progress(), anchor.cx, anchor.cy, d.contentRadiusPx * 2, scratch),
            d.accentColor,
        )
    }
}

/** Wraps [cappedRoundRect] (the pure geometry, unit-tested in `TimelineTrackGeometryTest.kt`) into
 *  an actual `Path` for painting — `Path()` construction is native-backed on Android and needs
 *  Robolectric to run in a JVM test, which is why this thin wrapper stays outside the pure
 *  `TimelineTrackGeometry.kt` file. */
private fun cappedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    roundTop: Boolean,
    roundBottom: Boolean,
    corner: CornerRadius,
): Path = Path().apply { addRoundRect(cappedRoundRect(left, top, right, bottom, roundTop, roundBottom, corner)) }
