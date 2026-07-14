@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import fr.bsodium.cron.ui.components.bleedHorizontally
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

internal val NODE_GUTTER = 48.dp

/** Custom alignment line (Round 29) declared by the Latest hero's headline specifically — not a
 *  built-in text baseline, since the hero's title also contains a kicker caption above the headline,
 *  and letting Compose's automatic baseline propagation bubble up through that nesting risks merging
 *  the wrong descendant's line. Only the headline itself ever declares this one (see
 *  [Modifier.declareCenterAs]), so there's never more than one candidate value to merge — the merge
 *  function is unreachable in practice. [TimelineNode]'s anchor and trailing-arrow Boxes align their
 *  own centers to whatever value the title's descendant declares, via `RowScope.alignBy`, replacing
 *  an earlier hand-computed padding offset that required guessing the kicker's exact rendered height
 *  and didn't hold up on-device ("the alignment fix didn't work at all"). */
internal val HeroHeadlineCenter = HorizontalAlignmentLine(merger = { old, _ -> old })

/** Declares [line]'s value — for a `RowScope.alignBy` ancestor further up the tree — as exactly this
 *  composable's own vertical center. Lets a non-Text composable (a Box, a Symbol's Canvas) act as the
 *  thing another sibling aligns to, or as the thing that reads another descendant's declared line
 *  (see [HeroHeadlineCenter]'s own use on the hero headline). */
internal fun Modifier.declareCenterAs(line: HorizontalAlignmentLine): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height, mapOf(line to placeable.height / 2)) {
        placeable.place(0, 0)
    }
}

/** Carved-socket tube width. Grown across rounds so every anchor fits with real padding and an
 *  end-cap anchor can nest a meaningfully-sized concentric disc — see the end-cap radius logic in
 *  [TimelineTrackOverlay]. Internal so the overlay and [LatestAnchor] can reference it. */
internal val TRACK_WIDTH = Spacing.xxxxl

/** Margin left uncarved between a cap anchor's own content disc and the track's edge — just the
 *  track's own calmer color showing through around the anchor. Doubled from 2dp to 4dp (Round 18) so
 *  a non-circular cap silhouette (Flower/Diamond) has real breathing room instead of nesting flush
 *  against the stadium's rounded end, which read as cramped/jagged for anything but a plain circle.
 *  Internal so [TimelineTrackOverlay] sizes the end-cap disc against it. */
internal val CAP_ANCHOR_PADDING = Spacing.xs

/** The one content diameter every cap anchor's disc/socket uses — sized so `contentRadius +
 *  CAP_ANCHOR_PADDING == halfTrack` exactly, i.e. flush to the track's rounded end with
 *  [CAP_ANCHOR_PADDING] of rim showing on every side. Deriving all four anchor types
 *  (Plain/Icon/Loader/Latest) from this single formula makes the "no overflow past the track edge"
 *  invariant structural: a future change to [TRACK_WIDTH] or [CAP_ANCHOR_PADDING] can't reintroduce
 *  overflow — the stadium's own cap radius stays `halfTrack` regardless, so the anchor simply nests
 *  with a wider or narrower rim inside that same cap. Internal so [LatestAnchor] shares it. */
internal val FLUSH_ANCHOR_SIZE = TRACK_WIDTH - CAP_ANCHOR_PADDING * 2

/** Interior (non-cap) anchors shrink to this diameter, leaving a wider rim of bare track around them
 *  than a cap anchor — the Google-Maps transit read where a line's terminus/interchange station is a
 *  bigger dot than the stops between. 24dp against [TRACK_WIDTH] = 40dp is an 8dp rim per side, clearly
 *  smaller/nested versus [FLUSH_ANCHOR_SIZE]'s 4dp, not a rounding difference. The glyph itself
 *  ([ICON_GLYPH_SIZE]) doesn't shrink — it just occupies more of its own smaller disc. */
