package fr.bsodium.cron.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design tokens for spacing and corner radii. Reach for these before reaching
 * for raw `dp` literals so the UI stays on a consistent 4dp rhythm.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Bottom clearance reserved for the floating nav pill (≈68dp) + breathing room. */
    val navBarClearance = 96.dp
}

object Radius {
    val sm = 6.dp
    val md = 12.dp
    val lg = 20.dp
    val xl = 28.dp
    val full = RoundedCornerShape(50)
}
