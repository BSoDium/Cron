@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lifecycle phase that drives the [ThinkingShape] morph. Derived from the AI thread: not running →
 * [Resting]; running with no answer yet → [Thinking]; running while the answer streams → [Writing].
 */
enum class ShapePhase { Resting, Thinking, Writing }

/**
 * A small brand-tinted Material shape under the AI thread that morphs with the assistant's state,
 * spinning springily with each morph: an outlined cozy shape while thinking, a filled sharp star
 * while the answer streams in, settling to a filled heart at rest — like a logo under a message.
 */
@Composable
fun ThinkingShape(phase: ShapePhase, modifier: Modifier = Modifier, restKey: Any? = null) {
    val color = MaterialTheme.colorScheme.primary
    // Each turn ([restKey]) settles to a different soft, low-corner shape.
    val rest = remember(restKey) { RESTING_SET.random() }
    var current by remember { mutableStateOf(rest) }
    var target by remember { mutableStateOf(rest) }
    val progress = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val morph = remember(current, target) { Morph(current, target) }
    val androidPath = remember { Path() }
    val matrix = remember { Matrix() }
    // Outline while thinking (no answer yet); fill in once the answer streams and at rest.
    val fill by animateFloatAsState(
        targetValue = if (phase == ShapePhase.Thinking) 0f else 1f,
        animationSpec = FILL_SPEC,
        label = "shape-fill",
    )

    LaunchedEffect(phase) {
        val cycle = when (phase) {
            ShapePhase.Thinking -> COZY
            ShapePhase.Writing -> SHARP
            ShapePhase.Resting -> null
        }
        if (cycle == null) {
            // Settle to the resting shape, upright (nearest full turn) so it lands level.
            target = rest
            progress.snapTo(0f)
            coroutineScope {
                launch { progress.animateTo(1f, REST_SPEC) }
                launch { rotation.animateTo(nearestUpright(rotation.value), REST_SPEC) }
            }
            current = rest
            target = rest
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val writing = phase == ShapePhase.Writing
        val morphSpec = if (writing) WRITING_STEP_SPEC else THINKING_STEP_SPEC
        val spinSpec = if (writing) WRITING_SPIN_SPEC else THINKING_SPIN_SPEC
        var i = 0
        while (true) {
            val next = cycle[i % cycle.size]
            target = next
            progress.snapTo(0f)
            // Morph and spin share a duration, so the spin lasts exactly as long as the morph.
            coroutineScope {
                launch { progress.animateTo(1f, morphSpec) }
                launch { rotation.animateTo(rotation.value + STEP_DEG, spinSpec) }
            }
            current = next
            i++
        }
    }

    Canvas(modifier = modifier.size(SHAPE_SIZE)) {
        drawMorph(morph, progress.value, rotation.value, fill, color, androidPath, matrix)
    }
}

/**
 * Renders [morph] at [progress], rotated by [rotationDeg] and fit-and-centered into the draw bounds.
 * [fit] is the fraction of the bounds the shape spans (default [SHAPE_FIT] leaves margin so rotation
 * never clips; pass 1f to fill the bounds when not rotating). [fillFraction] crossfades stroke→fill;
 * the stroke can use a different colour ([strokeColor]) from the fill (defaults to the same [color]).
 */
internal fun DrawScope.drawMorph(
    morph: Morph,
    progress: Float,
    rotationDeg: Float,
    fillFraction: Float,
    color: Color,
    path: Path,
    matrix: Matrix,
    strokeColor: Color = color,
    fit: Float = SHAPE_FIT,
) {
    path.rewind()
    morph.toPath(progress.coerceIn(0f, 1f), path)
    val bounds = RectF()
    path.computeBounds(bounds, true)
    val span = maxOf(bounds.width(), bounds.height())
    if (span <= 0f) return
    val scale = size.minDimension * fit / span
    matrix.reset()
    matrix.setScale(scale, scale)
    matrix.postTranslate(
        (size.width - bounds.width() * scale) / 2f - bounds.left * scale,
        (size.height - bounds.height() * scale) / 2f - bounds.top * scale,
    )
    matrix.postRotate(rotationDeg, size.width / 2f, size.height / 2f)
    path.transform(matrix)
    val composePath = path.asComposePath()
    val fill = fillFraction.coerceIn(0f, 1f)
    if (fill > 0f) drawPath(composePath, color = color.copy(alpha = fill))
    if (fill < 1f) drawPath(composePath, color = strokeColor.copy(alpha = 1f - fill), style = Stroke(width = STROKE_WIDTH.toPx()))
}

private fun nearestUpright(deg: Float): Float = (deg / 360f).roundToInt() * 360f

// Canvas is a touch larger than the visible shape so a spun shape never clips at the edges.
private val SHAPE_SIZE = 32.dp
private const val SHAPE_FIT = 0.8f
private val STROKE_WIDTH = 2.dp
private const val STEP_DEG = 120f

// Slow, gentle cadence while thinking; snappy while the answer streams; a soft landing into the heart.
private const val THINKING_STEP_MS = 1100
private const val WRITING_STEP_MS = 240
private const val REST_MS = 420
private val THINKING_STEP_SPEC = tween<Float>(durationMillis = THINKING_STEP_MS, easing = FastOutSlowInEasing)
private val WRITING_STEP_SPEC = tween<Float>(durationMillis = WRITING_STEP_MS, easing = FastOutSlowInEasing)
private val REST_SPEC = tween<Float>(durationMillis = REST_MS, easing = FastOutSlowInEasing)
private val FILL_SPEC = tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing)
// The spin shares the morph's per-phase duration so it lasts exactly as long as the morph, with a
// mild ease-out-back overshoot for a springy arrival.
private val SPIN_EASING = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
private val THINKING_SPIN_SPEC = tween<Float>(durationMillis = THINKING_STEP_MS, easing = SPIN_EASING)
private val WRITING_SPIN_SPEC = tween<Float>(durationMillis = WRITING_STEP_MS, easing = SPIN_EASING)

// At rest, a random soft low-corner shape; cozy round shapes for thinking; sharp star shapes streaming.
private val RESTING_SET: List<RoundedPolygon> = listOf(
    MaterialShapes.Ghostish,
    MaterialShapes.Bun,
    MaterialShapes.Heart,
    MaterialShapes.Flower,
    MaterialShapes.Clover4Leaf,
)
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
            StaticShape(RESTING_SET.first(), fill = 1f)
            StaticShape(COZY.first(), fill = 0f)
            StaticShape(SHARP.first(), fill = 1f)
        }
    }
}

@Composable
private fun StaticShape(polygon: RoundedPolygon, fill: Float) {
    val color = MaterialTheme.colorScheme.primary
    val morph = remember(polygon) { Morph(polygon, polygon) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier = Modifier.size(SHAPE_SIZE)) { drawMorph(morph, 0f, 0f, fill, color, path, matrix) }
}
