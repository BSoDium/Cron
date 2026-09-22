package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private const val PulseHighlightBlend = 0.05f
private const val PulseDurationMillis = 1500

/** Cubic-bezier approximation of ease-in-out-sine, for a smooth breathing pulse, not a linear scan. */
private val PulseEasing = CubicBezierEasing(0.445f, 0.05f, 0.55f, 0.95f)

/** Approximates [MemoryEntryRow]'s single-line content height (title + chip + vertical padding). */
private val entryCardHeight = 72.dp

/**
 * The pulsing placeholder fill shared by every skeleton shape: every pixel of the shape fades
 * together between [CronColors.elementSurface] and a highlight nudged toward onSurface — a calm,
 * synchronized "breathing" cue, not a moving gradient band. Base matches the real card fill rather
 * than a [MaterialTheme] surfaceContainer role, which is what [CronColors.pageBackground] resolves
 * to in light mode — using that role here made the skeleton blend into the page instead of reading
 * as a card.
 */
@Composable
private fun Modifier.skeletonPulse(): Modifier {
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

/**
 * A skeleton loader for the Memory screen, mimicking the categorized list of [MemoryEntryRow] items.
 */
@Composable
internal fun MemorySkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(2) { sectionIndex ->
            // Must match SectionHeader's (MemoryScreen.kt) top padding exactly, first section included.
            CategoryHeaderSkeleton(
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs)
            )
            repeat(if (sectionIndex == 0) 3 else 2) {
                MemoryEntrySkeleton()
            }
        }
    }
}

@Composable
private fun CategoryHeaderSkeleton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(80.dp)
            .height(16.dp)
            .clip(Radius.full)
            .skeletonPulse()
    )
}

@Composable
private fun MemoryEntrySkeleton(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(entryCardHeight)
            .clip(RoundedCornerShape(Radius.lg))
            .skeletonPulse()
    )
}

@Preview(showBackground = true)
@Composable
private fun MemorySkeletonPreview() {
    CronPreview {
        Box(modifier = Modifier.padding(Spacing.lg)) {
            MemorySkeleton()
        }
    }
}
