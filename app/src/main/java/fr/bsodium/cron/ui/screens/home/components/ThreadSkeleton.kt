package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private const val SKELETON_PULSE_MS = 850
private const val SKELETON_MIN_ALPHA = 0.35f
private const val SKELETON_MAX_ALPHA = 0.70f
private val HEADER_ICON = 18.dp
private val LINE_HEIGHT = 13.dp

// Mostly-full lines with a short last one, echoing the wrapped serif response paragraph.
private val RESPONSE_LINES = listOf(1f, 1f, 0.92f, 1f, 0.66f)

/**
 * Placeholder for the AI thread (thinking block + response) shown in the thread slot while it's deferred
 * off the first frame — the page (greeting, alarm card, replan tabs) is already up, only the response area
 * is "not yet". Echoes the thread's shape: a thinking-disclosure header (tool disc + label) then a few
 * response lines. The caller crossfades it into the real [AiThinkingThread] once it composes.
 */
@Composable
internal fun ThreadSkeleton(modifier: Modifier = Modifier) {
    // A soft synchronized breathe. An infinite loading affordance has no fixed end, so motionScheme (finite
    // specs only) doesn't apply — infiniteRepeatable(tween) is the right tool here.
    val transition = rememberInfiniteTransition(label = "thread-skeleton")
    val pulse = transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = SKELETON_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton-pulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Thinking-disclosure header: a small tool icon + the "Thought for Xs" label.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBlock(pulse, Modifier.size(HEADER_ICON), CircleShape)
            SkeletonBlock(pulse, Modifier.width(116.dp).height(LINE_HEIGHT))
        }
        // Response paragraph: tight, mostly-full lines with a short last one.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RESPONSE_LINES.forEach { fraction ->
                SkeletonBlock(pulse, Modifier.fillMaxWidth(fraction).height(LINE_HEIGHT))
            }
        }
    }
}

/** A rounded placeholder block whose alpha breathes with [pulse] (read in the layer pass). */
@Composable
private fun SkeletonBlock(
    pulse: State<Float>,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(Radius.sm),
) {
    Box(
        modifier = modifier
            .graphicsLayer { alpha = pulse.value }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Preview(showBackground = true)
@Composable
private fun ThreadSkeletonPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ThreadSkeleton(Modifier.padding(vertical = Spacing.xl))
        }
    }
}
