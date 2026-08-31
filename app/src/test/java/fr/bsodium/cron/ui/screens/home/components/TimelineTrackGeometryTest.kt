package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit coverage of [TimelineTrackGeometry.kt]'s pure decision logic — no Robolectric, no
 * device, runs in milliseconds. Named regression tests pin down the exact multi-frame sequences
 * that caused the timeline-overflow bug across Rounds 37 and 38 (docs/color-roles.md), so a future
 * change that reintroduces either gap fails here instead of needing another live-device round.
 */
class TimelineTrackGeometryTest {

    private fun descriptor(
        isSegmentTop: Boolean = false,
        isSegmentBottom: Boolean = false,
        asleepAbove: Boolean = false,
        asleepBelow: Boolean = false,
        contentRadiusPx: Float = 10f,
    ) = AnchorDescriptor(
        contentRadiusPx = contentRadiusPx,
        shape = AnchorShape.Circle,
        accentColor = Color.Black,
        isSegmentTop = isSegmentTop,
        isSegmentBottom = isSegmentBottom,
        asleepAbove = asleepAbove,
        asleepBelow = asleepBelow,
        isLatest = false,
    )

    private fun anchor(
        id: String,
        cy: Float,
        cx: Float = 0f,
        isSegmentTop: Boolean = false,
        isSegmentBottom: Boolean = false,
        asleepAbove: Boolean = false,
        asleepBelow: Boolean = false,
        contentRadiusPx: Float = 10f,
    ) = PlacedAnchor(
        id,
        descriptor(isSegmentTop, isSegmentBottom, asleepAbove, asleepBelow, contentRadiusPx),
        cx,
        cy,
    )

    // ---- resolvePlacedAnchors ----

    @Test
    fun resolvePlacedAnchors_emptyInputs_returnsEmpty() {
        assertTrue(resolvePlacedAnchors(emptyMap(), emptyMap()).isEmpty())
    }

