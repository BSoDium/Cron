package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates

/** The only two silhouettes an anchor may carve into the track. [Circle] is every cap anchor
 *  (segment top/bottom, or a sleep sub-track's own onset/wake) regardless of anchor type or
 *  semantic meaning; [Pill] is every INTERIOR (non-cap) anchor — a horizontal capsule, full
 *  [TimelineNode]'s `FLUSH_ANCHOR_SIZE` wide but only its own (animated) content diameter tall,
 *  drawn as a plain `drawRoundRect`. Kept a sealed set so a new shape is an exhaustive-`when`
 *  compile error, not a silent fallthrough — see docs/expressive.md. */
sealed interface AnchorShape {
    data object Circle : AnchorShape
    data object Pill : AnchorShape
}

/** Everything [TimelineTrackOverlay] needs about one anchor except its live position (kept in a
 *  separate map so a per-frame position update doesn't churn this). Reported by [TimelineNode] on
 *  (re)composition. `asleepAbove`/`asleepBelow` describe the track gaps immediately above/below the
 *  anchor; the overlay derives sleep-pill caps from them. [isLatest] mirrors the exact same
 *  `anchor is TimelineAnchor.Latest` check `TimelineNode` itself uses to decide whether this row's
 *  anchor is laid out via `alignBy(HeroHeadlineCenter)` (a variable-height hero headline, genuinely
 *  needs live measurement) rather than plain `Row`-centering (Phase 7, docs/color-roles.md — see
 *  [TimelineTrackOverlay]'s KDoc for why only that one case still needs [AnchorPosition]).
 *
 *  [outgoingShape]/[shapeCrossfadeFraction] (Phase 11, docs/color-roles.md) cover a shape identity
 *  change (e.g. a cap-losing row's socket going `Circle` → `Pill`) that [AnchorShape] itself has no
 *  interpolation for — see [advanceShapeCrossfadeState]. Both default so every existing call site
 *  (screenshot tests, `EventNode`) is unaffected: [outgoingShape] `null` means "nothing to crossfade,
 *  draw [shape] alone," and the overlay only reads [shapeCrossfadeFraction] when [outgoingShape] is
 *  non-null. */
data class AnchorDescriptor(
    val contentRadiusPx: Float,
    val shape: AnchorShape,
    val accentColor: Color,
    val isSegmentTop: Boolean,
    val isSegmentBottom: Boolean,
    val asleepAbove: Boolean,
    val asleepBelow: Boolean,
    val isLatest: Boolean,
    val outgoingShape: AnchorShape? = null,
    val shapeCrossfadeFraction: Float = 1f,
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

    fun setPosition(id: String, coordinates: LayoutCoordinates) {
        positions[id] = AnchorPosition(coordinates)
    }

    fun setDescriptor(id: String, descriptor: AnchorDescriptor) {
        if (descriptors[id] != descriptor) descriptors[id] = descriptor
    }

    fun remove(id: String) {
        positions.remove(id)
        descriptors.remove(id)
    }
}

@Composable
internal fun rememberTimelineTrackRegistry(): TimelineTrackRegistry = remember { TimelineTrackRegistry() }