internal val INTERIOR_ANCHOR_SIZE = 24.dp
private val ICON_GLYPH_SIZE = 18.dp

internal sealed interface TimelineAnchor {
    data object Plain : TimelineAnchor
    data class Icon(
        val symbol: MaterialSymbol,
        val tint: Color? = null,
        val containerColor: Color? = null,
        val valence: TimelineValence = TimelineValence.Neutral,
    ) : TimelineAnchor

    data object Loader : TimelineAnchor

    /** The single latest AI run — a morphing Material shape instead of a plain circle, distinguished
     *  by its animated silhouette. It shares the regular AI run's `primaryContainer` fill (the morph,
     *  not a bolder color, is what sets it apart). Only nests at the cap size/shape when it's
     *  genuinely a segment cap — `isLatest` does NOT guarantee `isSegmentTop`: an `Event` timestamped
     *  more recently than the latest AI run (e.g. an alarm dismissed after last night's plan ran)
     *  sorts above it, making the "latest" run an interior row despite the name. `TimelineNode`
     *  normalizes it to a plain interior `Icon` in that case — see `effectiveAnchor`. */
    data class Latest(val symbol: MaterialSymbol) : TimelineAnchor
}

/** Every anchor's full outer footprint is its own content disc plus [CAP_ANCHOR_PADDING] on each
 *  side — the Row's layout height, and the box whose measured center the overlay carves its socket
 *  at. The footprint always reserves the full [TRACK_WIDTH] (== [FLUSH_ANCHOR_SIZE] +
 *  [CAP_ANCHOR_PADDING] × 2 by construction) so the Row height doesn't jump as an anchor
 *  shrinks/grows between interior and cap; only the drawn content disc varies. */
private fun TimelineAnchor.footprintDiameter(): Dp = FLUSH_ANCHOR_SIZE + CAP_ANCHOR_PADDING * 2

/** A cap anchor (segment top/bottom, or a sleep sub-track's own onset/wake) nests flush against the
 *  track's rounded end at [FLUSH_ANCHOR_SIZE]; every interior anchor shrinks to [INTERIOR_ANCHOR_SIZE].
 *  Same two-size rule for all four anchor types — no per-type exception. */
private fun contentDiameterFor(atCap: Boolean): Dp = if (atCap) FLUSH_ANCHOR_SIZE else INTERIOR_ANCHOR_SIZE

/** The accent color the overlay fills this anchor's socket with — the visible disc for
 *  [TimelineAnchor.Plain]/[TimelineAnchor.Icon]; the same color [TimelineAnchor.Loader]/
 *  [TimelineAnchor.Latest] self-paint their content in on top of, so there's no seam. Every branch
 *  now resolves through `trackAccentColor` (Round 27 — see its KDoc in `SessionTimeline.kt`): a
 *  cap/interior [TimelineAnchor.Icon] or [TimelineAnchor.Plain] without its own `containerColor`
 *  falls back to it directly, and [TimelineAnchor.Loader]/[TimelineAnchor.Latest] (previously a
 *  flat hardcoded `primaryContainer` regardless of track) now use it too, so the spinner/hero morph
 *  never clashes with the sleep track the way a fixed `primary`-family fill could. */
@Composable
private fun TimelineAnchor.socketAccentColor(isAsleep: Boolean): Color = when (this) {
    TimelineAnchor.Plain -> trackAccentColor(isAsleep)
    is TimelineAnchor.Icon -> containerColor ?: trackAccentColor(isAsleep)
    TimelineAnchor.Loader -> trackAccentColor(isAsleep)
    is TimelineAnchor.Latest -> trackAccentColor(isAsleep)
}

