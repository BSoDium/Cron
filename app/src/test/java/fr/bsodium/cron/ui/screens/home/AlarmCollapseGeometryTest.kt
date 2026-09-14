package fr.bsodium.cron.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain-JUnit coverage of [AlarmCollapseGeometry.kt]'s pure decision logic — no Robolectric, no
 * device, runs in milliseconds.
 */
class AlarmCollapseGeometryTest {

    private val safeTop = 100
    private val range = 200f
    private val fade = 80f

    private fun collapse(
        visibleItems: List<VisibleItemSnapshot>,
        viewportStartOffset: Int = 0,
        firstVisibleItemIndex: Int = 0,
    ) = computeAlarmCollapse(
        visibleItems = visibleItems,
        viewportStartOffset = viewportStartOffset,
        firstVisibleItemIndex = firstVisibleItemIndex,
        collapseSafeTopPx = safeTop,
        collapseRangePx = range,
        collapseFadePx = fade,
    )

    @Test
    fun computeAlarmCollapse_noRowsFound_nearTop_fallsBackToExpanded() {
        val result = collapse(visibleItems = emptyList(), firstVisibleItemIndex = 1)
        assertEquals(0f, result.fraction, 0f)
        assertEquals(0f, result.distancePx, 0f)
        assertEquals(safeTop, result.top)
    }

    @Test
    fun computeAlarmCollapse_alarmSpacerMissingButStillAtFirstIndex_fallsBackToExpanded_notStuckCollapsed() {
        // The exact fix from fdecb40: a fling can leave "alarm-spacer" out of visibleItemsInfo for a
        // frame even while the list genuinely hasn't scrolled past it — this must resolve as expanded,
        // not collapsed, or the card gets stuck.
        val result = collapse(
            visibleItems = listOf(VisibleItemSnapshot(key = "some-other-row", offset = 500)),
            firstVisibleItemIndex = 0,
        )
        assertEquals(0f, result.fraction, 0f)
    }

    @Test
    fun computeAlarmCollapse_noRowsFound_scrolledPast_fallsBackToFullyCollapsed() {
        val result = collapse(visibleItems = emptyList(), firstVisibleItemIndex = 2)
        assertEquals(1f, result.fraction, 0f)
        assertEquals(range, result.distancePx, 0f)
    }

    @Test
    fun computeAlarmCollapse_screenTopAtOrAboveSafeTop_fractionZero() {
        val result = collapse(visibleItems = listOf(VisibleItemSnapshot(key = "alarm-spacer", offset = 150)))
        assertEquals(0f, result.fraction, 0f)
        assertEquals(0f, result.distancePx, 0f)
        assertEquals(150, result.top)
    }

    @Test
    fun computeAlarmCollapse_screenTopMidRange_interpolates() {
        // distance = safeTop - screenTop = 100 - 0 = 100; fraction = 100 / 200 = 0.5
        val result = collapse(visibleItems = listOf(VisibleItemSnapshot(key = "alarm-spacer", offset = 0)))
        assertEquals(0.5f, result.fraction, 0f)
        assertEquals(100f, result.distancePx, 0f)
        assertEquals(safeTop, result.top)
    }

    @Test
    fun computeAlarmCollapse_screenTopExactlyAtRange_fractionOne() {
        val result = collapse(visibleItems = listOf(VisibleItemSnapshot(key = "alarm-spacer", offset = safeTop - range.toInt())))
        assertEquals(1f, result.fraction, 0f)
    }

    @Test
    fun computeAlarmCollapse_screenTopBeyondRange_fractionCoercedToOne() {
        val result = collapse(visibleItems = listOf(VisibleItemSnapshot(key = "alarm-spacer", offset = safeTop - range.toInt() - 500)))
        assertEquals(1f, result.fraction, 0f)
    }

