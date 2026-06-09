package fr.bsodium.cron.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Draws content [bleed] wider on each side than its slot — overflowing the parent's horizontal
 * content padding out to the screen edges — while still reporting the slot width to the parent so
 * siblings are unaffected. Compensate with extra start/end padding to keep inner content put.
 */
internal fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    // Only meaningful under a bounded-width parent (e.g. a LazyColumn item); if width is unbounded
    // there's nothing to bleed into, so measure and place normally.
    if (constraints.maxWidth == Constraints.Infinity) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val bleedPx = bleed.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + bleedPx * 2))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleedPx, 0) }
}
