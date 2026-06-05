@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import fr.bsodium.cron.ui.theme.CronTheme

/**
 * Lifecycle phase that drives the [ThinkingShape] morph. Derived from the AI thread: not running →
 * [Resting]; running with no answer yet → [Thinking]; running while the answer streams → [Writing].
 */
enum class ShapePhase { Resting, Thinking, Writing }

/**
 * A small brand-tinted Material shape under the AI thread that morphs with the assistant's state:
 * cozy round shapes while thinking, sharp star shapes while the answer streams in, settling to a
 * clover at rest — like a logo under a message.
 */
@Composable
fun ThinkingShape(phase: ShapePhase, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    var current by remember { mutableStateOf(REST) }
    var target by remember { mutableStateOf(REST) }
    val progress = remember { Animatable(0f) }
    val morph = remember(current, target) { Morph(current, target) }
    val androidPath = remember { Path() }
    val matrix = remember { Matrix() }

    LaunchedEffect(phase) {
        val cycle = when (phase) {
            ShapePhase.Thinking -> COZY
            ShapePhase.Writing -> SHARP
            ShapePhase.Resting -> null
        }
        if (cycle == null) {
            target = REST
            progress.snapTo(0f)
            progress.animateTo(1f, REST_SPEC)
            current = REST
            target = REST
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val step = if (phase == ShapePhase.Writing) WRITING_STEP_SPEC else THINKING_STEP_SPEC
        var i = 0
        while (true) {
            val next = cycle[i % cycle.size]
            target = next
            progress.snapTo(0f)
            progress.animateTo(1f, step)
            current = next
            i++
        }
    }

    Canvas(modifier = modifier.size(SHAPE_SIZE)) {
        drawMorph(morph, progress.value, color, androidPath, matrix)
    }
}

/** Renders [morph] at [progress], fit and centered into the draw bounds (works for any polygon space). */
private fun DrawScope.drawMorph(
    morph: Morph,
    progress: Float,
    color: Color,
    path: Path,
    matrix: Matrix,
) {
    path.rewind()
    morph.toPath(progress.coerceIn(0f, 1f), path)
    val bounds = RectF()
    path.computeBounds(bounds, true)
    val span = maxOf(bounds.width(), bounds.height())
    if (span <= 0f) return
    val scale = size.minDimension / span
    matrix.reset()
    matrix.setScale(scale, scale)
    matrix.postTranslate(
        (size.width - bounds.width() * scale) / 2f - bounds.left * scale,
        (size.height - bounds.height() * scale) / 2f - bounds.top * scale,
    )
    path.transform(matrix)
    drawPath(path.asComposePath(), color = color)
}

private val SHAPE_SIZE = 28.dp

// Slow, gentle cadence while thinking; snappy while the answer streams; a soft landing into the clover.
private val THINKING_STEP_SPEC = tween<Float>(durationMillis = 1400, easing = FastOutSlowInEasing)
private val WRITING_STEP_SPEC = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
private val REST_SPEC = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)

// Resting clover; cozy round shapes for thinking; sharp star shapes for streaming.
private val REST: RoundedPolygon = MaterialShapes.Clover4Leaf
private val COZY: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.SoftBurst,
    MaterialShapes.Sunny,
    MaterialShapes.Puffy,
    MaterialShapes.Clover4Leaf,
)
private val SHARP: List<RoundedPolygon> = listOf(
    MaterialShapes.Burst,
    MaterialShapes.Boom,
    MaterialShapes.VerySunny,
    MaterialShapes.Gem,
)

@Preview(showBackground = true)
@Composable
private fun ThinkingShapePreview() {
    CronTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            StaticShape(REST)
            StaticShape(COZY.first())
            StaticShape(SHARP.first())
        }
    }
}

@Composable
private fun StaticShape(polygon: RoundedPolygon) {
    val color = MaterialTheme.colorScheme.primary
    val morph = remember(polygon) { Morph(polygon, polygon) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier = Modifier.size(SHAPE_SIZE)) { drawMorph(morph, 0f, color, path, matrix) }
}
