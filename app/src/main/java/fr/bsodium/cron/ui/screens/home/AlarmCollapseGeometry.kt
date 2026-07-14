package fr.bsodium.cron.ui.screens.home

/**
 * Pure decision/geometry logic for [HomeContent]'s `collapseState` and [AlarmCollapseEffects]'
 * magnetic snap, deliberately decoupled from `LazyListState`/`LazyListLayoutInfo` so it's
 * unit-testable in plain JUnit — see `AlarmCollapseGeometryTest.kt`.
 */

/** Minimal stand-in for one `LazyListLayoutInfo.visibleItemsInfo` entry — the real
 *  `LazyListItemInfo` is an interface with no public constructor, so tests build this instead. */
internal data class VisibleItemSnapshot(val key: Any, val offset: Int)

private const val SNAP_EPSILON = 0.001f

/** Resolves the sticky alarm card's collapse geometry from the current scroll layout. Prefers the
 *  "alarm-spacer" row's own on-screen position when it's visible; falls back to
 *  [firstVisibleItemIndex] otherwise, since a fast fling can leave `visibleItemsInfo` without that
 *  entry for a frame even though the list hasn't genuinely scrolled past it — the `<= 1` fallback
 *  (index 1 is always "alarm-spacer", right after "greeting" at 0) resolves that as still-expanded
 *  rather than forcing a collapse, which used to be the discontinuity that got this state stuck. */
internal fun computeAlarmCollapse(
    visibleItems: List<VisibleItemSnapshot>,
    viewportStartOffset: Int,
    firstVisibleItemIndex: Int,
    collapseSafeTopPx: Int,
    collapseRangePx: Float,
    collapseFadePx: Float,
): AlarmCollapse {
    val screenTop = visibleItems.firstOrNull { it.key == "alarm-spacer" }
        ?.let { it.offset - viewportStartOffset }
    // Keyed off the greeting row's position, not the alarm-spacer's, so occlusion engages as soon as scrolling starts rather than only once the card nears collapse.
    val greetingTop = visibleItems.firstOrNull { it.key == "greeting" }
        ?.let { it.offset - viewportStartOffset }
    val gradientAlpha = if (greetingTop == null) {
        1f
    } else {
        ((collapseSafeTopPx - greetingTop).coerceAtLeast(0).toFloat() / collapseFadePx).coerceIn(0f, 1f)
    }
    return when {
        screenTop != null -> {
            val distance = (collapseSafeTopPx - screenTop).coerceAtLeast(0).toFloat()
            AlarmCollapse(
                top = maxOf(collapseSafeTopPx, screenTop),
                gradientAlpha = gradientAlpha,
                fraction = (distance / collapseRangePx).coerceIn(0f, 1f),
                distancePx = distance,
            )
        }
        firstVisibleItemIndex <= 1 -> AlarmCollapse(top = collapseSafeTopPx, gradientAlpha = gradientAlpha, fraction = 0f, distancePx = 0f)
        else -> AlarmCollapse(top = collapseSafeTopPx, gradientAlpha = gradientAlpha, fraction = 1f, distancePx = collapseRangePx)
    }
}

/** Which magnetic-snap action [AlarmCollapseEffects] should take once a scroll comes to rest —
 *  [NoSnap] when already at a stable end, [ScrollByThenMaybeTop] to finish collapsing (falling back
 *  to the very top if the content below is too short to reach a full collapse), [ScrollToTop] to
 *  expand back to the greeting. */
internal enum class SnapTarget { NoSnap, ScrollByThenMaybeTop, ScrollToTop }

/** Pure replacement for [AlarmCollapseEffects]' settle-time branch — collapsing only commits when
 *  there's room to scroll further (content below is tall enough), expanding is always reachable via
 *  the off-screen greeting. */
internal fun resolveSnapTarget(fraction: Float, scrollingDown: Boolean, canScrollForward: Boolean): SnapTarget = when {
    fraction <= SNAP_EPSILON || fraction >= 1f - SNAP_EPSILON -> SnapTarget.NoSnap
    scrollingDown && canScrollForward -> SnapTarget.ScrollByThenMaybeTop
    else -> SnapTarget.ScrollToTop
}
