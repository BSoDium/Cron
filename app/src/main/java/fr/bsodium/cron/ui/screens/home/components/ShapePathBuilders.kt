package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/** Builds a [Morph]'s outline at [progress] as a Compose [Path], scaled into a [diameterPx]-wide
 *  square centered at ([cx], [cy]). [scratch] is a caller-owned, reused [android.graphics.Path] so the
 *  morph animation doesn't allocate one per frame. Shared by the Latest socket carve here and (before
 *  Round 13) the anchor's own fill, kept a single source so the two can't drift. */
internal fun buildMorphPath(
    morph: Morph,
    progress: Float,
    cx: Float,
    cy: Float,
    diameterPx: Float,
    scratch: android.graphics.Path,
): Path {
    scratch.rewind()
    morph.toPath(progress.coerceIn(0f, 1f), scratch)
    val bounds = android.graphics.RectF()
    scratch.computeBounds(bounds, true)
    val span = maxOf(bounds.width(), bounds.height())
    if (span <= 0f) return Path()
    val scale = diameterPx / span
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            cx - (bounds.left + bounds.width() / 2f) * scale,
            cy - (bounds.top + bounds.height() / 2f) * scale,
        )
    }
    scratch.transform(matrix)
    return scratch.asComposePath()
}

/** Builds a static [RoundedPolygon]'s outline as a Compose [Path], scaled into a [diameterPx]-wide
 *  square centered at ([cx], [cy]). Sibling to [buildMorphPath] — the same measure-and-scale technique,
 *  but for a fixed silhouette (valence shape), so there's no progress/rewind: the polygon never changes
 *  frame to frame. [scratch] is reused to avoid a per-draw allocation. */
internal fun buildPolygonPath(
    polygon: RoundedPolygon,
    cx: Float,
    cy: Float,
    diameterPx: Float,
    scratch: android.graphics.Path,
): Path {
    scratch.rewind()
    polygon.toPath(scratch)
    val bounds = android.graphics.RectF()
    scratch.computeBounds(bounds, true)
    val span = maxOf(bounds.width(), bounds.height())
    if (span <= 0f) return Path()
    val scale = diameterPx / span
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            cx - (bounds.left + bounds.width() / 2f) * scale,
            cy - (bounds.top + bounds.height() / 2f) * scale,
        )
    }
    scratch.transform(matrix)
    return scratch.asComposePath()
}