    @Test
    fun resolvePlacedAnchors_requiresBothDescriptorAndPosition() {
        val descriptors = mapOf("a" to descriptor(), "b" to descriptor())
        val positions = mapOf("a" to Offset(0f, 10f)) // "b" has no position — not yet placed.
        val result = resolvePlacedAnchors(descriptors, positions)
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun resolvePlacedAnchors_positionWithoutDescriptor_isExcluded() {
        val descriptors = emptyMap<String, AnchorDescriptor>()
        val positions = mapOf("a" to Offset(0f, 10f))
        assertTrue(resolvePlacedAnchors(descriptors, positions).isEmpty())
    }

    // ---- resolveTrackEnds ----

    @Test
    fun resolveTrackEnds_freshMount_emptyPlaced_keepsNullPrevious() {
        val result = resolveTrackEnds(emptyList(), TrackEnds(null, null))
        assertNull(result.topId)
        assertNull(result.bottomId)
    }

    @Test
    fun resolveTrackEnds_singleAnchor_claimsBothTopAndBottom() {
        val a = anchor("a", cy = 100f, isSegmentTop = true, isSegmentBottom = true)
        val result = resolveTrackEnds(listOf(a), TrackEnds(null, null))
        assertEquals("a", result.topId)
        assertEquals("a", result.bottomId)
    }

    /** Named regression test for the Round 37 bug (commit fdecb40's gap): a new top row's descriptor
     *  AND position can both be missing for a frame after it's prepended — the resolver must keep
     *  the outgoing anchor's id rather than ever resolving to null (which would flush the cap). */
    @Test
    fun resolveTrackEnds_insertAtTop_descriptorAndPositionBothMissing_holdsOldId() {
        // Frame A: "old" is the confirmed, settled top.
        val frameA = listOf(anchor("old", cy = 200f, isSegmentTop = true))
        val endsA = resolveTrackEnds(frameA, TrackEnds(null, null))
        assertEquals("old", endsA.topId)

        // Frame B: data says "new" is now the true top (SessionTimeline recomputed firstAnchorIndex),
        // so "old"'s descriptor no longer claims isSegmentTop — but "new" hasn't registered a
        // descriptor OR a position yet, so it's entirely absent from `placed`.
        val frameB = listOf(anchor("old", cy = 200f, isSegmentTop = false))
        val endsB = resolveTrackEnds(frameB, endsA)
        assertEquals("old", endsB.topId) // must NOT regress to null.

        // Frame C: "new" is now fully placed and claiming, and genuinely topmost by position.
        val frameC = listOf(
            anchor("new", cy = 100f, isSegmentTop = true),
            anchor("old", cy = 200f, isSegmentTop = false),
        )
        val endsC = resolveTrackEnds(frameC, endsB)
        assertEquals("new", endsC.topId)
    }

    /** Named regression test for the Round 38 bug found by this session's live debugging: an anchor
     *  can be *placed* (descriptor + a registered position) before that position has animated to its
     *  final, topmost spot — claiming isSegmentTop while still visually below the outgoing anchor. */
    @Test
    fun resolveTrackEnds_insertAtTop_placedButNotYetTopmostByPosition_holdsOldId() {
        val endsA = resolveTrackEnds(listOf(anchor("old", cy = 200f, isSegmentTop = true)), TrackEnds(null, null))
        assertEquals("old", endsA.topId)

        // "new" is placed (has a position) and its descriptor claims isSegmentTop — but its cy
        // (300f) is still BELOW "old"'s (200f), i.e. animateItem hasn't carried it to the top yet.
        val frameB = listOf(
            anchor("old", cy = 200f, isSegmentTop = false),
            anchor("new", cy = 300f, isSegmentTop = true),
        )
        val endsB = resolveTrackEnds(frameB, endsA)
        assertEquals("old", endsB.topId) // must NOT promote "new" while it's not yet topmost.

        // Position animation completes: "new" is now topmost by cy too.
        val frameC = listOf(
            anchor("new", cy = 100f, isSegmentTop = true),
            anchor("old", cy = 200f, isSegmentTop = false),
        )
        val endsC = resolveTrackEnds(frameC, endsB)
        assertEquals("new", endsC.topId)
    }

    @Test
    fun resolveTrackEnds_insertAtBottom_mirrorsTopBehavior() {
        val endsA = resolveTrackEnds(listOf(anchor("old", cy = 100f, isSegmentBottom = true)), TrackEnds(null, null))
        assertEquals("old", endsA.bottomId)

        // "new" placed, claiming, but not yet bottommost by position.
        val frameB = listOf(
            anchor("old", cy = 100f, isSegmentBottom = false),
            anchor("new", cy = 50f, isSegmentBottom = true),
        )
        val endsB = resolveTrackEnds(frameB, endsA)
        assertEquals("old", endsB.bottomId)

        val frameC = listOf(
            anchor("old", cy = 100f, isSegmentBottom = false),
            anchor("new", cy = 200f, isSegmentBottom = true),
        )
        val endsC = resolveTrackEnds(frameC, endsB)
        assertEquals("new", endsC.bottomId)
    }

    /** Remove via ordinary scroll disposal: the top row is gone from `placed` entirely (not merely
     *  unclaimed), and no other placed anchor claims isSegmentTop (that flag stays pinned to the
     *  absolute-first timeline item, which is now off-screen). The resolver must NOT get stuck
     *  rounding a stale id forever — segmentCapDecision handles the actual un-matching. */
    @Test
    fun resolveTrackEnds_scrollDisposal_previousIdNoLongerInPlaced() {
        val endsA = resolveTrackEnds(listOf(anchor("top", cy = 50f, isSegmentTop = true)), TrackEnds(null, null))
        assertEquals("top", endsA.topId)

        // "top" scrolled off and disposed; "next" is now the topmost placed anchor but its
        // descriptor doesn't claim isSegmentTop (that belongs to the still-off-screen "top").
        val frameB = listOf(anchor("next", cy = 60f, isSegmentTop = false))
        val endsB = resolveTrackEnds(frameB, endsA)
        assertEquals("top", endsB.topId) // remembered id persists...

        // ...but segmentCapDecision correctly stops rounding, since "top" isn't in `anchors` at all.
        val decision = segmentCapDecision(frameB, endsB, viewportHeight = 1000f, halfTrack = 20f)
        assertFalse(decision.roundTop)
        assertEquals(0f, decision.bgTop, 0f)
    }

    /** Same signature as ordinary scroll disposal — documents that the two are intentionally
     *  indistinguishable at this layer (both are "the confirmed anchor is no longer in `placed`"). */
    @Test
    fun resolveTrackEnds_genuineDataDeletion_sameDegradationAsScrollDisposal() {
        val endsA = resolveTrackEnds(listOf(anchor("deleted", cy = 50f, isSegmentTop = true)), TrackEnds(null, null))
        val frameB = listOf(anchor("survivor", cy = 60f, isSegmentTop = true))
        val endsB = resolveTrackEnds(frameB, endsA)
        // A genuinely new claim on a placed anchor DOES take over (unlike disposal, where nothing claims it).
        assertEquals("survivor", endsB.topId)
    }

    @Test
    fun resolveTrackEnds_rapidBackToBackInsertions_neverRegressesToUnclaimed() {
        var ends = resolveTrackEnds(listOf(anchor("a", cy = 300f, isSegmentTop = true)), TrackEnds(null, null))
        assertEquals("a", ends.topId)

        // "b" prepended before "a" ever fully settles — "b" placed but not topmost yet.
        ends = resolveTrackEnds(
            listOf(anchor("a", cy = 300f, isSegmentTop = false), anchor("b", cy = 350f, isSegmentTop = true)),
            ends,
        )
        assertEquals("a", ends.topId)

        // "c" prepended on top of THAT, also not yet topmost — "a" must still hold, not "b".
        ends = resolveTrackEnds(
            listOf(
                anchor("a", cy = 300f, isSegmentTop = false),
                anchor("b", cy = 350f, isSegmentTop = false),
                anchor("c", cy = 400f, isSegmentTop = true),
            ),
            ends,
        )
        assertEquals("a", ends.topId) // never regresses to null or an intermediate unsettled claim.

        // Everything settles: "c" is now genuinely topmost.
        ends = resolveTrackEnds(
            listOf(
                anchor("c", cy = 100f, isSegmentTop = true),
                anchor("b", cy = 200f, isSegmentTop = false),
                anchor("a", cy = 300f, isSegmentTop = false),
            ),
            ends,
        )
        assertEquals("c", ends.topId)
    }

    // ---- segmentCapDecision ----

    @Test
    fun segmentCapDecision_bothEndsConfirmed_roundsBothCaps() {
        val a = anchor("a", cy = 100f)
        val b = anchor("b", cy = 300f)
        val decision = segmentCapDecision(listOf(a, b), TrackEnds("a", "b"), viewportHeight = 1000f, halfTrack = 20f)
        assertTrue(decision.roundTop)
        assertTrue(decision.roundBottom)
        assertEquals(80f, decision.bgTop, 0f) // 100 - 20
        assertEquals(320f, decision.bgBottom, 0f) // 300 + 20
    }

    @Test
    fun segmentCapDecision_noConfirmedEnds_flushesToViewportEdges() {
        val a = anchor("a", cy = 100f)
        val b = anchor("b", cy = 300f)
        val decision = segmentCapDecision(listOf(a, b), TrackEnds(null, null), viewportHeight = 1000f, halfTrack = 20f)
        assertFalse(decision.roundTop)
        assertFalse(decision.roundBottom)
        assertEquals(0f, decision.bgTop, 0f)
        assertEquals(1000f, decision.bgBottom, 0f)
    }

    @Test
    fun segmentCapDecision_singleAnchorSegment_independentTopBottomIds() {
        // A single-anchor segment mid-replacement: the remembered top/bottom ids can genuinely
        // differ from the one anchor currently placed (e.g. mid-transition on both ends at once).
        val a = anchor("a", cy = 150f)
        val decision = segmentCapDecision(listOf(a), TrackEnds(topId = "a", bottomId = "stale"), viewportHeight = 1000f, halfTrack = 20f)
        assertTrue(decision.roundTop)
        assertFalse(decision.roundBottom)
    }

    // ---- spineGapRanges ----

    @Test
    fun spineGapRanges_noAnchors_singleFullRange() {
        val ranges = spineGapRanges(emptyList(), top = 0f, bottom = 100f, gap = 5f)
        assertEquals(listOf(0f to 100f), ranges)
    }

    @Test
    fun spineGapRanges_anchorOutsideRange_ignoredEntirely() {
        val a = anchor("a", cy = 500f, contentRadiusPx = 10f)
        val ranges = spineGapRanges(listOf(a), top = 0f, bottom = 100f, gap = 5f)
        assertEquals(listOf(0f to 100f), ranges)
    }

    @Test
    fun spineGapRanges_anchorExactlyAtBoundary_stillGapped() {
        val a = anchor("a", cy = 0f, contentRadiusPx = 10f)
        val ranges = spineGapRanges(listOf(a), top = 0f, bottom = 100f, gap = 5f)
        // gapRadius = 15, gapEnd = 15 — the line resumes right after the gap.
        assertEquals(listOf(15f to 100f), ranges)
    }

    @Test
    fun spineGapRanges_twoOverlappingGaps_cursorClampsMonotonically() {
        val a = anchor("a", cy = 50f, contentRadiusPx = 30f) // gap range [15, 85]
        val b = anchor("b", cy = 60f, contentRadiusPx = 30f) // gap range [25, 95] — overlaps a's.
        val ranges = spineGapRanges(listOf(a, b), top = 0f, bottom = 200f, gap = 5f)
        // No negative-length segment between the two overlapping gaps; cursor never moves backward.
        assertEquals(listOf(0f to 15f, 95f to 200f), ranges)
    }

    // ---- buildSleepPills ----

    @Test
    fun buildSleepPills_allAwake_noPills() {
        val anchors = listOf(anchor("a", cy = 100f), anchor("b", cy = 200f))
        val pills = buildSleepPills(anchors, bgTop = 80f, bgBottom = 220f, roundTop = true, roundBottom = true, halfTrack = 20f)
        assertTrue(pills.isEmpty())
    }

    @Test
    fun buildSleepPills_allAsleep_oneFullSpanPill() {
        val anchors = listOf(
            anchor("a", cy = 100f, asleepAbove = true, asleepBelow = true),
            anchor("b", cy = 200f, asleepAbove = true, asleepBelow = true),
        )
        val pills = buildSleepPills(anchors, bgTop = 80f, bgBottom = 220f, roundTop = true, roundBottom = true, halfTrack = 20f)
        assertEquals(1, pills.size)
        assertEquals(80f, pills[0].top, 0f)
        assertEquals(220f, pills[0].bottom, 0f)
        assertTrue(pills[0].roundTop)
        assertTrue(pills[0].roundBottom)
    }

    @Test
    fun buildSleepPills_singleAsleepAnchorSurroundedByAwake_onePill() {
        val anchors = listOf(
            anchor("a", cy = 100f, asleepAbove = false, asleepBelow = true),
            anchor("b", cy = 200f, asleepAbove = true, asleepBelow = false),
            anchor("c", cy = 300f, asleepAbove = false, asleepBelow = false),
        )
        val pills = buildSleepPills(anchors, bgTop = 80f, bgBottom = 320f, roundTop = true, roundBottom = true, halfTrack = 20f)
        assertEquals(1, pills.size)
        assertEquals(80f, pills[0].top, 0f) // a.cy - halfTrack
        assertEquals(220f, pills[0].bottom, 0f) // b.cy + halfTrack
    }

    @Test
    fun buildSleepPills_asleepRunStartsExactlyAtSegmentTop_capPropagates() {
        val anchors = listOf(
            anchor("a", cy = 100f, asleepAbove = true, asleepBelow = false),
        )
        val pills = buildSleepPills(anchors, bgTop = 80f, bgBottom = 200f, roundTop = true, roundBottom = false, halfTrack = 20f)
        assertEquals(1, pills.size)
        assertTrue(pills[0].roundTop) // propagated from the segment's own roundTop.
    }

    @Test
    fun buildSleepPills_asleepRunEndsExactlyAtSegmentBottom_capPropagates() {
        val anchors = listOf(
            anchor("a", cy = 100f, asleepAbove = false, asleepBelow = true),
        )
        val pills = buildSleepPills(anchors, bgTop = 80f, bgBottom = 200f, roundTop = false, roundBottom = true, halfTrack = 20f)
        assertEquals(1, pills.size)
        assertTrue(pills[0].roundBottom) // propagated from the segment's own roundBottom.
    }

    @Test
    fun buildSleepPills_twoDisjointAsleepRuns_twoPills() {
        val anchors = listOf(
            anchor("a", cy = 100f, asleepAbove = false, asleepBelow = true),
            anchor("b", cy = 150f, asleepAbove = true, asleepBelow = false),
            anchor("c", cy = 200f, asleepAbove = false, asleepBelow = false),
            anchor("d", cy = 250f, asleepAbove = false, asleepBelow = true),
            anchor("e", cy = 300f, asleepAbove = true, asleepBelow = false),
        )
        val pills = buildSleepPills(anchors, bgTop = 80f, bgBottom = 320f, roundTop = true, roundBottom = true, halfTrack = 20f)
        assertEquals(2, pills.size)
    }

    @Test
    fun buildSleepPills_runOpenAtBothScrollEdges_extendsToBgTopAndBgBottom() {
        val anchors = listOf(
            anchor("a", cy = 100f, asleepAbove = true, asleepBelow = true),
        )
        val pills = buildSleepPills(anchors, bgTop = 50f, bgBottom = 250f, roundTop = false, roundBottom = false, halfTrack = 20f)
        assertEquals(1, pills.size)
        assertEquals(50f, pills[0].top, 0f)
        assertEquals(250f, pills[0].bottom, 0f)
        assertFalse(pills[0].roundTop) // scroll-open edge, not a real segment cap — never rounds.
        assertFalse(pills[0].roundBottom)
    }

    // ---- cappedRoundRect ----

    @Test
    fun cappedRoundRect_neitherRounded_allCornersZero() {
        val rr = cappedRoundRect(0f, 0f, 100f, 100f, roundTop = false, roundBottom = false, corner = CornerRadius(20f))
        assertEquals(CornerRadius.Zero, rr.topLeftCornerRadius)
        assertEquals(CornerRadius.Zero, rr.topRightCornerRadius)
        assertEquals(CornerRadius.Zero, rr.bottomLeftCornerRadius)
        assertEquals(CornerRadius.Zero, rr.bottomRightCornerRadius)
    }

    @Test
    fun cappedRoundRect_topOnly_roundsTopCornersOnly() {
        val corner = CornerRadius(20f)
        val rr = cappedRoundRect(0f, 0f, 100f, 100f, roundTop = true, roundBottom = false, corner = corner)
        assertEquals(corner, rr.topLeftCornerRadius)
        assertEquals(corner, rr.topRightCornerRadius)
        assertEquals(CornerRadius.Zero, rr.bottomLeftCornerRadius)
        assertEquals(CornerRadius.Zero, rr.bottomRightCornerRadius)
    }

    @Test
    fun cappedRoundRect_bottomOnly_roundsBottomCornersOnly() {
        val corner = CornerRadius(20f)
        val rr = cappedRoundRect(0f, 0f, 100f, 100f, roundTop = false, roundBottom = true, corner = corner)
        assertEquals(CornerRadius.Zero, rr.topLeftCornerRadius)
        assertEquals(CornerRadius.Zero, rr.topRightCornerRadius)
        assertEquals(corner, rr.bottomLeftCornerRadius)
        assertEquals(corner, rr.bottomRightCornerRadius)
    }

    @Test
    fun cappedRoundRect_bothRounded_allCornersMatch() {
        val corner = CornerRadius(20f)
        val rr = cappedRoundRect(0f, 0f, 100f, 100f, roundTop = true, roundBottom = true, corner = corner)
        assertEquals(corner, rr.topLeftCornerRadius)
        assertEquals(corner, rr.topRightCornerRadius)
        assertEquals(corner, rr.bottomLeftCornerRadius)
        assertEquals(corner, rr.bottomRightCornerRadius)
    }

    @Test
    fun nonLatestAnchorCenterY_zeroViewportOffset_addsPaddingAndHalfDiameter() {
        val y = nonLatestAnchorCenterY(itemOffset = 100, viewportStartOffset = 0, verticalPaddingPx = 20f, anchorDiamPx = 40f)
        assertEquals(140f, y, 0f)
    }

    @Test
    fun nonLatestAnchorCenterY_nonZeroViewportStartOffset_isSubtracted() {
        // A LazyColumn with beforeContentPadding reports a negative viewportStartOffset; the item's
        // on-screen position is itemOffset - viewportStartOffset, matching computeAlarmCollapse's
        // identical convention (AlarmCollapseGeometry.kt).
        val y = nonLatestAnchorCenterY(itemOffset = 100, viewportStartOffset = -50, verticalPaddingPx = 20f, anchorDiamPx = 40f)
        assertEquals(190f, y, 0f)
    }

    @Test
    fun nonLatestAnchorCenterY_itemScrolledAboveViewportTop_returnsNegativeY() {
        // No clamping — an anchor whose item has scrolled above the visible top simply paints off-canvas, same as every other geometry function in this file.
        val y = nonLatestAnchorCenterY(itemOffset = -200, viewportStartOffset = 0, verticalPaddingPx = 20f, anchorDiamPx = 40f)
        assertEquals(-160f, y, 0f)
    }

    @Test
    fun nonLatestAnchorCenterY_zeroPaddingAndDiameter_equalsItemOffset() {
        val y = nonLatestAnchorCenterY(itemOffset = 300, viewportStartOffset = 0, verticalPaddingPx = 0f, anchorDiamPx = 0f)
        assertEquals(300f, y, 0f)
    }
}
