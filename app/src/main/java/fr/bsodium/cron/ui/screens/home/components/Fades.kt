package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Fades the bottom [height] of the content to transparent — a soft edge used for the collapsed
 * reasoning truncation and the streaming response's growing edge. Renders to an offscreen layer so the
 * [BlendMode.DstIn] gradient erases only the destination's lower band; the gradient is opaque above
 * [height] from the bottom, so everything but that band is kept intact.
 */
internal fun Modifier.fadeBottom(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - height.toPx(),
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