    @Test
    fun computeAlarmCollapse_viewportStartOffsetIsSubtracted() {
        val result = collapse(
            visibleItems = listOf(VisibleItemSnapshot(key = "alarm-spacer", offset = 250)),
            viewportStartOffset = 100,
        )
        // screenTop = 250 - 100 = 150 >= safeTop(100) -> fraction 0
        assertEquals(0f, result.fraction, 0f)
    }

    @Test
    fun computeAlarmCollapse_greetingMissing_gradientAlphaFullyOpaque() {
        val result = collapse(visibleItems = listOf(VisibleItemSnapshot(key = "alarm-spacer", offset = 0)))
        assertEquals(1f, result.gradientAlpha, 0f)
    }

    @Test
    fun computeAlarmCollapse_greetingAtOrAboveSafeTop_gradientAlphaZero() {
        val result = collapse(
            visibleItems = listOf(
                VisibleItemSnapshot(key = "greeting", offset = safeTop + 50),
                VisibleItemSnapshot(key = "alarm-spacer", offset = 0),
            ),
        )
        assertEquals(0f, result.gradientAlpha, 0f)
    }

    @Test
    fun computeAlarmCollapse_greetingFullyPastFade_gradientAlphaOne() {
        val result = collapse(
            visibleItems = listOf(
                VisibleItemSnapshot(key = "greeting", offset = safeTop - fade.toInt() - 500),
                VisibleItemSnapshot(key = "alarm-spacer", offset = 0),
            ),
        )
        assertEquals(1f, result.gradientAlpha, 0f)
    }

    @Test
    fun computeAlarmCollapse_greetingMidFade_gradientAlphaInterpolates() {
        // distance above safeTop = safeTop - (safeTop - fade/2) = fade/2 -> alpha = 0.5
        val result = collapse(
            visibleItems = listOf(
                VisibleItemSnapshot(key = "greeting", offset = safeTop - (fade / 2).toInt()),
                VisibleItemSnapshot(key = "alarm-spacer", offset = 0),
            ),
        )
        assertEquals(0.5f, result.gradientAlpha, 0f)
    }

    @Test
    fun resolveSnapTarget_atFullyExpanded_noSnap() {
        assertEquals(SnapTarget.NoSnap, resolveSnapTarget(fraction = 0f, scrollingDown = true, canScrollForward = true))
    }

    @Test
    fun resolveSnapTarget_atFullyCollapsed_noSnap() {
        assertEquals(SnapTarget.NoSnap, resolveSnapTarget(fraction = 1f, scrollingDown = false, canScrollForward = false))
    }

    @Test
    fun resolveSnapTarget_withinEpsilonOfExpanded_noSnap() {
        assertEquals(SnapTarget.NoSnap, resolveSnapTarget(fraction = 0.0005f, scrollingDown = true, canScrollForward = true))
    }

    @Test
    fun resolveSnapTarget_withinEpsilonOfCollapsed_noSnap() {
        assertEquals(SnapTarget.NoSnap, resolveSnapTarget(fraction = 0.9995f, scrollingDown = false, canScrollForward = true))
    }

    @Test
    fun resolveSnapTarget_midRangeScrollingDownWithRoom_scrollsByThenMaybeTop() {
        assertEquals(
            SnapTarget.ScrollByThenMaybeTop,
            resolveSnapTarget(fraction = 0.5f, scrollingDown = true, canScrollForward = true),
        )
    }

    @Test
    fun resolveSnapTarget_midRangeScrollingDownButNoRoom_fallsBackToScrollToTop() {
        assertEquals(
            SnapTarget.ScrollToTop,
            resolveSnapTarget(fraction = 0.5f, scrollingDown = true, canScrollForward = false),
        )
    }

    @Test
    fun resolveSnapTarget_midRangeScrollingUp_scrollsToTop() {
        assertEquals(
            SnapTarget.ScrollToTop,
            resolveSnapTarget(fraction = 0.5f, scrollingDown = false, canScrollForward = true),
        )
    }
}
