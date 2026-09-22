@file:Suppress("AnimationPreviewNotRequired") // A Modifier extension, not a composable — every call site (MemorySkeleton.kt, TimelineSkeleton.kt's TimelineRowsSkeleton) already ships its own @Preview covering this in context.

package fr.bsodium.cron.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
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

/** How far each pulse phase sits from [CronColors.pageBackground], blending toward `onSurface`. Both
 *  are deliberately anchored to the page background itself, not [CronColors.elementSurface] — that
 *  role swaps between `surface`/`surfaceContainer` per theme (see its KDoc) and can end up equal to
 *  the actual page background in some color schemes (confirmed live in Compose Preview), making the
 *  whole skeleton flash invisible at the low phase. Blending off the background by a guaranteed
 *  nonzero amount rules that out structurally, regardless of how any particular scheme resolves its
 *  surface roles. */
private const val PulseLowBlend = 0.05f

/** The "raised" phase — clearly more contrast than [PulseLowBlend], never so strong it reads as a
 *  solid accent color rather than a placeholder shape. */
private const val PulseHighBlend = 0.16f
private const val PulseDurationMillis = 1500

/** Gap between each successive [skeletonPulse] caller's own phase, driving the top-to-bottom wave —
 *  see [skeletonPulse]'s `staggerIndex` param. */
private const val StaggerStepMillis = 90

/** Cubic-bezier approximation of ease-in-out-sine, for a smooth breathing pulse, not a linear scan. */
private val PulseEasing = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)

/**
 * The pulsing placeholder fill shared by every skeleton shape across the app: every pixel of the
 * shape fades together between a low phase — close to [CronColors.pageBackground] but never
 * identical to it — and a higher, more "elevated" phase, both blended toward `onSurface` so the pair
 * stays visible in every color scheme (see [PulseLowBlend]'s KDoc for why this isn't anchored to
 * [CronColors.elementSurface]).
 *
 * @param staggerIndex This shape's position in an ordered stack of skeleton shapes (e.g. a row's
 * index in a list) — each successive index delays its pulse's start by [StaggerStepMillis], so a
 * whole stack reads as one wave rolling top-to-bottom rather than every shape breathing in lockstep.
 * The delay is permanent (an infinite animation's phase offset, not just its first cycle), so the
 * wave keeps rolling for as long as the shapes stay on screen. Defaults to 0 (no stagger) for a
 * standalone shape.
 */
@Composable
fun Modifier.skeletonPulse(staggerIndex: Int = 0): Modifier {
    val background = CronColors.pageBackground
    val onSurface = MaterialTheme.colorScheme.onSurface
    val low = lerp(background, onSurface, PulseLowBlend)
    val high = lerp(background, onSurface, PulseHighBlend)
    val transition = rememberInfiniteTransition(label = "skeleton-pulse-transition")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PulseDurationMillis, easing = PulseEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(staggerIndex * StaggerStepMillis, StartOffsetType.Delay),
        ),
        label = "skeleton-pulse-fraction",
    )
    return this.background(lerp(low, high, fraction))
}
