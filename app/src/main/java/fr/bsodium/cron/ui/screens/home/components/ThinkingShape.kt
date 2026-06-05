@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
fun ThinkingShape(phase: ShapePhase, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    var current by remember { mutableStateOf(REST) }
    var target by remember { mutableStateOf(REST) }
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
            // Settle to the heart, upright (nearest full turn) so it lands level.
            target = REST
            progress.snapTo(0f)
            coroutineScope {
                launch { progress.animateTo(1f, REST_SPEC) }
                launch { rotation.animateTo(nearestUpright(rotation.value), REST_SPIN_SPEC) }
            }
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
            // Morph and a quick springy spin run together — one spin per morph step.
            coroutineScope {
                launch { progress.animateTo(1f, step) }
                launch { rotation.animateTo(rotation.value + STEP_DEG, SPIN_SPEC) }
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
 * Renders [morph] at [progress], rotated by [rotationDeg] and fit-and-centered into the draw bounds
 * (with [SHAPE_FIT] margin so rotation never clips). [fillFraction] crossfades stroke→fill.
 */
private fun DrawScope.drawMorph(
    morph: Morph,
    progress: Float,
    rotationDeg: Float,
    fillFraction: Float,
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
    val scale = size.minDimension * SHAPE_FIT / span
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
    if (fill < 1f) drawPath(composePath, color = color.copy(alpha = 1f - fill), style = Stroke(width = STROKE_WIDTH.toPx()))
}

private fun nearestUpright(deg: Float): Float = (deg / 360f).roundToInt() * 360f

// Canvas is a touch larger than the visible shape so a spun shape never clips at the edges.
private val SHAPE_SIZE = 32.dp
private const val SHAPE_FIT = 0.8f
private val STROKE_WIDTH = 2.dp
private const val STEP_DEG = 120f

// Slow, gentle cadence while thinking; snappy while the answer streams; a soft landing into the heart.
private val THINKING_STEP_SPEC = tween<Float>(durationMillis = 1100, easing = FastOutSlowInEasing)
private val WRITING_STEP_SPEC = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
private val REST_SPEC = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
private val FILL_SPEC = tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing)
// Quick, springy spin per morph step; calm critically-damped settle to upright at rest.
private val SPIN_SPEC = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
private val REST_SPIN_SPEC = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

// Filled heart at rest; cozy round shapes for thinking; sharp star shapes for streaming.
private val REST: RoundedPolygon = MaterialShapes.Heart
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
            StaticShape(REST, fill = 1f)
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
