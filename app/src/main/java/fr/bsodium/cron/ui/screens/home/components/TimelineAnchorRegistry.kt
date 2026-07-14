package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** The silhouette an anchor carves into the track. [Circle] covers every cap plain/loader anchor and
 *  a cap neutral-valence icon; [Pill] is every INTERIOR (non-cap) anchor's shape — a horizontal
 *  capsule, full [TimelineNode]'s `FLUSH_ANCHOR_SIZE` wide but only its own (animated) content
 *  diameter tall by default, drawn as a plain `drawRoundRect` whose corner radius and height both
 *  animate off [Pill.pressProgress]. Deliberately plain geometry, not a `Morph` — morphing between
 *  two shapes with mismatched vertex topology and non-uniformly rescaling the *mid-interpolation*
 *  outline to fit the growing rect produces a visibly broken bowtie/"butterfly" silhouette at
 *  in-between press values, which a full capsule shrinking its corner radius toward a rounded-square
 *  avoids entirely. [Polygon] is a static valence silhouette (Flower for positive, Diamond for
 *  negative) carved at a fixed shape, reserved for cap anchors only; [MorphShape] is a live
 *  Circle→(some Material shape) morph, whose [progress] is read live at draw time so the overlay's
 *  socket animates in lockstep with the driving spring — used both for the Latest run's
 *  Circle→Cookie9Sided arrival and a clickable cap anchor's Circle→Cookie6Sided press morph (a
 *  same-vertex-count, same-family pair, so it doesn't hit the Pill/Square mismatch above). Kept a
 *  sealed set so a new shape is an exhaustive-`when` compile error, not a silent fallthrough. */
sealed interface AnchorShape {
    data object Circle : AnchorShape

    /** [pressProgress] defaults to "unpressed, non-interactive" (always-0) — only a clickable
     *  [TimelineNode] row (`AiRunNode`) supplies a real one; `EventNode`'s rows are never clickable and
     *  stay at the default, rendering the plain capsule unconditionally. */
    data class Pill(val pressProgress: () -> Float = { 0f }) : AnchorShape
    data class Polygon(val polygon: RoundedPolygon) : AnchorShape
    data class MorphShape(val morph: Morph, val progress: () -> Float) : AnchorShape
}

/** Everything [TimelineTrackOverlay] needs about one anchor except its live position (kept in a
 *  separate map so a per-frame position update doesn't churn this). Reported by [TimelineNode] on
 *  (re)composition. `asleepAbove`/`asleepBelow` describe the track gaps immediately above/below the
 *  anchor; the overlay derives sleep-pill caps from them. [isLatest] mirrors the exact same
 *  `anchor is TimelineAnchor.Latest` check `TimelineNode` itself uses to decide whether this row's
 *  anchor is laid out via `alignBy(HeroHeadlineCenter)` (a variable-height hero headline, genuinely
 *  needs live measurement) rather than plain `Row`-centering (Phase 7, docs/color-roles.md — see
 *  [TimelineTrackOverlay]'s KDoc for why only that one case still needs [AnchorPosition]). */
data class AnchorDescriptor(
    val contentRadiusPx: Float,
    val shape: AnchorShape,
    val accentColor: Color,
    val isSegmentTop: Boolean,
    val isSegmentBottom: Boolean,
    val asleepAbove: Boolean,
    val asleepBelow: Boolean,
    val isLatest: Boolean,
)

/** Wraps a row's [LayoutCoordinates] handle — deliberately reference-identity-only (no custom
 *  `equals`/`hashCode`), so a fresh instance on every [TimelineTrackRegistry.setPosition] call always
 *  registers as a genuine change to the backing `mutableStateMapOf`, even on frames where Compose
 *  happens to hand back a coordinates object that's `==` to the previous one — that keeps the overlay
 *  redrawing every layout pass, matching the zero-lag design [TimelineTrackOverlay] relies on for
 *  ordinary scrolling. See that file's KDoc (Round 35) for why the *value* itself is never cached: this
 *  wrapper only carries the *handle*, queried fresh at draw time via [LayoutCoordinates.positionInWindow].
 *  Since Phase 7 (docs/color-roles.md), only ever populated for the Latest row — every other anchor's
 *  position comes from `LazyListState.layoutInfo` instead, which has no per-child race to solve. */
class AnchorPosition(val coordinates: LayoutCoordinates)

/** Shared handle the rows write into and [TimelineTrackOverlay] reads. [positions] hold a live
 *  [LayoutCoordinates] handle per row (Round 35 — previously a cached `Offset` snapshot, which could go
 *  numerically stale mid-transition; see [TimelineTrackOverlay]'s KDoc), queried fresh at draw time, not
 *  a value computed once at registration; descriptors change only when the list does. Both keyed by the
 *  stable [TimelineItem.id]. */
@Stable
class TimelineTrackRegistry {
    val positions = mutableStateMapOf<String, AnchorPosition>()
    val descriptors = mutableStateMapOf<String, AnchorDescriptor>()

    /** Plain (non-State) set — deliberately NOT observed, just a side-table that survives a row
     *  being disposed/recomposed by scrolling off-screen and back, unlike a per-row `remember`. See
     *  [markEnteredOnce]. */
    private val enteredIds = mutableSetOf<String>()

    fun setPosition(id: String, coordinates: LayoutCoordinates) {
        positions[id] = AnchorPosition(coordinates)
    }

    fun setDescriptor(id: String, descriptor: AnchorDescriptor) {
        if (descriptors[id] != descriptor) descriptors[id] = descriptor
    }

    fun remove(id: String) {
        positions.remove(id)
        descriptors.remove(id)
        // Deliberately NOT removed from enteredIds — a row scrolled off-screen and disposed must not replay its entrance animation when it scrolls back into view and recomposes fresh.
    }

    /** Returns `true` the first time [id] is ever checked, `false` every time after — used to play a
     *  newly-arrived row's entrance animation exactly once per row identity for the lifetime of this
     *  registry (i.e. this screen instance), even though scrolling the row off-screen and back disposes
     *  and recomposes its composable, which would otherwise replay a per-row `remember`-held animation
     *  state every time. */
    fun markEnteredOnce(id: String): Boolean = enteredIds.add(id)
}

@Composable
internal fun rememberTimelineTrackRegistry(): TimelineTrackRegistry = remember { TimelineTrackRegistry() }
