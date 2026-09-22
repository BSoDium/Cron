@file:Suppress("AnimationPreviewNotRequired") // A Modifier extension, not a composable — every call site (MemorySkeleton.kt, TimelineSkeleton.kt) already ships its own @Preview covering this in context.

package fr.bsodium.cron.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import fr.bsodium.cron.ui.theme.CronColors

private const val PulseHighlightBlend = 0.05f
private const val PulseDurationMillis = 1500

/** Cubic-bezier approximation of ease-in-out-sine, for a smooth breathing pulse, not a linear scan. */
private val PulseEasing = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)

/**
 * The pulsing placeholder fill shared by every skeleton shape across the app: every pixel of the
 * shape fades together between [CronColors.elementSurface] and a highlight nudged toward onSurface —
 * a calm, synchronized "breathing" cue, not a moving gradient band. Base matches the real card fill
 * rather than a [MaterialTheme] surfaceContainer role, which is what [CronColors.pageBackground]
 * resolves to in light mode — using that role here made the skeleton blend into the page instead of
 * reading as a card.
 */
@Composable
fun Modifier.skeletonPulse(): Modifier {
    val base = CronColors.elementSurface
    val highlight = lerp(base, MaterialTheme.colorScheme.onSurface, PulseHighlightBlend)
    val transition = rememberInfiniteTransition(label = "skeleton-pulse-transition")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PulseDurationMillis, easing = PulseEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-pulse-fraction",
    )
    return this.background(lerp(base, highlight, fraction))
}
