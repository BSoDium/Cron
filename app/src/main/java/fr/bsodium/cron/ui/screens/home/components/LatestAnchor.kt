@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Symbol

internal val LATEST_ANCHOR_SIZE = 32.dp
private val LATEST_GLYPH_SIZE = 20.dp

/** The single latest AI run's anchor: a filled Material shape (docs/expressive.md shape vocabulary)
 *  that morphs in from a plain circle once on entry, instead of the icon-in-a-dot every other node
 *  uses — the newest row visibly "arrives". It settles and holds; no continuous motion once arrived,
 *  since this row can sit on screen for hours between plans. */
@Composable
internal fun LatestAnchor(symbol: MaterialSymbol, modifier: Modifier = Modifier) {
    val fillColor = MaterialTheme.colorScheme.primary
    val glyphColor = MaterialTheme.colorScheme.onPrimary
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = remember { Animatable(0f) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val arriveSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { progress.animateTo(1f, arriveSpec) }

    Box(modifier = modifier.size(LATEST_ANCHOR_SIZE), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(LATEST_ANCHOR_SIZE)) {
            path.rewind()
            morph.toPath(progress.value.coerceIn(0f, 1f), path)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            val span = maxOf(bounds.width(), bounds.height())
            if (span > 0f) {
                val scale = size.minDimension / span
                matrix.reset()
                matrix.setScale(scale, scale)
                matrix.postTranslate(
                    (size.width - bounds.width() * scale) / 2f - bounds.left * scale,
                    (size.height - bounds.height() * scale) / 2f - bounds.top * scale,
                )
                path.transform(matrix)
                drawPath(path.asComposePath(), color = fillColor)
            }
        }
        Symbol(symbol = symbol, contentDescription = null, tint = glyphColor, size = LATEST_GLYPH_SIZE)
    }
}

@Preview(showBackground = true, name = "Latest anchor — arrival")
@Composable
private fun LatestAnchorPreview() {
    CronTheme {
        LatestAnchor(symbol = MaterialSymbol.Schedule)
    }
}
