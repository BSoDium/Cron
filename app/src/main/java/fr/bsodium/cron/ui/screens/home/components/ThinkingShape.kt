@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lifecycle phase that drives the [ThinkingShape] morph. Derived from the AI thread: not running →
 * [Resting]; running with no answer yet → [Thinking]; running while the answer streams → [Writing].
 */
enum class ShapePhase { Resting, Thinking, Writing }

/**
 * A small brand-tinted Material shape under the AI thread that reacts to the assistant's state: while
 * thinking, a small muted outline that pulses between a down-arrow (the pull-to-show cue) and a random
 * shape about once a second; the moment the answer streams it grows, fills in, and morphs through sharp
 * stars with a spin; it settles to a soft filled shape at rest. Like a logo under a message.
 */
@Composable
fun ThinkingShape(phase: ShapePhase, modifier: Modifier = Modifier, restKey: Any? = null) {
    // Smaller + muted while thinking (quiet, low-contrast), growing to full-size brand primary once the
    // answer streams and at rest — so the shape visibly "wakes up" as it comes alive.
    val shapeSize by animateDpAsState(
        targetValue = if (phase == ShapePhase.Thinking) THINKING_SHAPE_SIZE else SHAPE_SIZE,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "shape-size",
    )
    val strokeColor by animateColorAsState(
        targetValue = if (phase == ShapePhase.Thinking) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "shape-stroke",
    )
    val fillColor = MaterialTheme.colorScheme.primary
    // Bouncy Expressive spring shared by the thinking pulse and the resting settle (captured here, used in
    // the effect below).
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
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
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "shape-fill",
    )

    LaunchedEffect(phase) {
        // One morph step: re-target and play the morph 0→1. Deliberately does NOT touch [current] — each
        // caller holds the just-completed morph and reassigns current only right before the next step, so a
        // static pause never renders a degenerate Morph(X, X) whose re-matched bounds would jump the
        // fit-scale the instant the shape lands.
        suspend fun morphTo(next: RoundedPolygon) {
            target = next
            progress.snapTo(0f)
            progress.animateTo(1f, spatialSpec)
        }
        when (phase) {
            ShapePhase.Thinking -> {
                // Bouncily pulse the down-arrow ⇄ a random shape (~once a second) — the pull-to-show-thinking
                // cue. (A MaterialShapes polygon in the morph canvas, not a vector icon; the downward
                // orientation is the intended pull affordance, not an accidental icon flip. The pool shapes
                // read fine at this fixed rotation, so there's no spin.)
                rotation.snapTo(ARROW_DOWN_DEG)
                morphTo(THINKING_ARROW)
                while (true) {
                    delay(THINKING_HOLD_MS)
                    current = THINKING_ARROW
                    val other = THINKING_POOL.random()
                    morphTo(other)
                    delay(THINKING_HOLD_MS)
                    current = other
                    morphTo(THINKING_ARROW)
                }
            }
            ShapePhase.Resting -> {
                // Settle to the resting shape, upright (nearest full turn) so it lands level.
                target = rest
                progress.snapTo(0f)
                coroutineScope {
                    launch { progress.animateTo(1f, spatialSpec) }
                    launch { rotation.animateTo(nearestUpright(rotation.value), spatialSpec) }
                }
                current = rest
                target = rest
                progress.snapTo(0f)
            }
            ShapePhase.Writing -> {
                // The answer is streaming: come alive — morph and spin through the sharp shapes (fill ramps in).
                var i = 0
                while (true) {
                    val next = SHARP[i % SHARP.size]
                    target = next
                    progress.snapTo(0f)
                    // Morph and spin share a duration, so the spin lasts exactly as long as the morph.
                    coroutineScope {
                        launch { progress.animateTo(1f, WRITING_STEP_SPEC) }
                        launch { rotation.animateTo(rotation.value + STEP_DEG, WRITING_SPIN_SPEC) }
                    }
                    current = next
                    i++
                }
            }
        }
    }

    Canvas(modifier = modifier.size(shapeSize)) {
        drawMorph(morph, progress.value, rotation.value, fill, fillColor, androidPath, matrix, strokeColor = strokeColor)
    }
}

