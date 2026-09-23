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
import androidx.compose.ui.graphics.Color
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

/** [PulseLowBlend]/[PulseHighBlend]'s counterpart for a shape that must read as a distinct marker
 *  sitting on top of plain skeleton fill (e.g. [TimelineSkeleton]'s anchor chip, on its own track) —
 *  the real timeline draws its anchor sockets in a separate accent color from the track itself, a
 *  contrast a shared stagger-phase offset alone can't reliably reproduce (see
 *  [rememberEmphasizedSkeletonPulseColor]'s KDoc). */
private const val EmphasizedPulseLowBlend = 0.16f
private const val EmphasizedPulseHighBlend = 0.34f
private const val PulseDurationMillis = 1500

/** Gap between each successive [skeletonPulse] caller's own phase, driving the top-to-bottom wave —
 *  see [skeletonPulse]'s `staggerIndex` param. */
private const val StaggerStepMillis = 90

/** Cubic-bezier approximation of ease-in-out-sine, for a smooth breathing pulse, not a linear scan. */
private val PulseEasing = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)

/** The raw 0..1 breathing progress behind [rememberSkeletonPulseColor] and
 *  [rememberEmphasizedSkeletonPulseColor] — split out so a shape needing both a normal and an
 *  emphasized color (e.g. a row and its own anchor chip) can derive both from one shared animation
 *  instead of running two independent [rememberInfiniteTransition]s for what's really the same wave.
 *
 * @param staggerIndex This shape's position in an ordered stack of skeleton shapes (e.g. a row's
 * index in a list) — each successive index delays its pulse's start by [StaggerStepMillis], so a
 * whole stack reads as one wave rolling top-to-bottom rather than every shape breathing in lockstep.
 * The delay is permanent (an infinite animation's phase offset, not just its first cycle), so the
 * wave keeps rolling for as long as the shapes stay on screen. Defaults to 0 (no stagger) for a
 * standalone shape.
 */
@Composable
fun rememberSkeletonPulseFraction(staggerIndex: Int = 0): Float {
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
    return fraction
}

/**
 * The pulsing placeholder color shared by every skeleton shape across the app: fades between a low
 * phase — close to [CronColors.pageBackground] but never identical to it — and a higher, more
 * "elevated" phase, both blended toward `onSurface` so the pair stays visible in every color scheme
 * (see [PulseLowBlend]'s KDoc for why this isn't anchored to [CronColors.elementSurface]).
 *
 * @param staggerIndex See [rememberSkeletonPulseFraction].
 */
@Composable
fun rememberSkeletonPulseColor(staggerIndex: Int = 0): Color =
    rememberSkeletonPulseColor(rememberSkeletonPulseFraction(staggerIndex))

/** Overload for a caller that already has a [fraction] from [rememberSkeletonPulseFraction] (e.g. to
 *  also derive [rememberEmphasizedSkeletonPulseColor] from the same wave) and would otherwise start a
 *  second, redundant animation by calling the `staggerIndex` overload instead. */
@Composable
fun rememberSkeletonPulseColor(fraction: Float): Color =
    skeletonPulseColorAt(fraction, PulseLowBlend, PulseHighBlend)

/** A bolder companion to [rememberSkeletonPulseColor], for a shape that has to read as a distinct
 *  marker sitting on top of plain skeleton fill rather than blend into it — [TimelineSkeleton]'s
 *  per-row anchor chip against its own track, mirroring how the real timeline's anchor socket paints
 *  in a separate accent color from the track fill behind it. A one-[staggerIndex]-step delay between
 *  an anchor and its track (`TimelineSkeleton.kt`'s own history: "fix invisible cap anchor") isn't a
 *  reliable enough contrast on its own — at [PulseDurationMillis] = 1500ms and [StaggerStepMillis] =
 *  90ms, a one-step offset is only 6% of the cycle, so for most of that cycle the two phases (and
 *  therefore colors) land within a couple of RGB units of each other, confirmed by direct pixel
 *  sampling of a recorded skeleton screenshot. Takes [fraction] directly (from
 *  [rememberSkeletonPulseFraction]) rather than its own `staggerIndex`, so the chip still rides the
 *  exact same wave timing as the rest of its row — it's the blend amount that's different, not the
 *  animation. */
@Composable
fun rememberEmphasizedSkeletonPulseColor(fraction: Float): Color =
    skeletonPulseColorAt(fraction, EmphasizedPulseLowBlend, EmphasizedPulseHighBlend)

@Composable
private fun skeletonPulseColorAt(fraction: Float, lowBlend: Float, highBlend: Float): Color {
    val background = CronColors.pageBackground
    val onSurface = MaterialTheme.colorScheme.onSurface
    val low = lerp(background, onSurface, lowBlend)
    val high = lerp(background, onSurface, highBlend)
    return lerp(low, high, fraction)
}

/** [Modifier.background]-applying convenience over [rememberSkeletonPulseColor] — the shape form
 *  every skeleton `Box` uses; a plain `Canvas`-based draw (e.g. [fr.bsodium.cron.ui.screens.home.components.SkeletonTrackConnector],
 *  which paints outside the normal layout tree) calls [rememberSkeletonPulseColor] directly instead. */
@Composable
fun Modifier.skeletonPulse(staggerIndex: Int = 0): Modifier = this.background(rememberSkeletonPulseColor(staggerIndex))