@Composable
internal fun TimelineNode(
    id: String,
    registry: TimelineTrackRegistry,
    anchor: TimelineAnchor,
    isSegmentTop: Boolean,
    isSegmentBottom: Boolean,
    isAsleepAbove: Boolean,
    isAsleepBelow: Boolean,
    // Whether this row is genuinely new since the ViewModel's last emission — gates the Latest anchor's Circle→Cookie9Sided arrival morph, alongside registry.markEnteredOnce (a defensive second guard: isNewlyArrived alone doesn't rule out a row disposing/recomposing via rapid scroll churn within the same "new" window).
    isNewlyArrived: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = Spacing.md,
    title: @Composable () -> Unit,
    status: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    /** A cap anchor keeps the flush size; an interior one shrinks. Latest is always a segment top,
     *  so it evaluates true with no special-casing (Google-Maps "first station is bigger"). */
    val atCap = isSegmentTop || isSegmentBottom ||
        (!isAsleepAbove && isAsleepBelow) || (isAsleepAbove && !isAsleepBelow)
    /** An interior Icon anchor drops its semantic accent/valence entirely — shape and color both
     *  fall back to the track-matched treatment below, rather than threading an `atCap` check
     *  separately through the shape `when`, the socket color, and the glyph tint;
     *  `socketAccentColor`'s `containerColor ?: trackAccentColor(...)` fallback just does the right
     *  thing without knowing about `atCap` itself. A non-cap Latest anchor (see its own KDoc —
     *  `isLatest` doesn't guarantee `isSegmentTop`) is converted into a plain interior Icon for the
     *  same reason: "only cap anchors get custom shapes" applies to the morph as much as the
     *  valence polygons, so it's normalized through the identical Icon path. */
    val effectiveAnchor = when {
        anchor is TimelineAnchor.Icon && !atCap -> anchor.copy(
            tint = trackOnAccentColor(isAsleepAbove),
            containerColor = null,
            valence = TimelineValence.Neutral,
        )
        anchor is TimelineAnchor.Latest && !atCap -> TimelineAnchor.Icon(
            symbol = anchor.symbol,
            tint = trackOnAccentColor(isAsleepAbove),
            containerColor = null,
            valence = TimelineValence.Neutral,
        )
        else -> anchor
    }
    val anchorDiam = effectiveAnchor.footprintDiameter()
    val titleSpacer = Spacing.md
    val accentColor = effectiveAnchor.socketAccentColor(isAsleep = isAsleepAbove)
    val targetRadiusPx = with(LocalDensity.current) { contentDiameterFor(atCap).toPx() / 2 }
    /** Animates the size change so a row that stops being a cap (superseded by an insertion)
     *  visibly shrinks instead of snapping; the *animated* value is what gets reported into the
     *  registry. */
    val contentRadiusPx by animateFloatAsState(
        targetValue = targetRadiusPx,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "anchor-radius",
    )

    /** Hoisted above the shape derivation (not down by the Surface below) so a clickable row's
     *  shape itself can react to press, rather than a whole-row scale transform that visually
     *  decouples from this overlay-drawn socket. Harmless to compute even when `onClick` is null —
     *  `pressed` just never turns true without a `Surface.onClick` feeding these events. */
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    /** `fastSpatialSpec`, checked against the actual token values
     *  (`androidx.compose.material3.tokens.ExpressiveMotionTokens`): fast is dampingRatio=0.6 /
     *  stiffness=800 vs slow's 0.8 / 200 — genuinely more underdamped (more overshoot) as well as
     *  quicker to settle, i.e. "faster and bouncier." */
    val pressMorphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "timeline-node-press-morph",
    )

    /** The Latest morph's progress is hoisted here and exposed to the overlay via the reported
     *  `AnchorShape.MorphShape` so the socket carve animates in lockstep with the row's arrival.
     *  Gated below on [isNewlyArrived] (survives navigation, unlike a bare composition check) *and*
     *  `registry.markEnteredOnce` (a defensive second guard against rapid scroll-churn
     *  disposing/recomposing this row within the same "new" window) — an unconditional effect here
     *  would replay this morph on every fresh mount regardless of whether the row is actually new
     *  data. */
    val latestMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    /** Unconditional (not short-circuited by the `&&` below) so this composable call happens the
     *  same way on every recomposition regardless of the other flags — Compose's slot table expects
     *  the same sequence of composable calls call-over-call, not one gated behind a boolean that can
     *  itself change between recompositions. */
    val enteredOnce = remember(id) { registry.markEnteredOnce(id) }
    val playsArrival = isNewlyArrived && atCap && anchor is TimelineAnchor.Latest && enteredOnce
    val latestProgress = remember(id) { Animatable(if (playsArrival) 0f else 1f) }
    if (anchor is TimelineAnchor.Latest && atCap) {
        val arriveSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
        LaunchedEffect(id) {
            if (playsArrival) latestProgress.animateTo(1f, arriveSpec) else latestProgress.snapTo(1f)
        }
    }
    /** A clickable Neutral cap's plain Circle morphs toward this distinct silhouette while
     *  pressed — Cookie6Sided rather than Latest's own Cookie9Sided so the two "selected" cues stay
     *  visually distinguishable. At `pressMorphProgress == 0` this renders pixel-identical to a
     *  plain Circle, so it's safe to use unconditionally for every clickable cap. */
    val pressCapMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided) }
    val isClickable = onClick != null
    /** A clickable interior Pill needs no Morph of its own — its press target is a plain
     *  `drawRoundRect` whose corner radius and height both animate off `pressMorphProgress`
     *  directly in `TimelineTrackOverlay.kt`; see [AnchorShape.Pill]'s KDoc for why a Morph-based
     *  approach here produces a broken "butterfly" mid-press silhouette. */
    val shape = remember(effectiveAnchor, latestMorph, pressCapMorph, atCap, isClickable) {
        when (effectiveAnchor) {
            is TimelineAnchor.Latest -> AnchorShape.MorphShape(latestMorph) { latestProgress.value }
            // A non-cap Icon is already normalized to Neutral valence above, so the Neutral branch is reached by every interior anchor — gated on atCap since a genuinely Neutral CAP anchor must stay a circle, only an interior one becomes a pill.
            is TimelineAnchor.Icon -> when (effectiveAnchor.valence) {
                TimelineValence.Positive -> AnchorShape.Polygon(MaterialShapes.Flower)
                // Triangle's centroid sits below its bounding-box center, so a glyph centered on the bbox reads as sitting too high; Diamond (a square rotated 45°) is symmetric on both axes, so bbox-centering is also visual-centering — still reads sharper/"less positive" than Flower/Circle.
                TimelineValence.Negative -> AnchorShape.Polygon(MaterialShapes.Diamond)
                TimelineValence.Neutral -> when {
                    atCap && isClickable -> AnchorShape.MorphShape(pressCapMorph) { pressMorphProgress }
                    atCap -> AnchorShape.Circle
                    isClickable -> AnchorShape.Pill(pressProgress = { pressMorphProgress })
                    else -> AnchorShape.Pill()
                }
            }
            TimelineAnchor.Plain -> when {
                atCap && isClickable -> AnchorShape.MorphShape(pressCapMorph) { pressMorphProgress }
                atCap -> AnchorShape.Circle
                isClickable -> AnchorShape.Pill(pressProgress = { pressMorphProgress })
                else -> AnchorShape.Pill()
            }
            TimelineAnchor.Loader -> AnchorShape.Circle
        }
    }

    SideEffect {
        registry.setDescriptor(
            id,
            AnchorDescriptor(
                contentRadiusPx = contentRadiusPx,
                shape = shape,
                accentColor = accentColor,
                isSegmentTop = isSegmentTop,
                isSegmentBottom = isSegmentBottom,
                asleepAbove = isAsleepAbove,
                asleepBelow = isAsleepBelow,
                isLatest = anchor is TimelineAnchor.Latest,
            ),
        )
    }
    DisposableEffect(id) { onDispose { registry.remove(id) } }

    val inner = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = verticalPadding, end = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    // The Latest anchor's title can wrap past the anchor's own height, unlike every other row — align to HeroHeadlineCenter rather than the row's whole-height center so it lines up with the headline specifically; alignBy has Compose measure the real value instead of a hand-computed offset.
                    modifier = Modifier
                        .width(NODE_GUTTER)
                        .let { if (anchor is TimelineAnchor.Latest) it.alignBy { measured -> measured.measuredHeight / 2 } else it },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(anchorDiam)
                            // Still reported for every row: the overlay only trusts this handle's Y for the Latest row now (Phase 7, docs/color-roles.md — every other row's Y comes from LazyListState.layoutInfo instead), but its X is scroll-invariant (every anchor centers in the same fixed-width gutter) and stays the shared source for trackCenterX, including when no Latest row happens to be visible.
                            .onGloballyPositioned { coords -> registry.setPosition(id, coords) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnchorContent(effectiveAnchor, isAsleep = isAsleepAbove)
                    }
                }
                Spacer(Modifier.width(titleSpacer))
                Box(
                    modifier = Modifier.weight(1f).wrapContentHeight()
                        .let { if (anchor is TimelineAnchor.Latest) it.alignBy(HeroHeadlineCenter) else it },
                ) { title() }
                if (status != null) {
                    Box(
                        // Same HeroHeadlineCenter alignment as the anchor gutter above, so the trailing arrow (the Latest row's only status content while latest) lines up with the headline too, not the row's whole-height center.
                        modifier = Modifier.let {
                            if (anchor is TimelineAnchor.Latest) it.alignBy { measured -> measured.measuredHeight / 2 } else it
                        },
                    ) { status() }
                }
            }
            // Text in title()/status()/content() always passes an explicit `style`, which bypasses LocalTextStyle entirely — font-padding leading fixes belong on each style itself (CronTypography's timeline roles are built on `tight` directly in Type.kt; ad hoc bodyMedium uses merge TightTextStyle locally, see EventNode.kt/SessionTimeline.kt), not a CompositionLocalProvider here.
            if (content != null) {
                Box(
                    modifier = Modifier.padding(
                        start = NODE_GUTTER + titleSpacer,
                        end = Spacing.md,
                        top = Spacing.xxs,
                    ),
                ) { content() }
            }
            Spacer(Modifier.height(verticalPadding))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                interactionSource = interactionSource,
                // Bleeds the tap/ripple target to the true screen edges past HomeContent's LazyColumn contentPadding (compensating padding below keeps inner() in place); no scale/graphicsLayer here — press feedback lives entirely in the anchor's own shape morph, which the overlay draws at its true registered position.
                modifier = Modifier
                    .fillMaxWidth()
                    .bleedHorizontally(Spacing.xl),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.padding(horizontal = Spacing.xl)) { inner() }
            }
        } else {
            inner()
        }
    }
}

/** Draws only the anchor's own foreground — the glyph or loading indicator — on top of the socket
 *  the overlay already filled behind it. Plain has no foreground: the overlay's accent disc *is* the
 *  dot. Transparent background throughout; the container disc is the overlay's job now. */
@Composable
private fun AnchorContent(anchor: TimelineAnchor, isAsleep: Boolean) {
    when (anchor) {
        TimelineAnchor.Plain -> Unit
        is TimelineAnchor.Icon -> Symbol(
            symbol = anchor.symbol,
            contentDescription = null,
            tint = anchor.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            size = ICON_GLYPH_SIZE,
        )
        TimelineAnchor.Loader -> ContainedLoadingIndicator(
            modifier = Modifier.size(FLUSH_ANCHOR_SIZE),
            containerShape = CircleShape,
            containerColor = trackAccentColor(isAsleep),
            indicatorColor = trackOnAccentColor(isAsleep),
        )
        is TimelineAnchor.Latest -> LatestAnchor(symbol = anchor.symbol, isAsleep = isAsleep)
    }
}
