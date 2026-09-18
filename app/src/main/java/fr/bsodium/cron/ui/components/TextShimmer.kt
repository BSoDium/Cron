package fr.bsodium.cron.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Sweeps a highlight gradient across text glyphs to signal processing state.
 *
 * @param durationMillis Duration in milliseconds for one complete sweep (controls shimmer frequency).
 * @param baseColor Exact default/resting color of the text glyphs.
 * @param highlightColor Exact color of the sweeping highlight across the glyphs.
 */
@Composable
fun Modifier.textShimmer(
    durationMillis: Int = 1600,
    baseColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Modifier {
    val transition = rememberInfiniteTransition(label = "text_shimmer_transition")
    val progress by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "text_shimmer_progress",
    )

    return this
        .graphicsLayer { alpha = 0.99f }
        .drawWithContent {
            val width = size.width
            val startX = width * progress
            val shimmerWidth = width

            val brush = Brush.linearGradient(
                colors = listOf(
                    baseColor,
                    highlightColor,
                    baseColor,
                ),
                start = Offset(startX - shimmerWidth, 0f),
                end = Offset(startX, 0f),
            )

            drawContent()
            drawRect(
                brush = brush,
                blendMode = BlendMode.SrcIn,
            )
        }
}