/**
 * Renders [morph] at [progress], rotated by [rotationDeg] and fit-and-centered into the draw bounds
 * (spanning [SHAPE_FIT] of them, so a rotated shape never clips). [fillFraction] crossfades stroke→fill;
 * the stroke can use a different colour ([strokeColor]) from the fill (defaults to the same [color]).
 */
private fun DrawScope.drawMorph(
    morph: Morph,
    progress: Float,
    rotationDeg: Float,
    fillFraction: Float,
    color: Color,
    path: Path,
    matrix: Matrix,
    strokeColor: Color = color,
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
    if (fill < 1f) drawPath(composePath, color = strokeColor.copy(alpha = 1f - fill), style = Stroke(width = STROKE_WIDTH.toPx()))
}

private fun nearestUpright(deg: Float): Float = (deg / 360f).roundToInt() * 360f

// Canvas is a touch larger than the visible shape so a spun shape never clips at the edges.
private val SHAPE_SIZE = 32.dp
// Small, ~text-sized footprint while thinking (a down-arrow pull cue); grows to SHAPE_SIZE as it comes alive.
private val THINKING_SHAPE_SIZE = 18.dp
private const val SHAPE_FIT = 0.8f
private val STROKE_WIDTH = 1.5.dp
private const val STEP_DEG = 120f
// Rotation that points MaterialShapes.Arrow downward (its 0° orientation points up); tune on device.
private const val ARROW_DOWN_DEG = 180f
// Hold between thinking morphs so the arrow ⇄ random pulse lands at ~one change per second.
private const val THINKING_HOLD_MS = 700L

/** Documented exception to the motionScheme-only rule: the writing morph and its spin must share an EXACT
 *  duration so the spin lands precisely when the morph does, and springs expose no fixed duration — this
 *  coupled pair is tween-based by design. Everything else in this file animates on motionScheme specs. */
private const val WRITING_STEP_MS = 240
private val WRITING_STEP_SPEC = tween<Float>(durationMillis = WRITING_STEP_MS, easing = FastOutSlowInEasing)
// Mild ease-out-back overshoot so the spin still arrives springily despite the fixed duration.
private val SPIN_EASING = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
private val WRITING_SPIN_SPEC = tween<Float>(durationMillis = WRITING_STEP_MS, easing = SPIN_EASING)

// At rest, a random soft low-corner shape; a small down-arrow while thinking; sharp star shapes while streaming.
private val RESTING_SET: List<RoundedPolygon> = listOf(
    MaterialShapes.Ghostish,
    MaterialShapes.Bun,
    MaterialShapes.Heart,
    MaterialShapes.Flower,
    MaterialShapes.Clover4Leaf,
)
// While thinking, the shape pulses between the downward arrow (rotated via ARROW_DOWN_DEG) and a random
// shape from THINKING_POOL, as the pull-to-show cue.
private val THINKING_ARROW: RoundedPolygon = MaterialShapes.Arrow
private val SHARP: List<RoundedPolygon> = listOf(
    MaterialShapes.Burst,
    MaterialShapes.Boom,
    MaterialShapes.VerySunny,
    MaterialShapes.Gem,
)
// Any shape but the arrow — the thinking pulse morphs the arrow to a random one of these and back.
private val THINKING_POOL: List<RoundedPolygon> = RESTING_SET + SHARP + MaterialShapes.Circle

@Preview(showBackground = true)
@Composable
private fun ThinkingShapePreview() {
    CronTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            StaticShape(RESTING_SET.first(), fill = 1f)
            StaticShape(THINKING_ARROW, fill = 0f)
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
