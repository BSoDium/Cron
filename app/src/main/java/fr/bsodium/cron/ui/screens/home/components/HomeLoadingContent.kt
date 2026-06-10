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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private const val SKELETON_PULSE_MS = 850
private const val SKELETON_MIN_ALPHA = 0.35f
private const val SKELETON_MAX_ALPHA = 0.70f
private val SKELETON_CARD_HEIGHT = 224.dp

/**
 * Shimmer skeleton shown while the day's plan is still loading (the `HomePhase.Loading` gate, before
 * `uiState.initialized`). Echoes the above-the-fold layout — greeting lines, the alarm card, a few
 * response lines — so the cold-start moment reads as "loading" and the caller's phase `Crossfade`
 * dissolves it into the real content. Only seen on genuine first loads; the ViewModel survives tab
 * re-entry, so it never flashes when switching back.
 */
@Composable
internal fun HomeLoadingContent(statusInsetTop: Dp, navInsetBottom: Dp) {
    // A soft synchronized breathe. An infinite loading affordance has no fixed end, so motionScheme (which
    // only offers finite spatial/effects specs) doesn't apply — infiniteRepeatable(tween) is the right tool.
    val transition = rememberInfiniteTransition(label = "home-skeleton")
    val pulse = transition.animateFloat(
        initialValue = SKELETON_MIN_ALPHA,
        targetValue = SKELETON_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton-pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = statusInsetTop + Spacing.xxl, bottom = navInsetBottom + Spacing.navBarClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBar(pulse, widthFraction = 0.42f, height = 26.dp)
            SkeletonBar(pulse, widthFraction = 0.66f, height = 26.dp)
        }
        SkeletonBar(
            pulse,
            widthFraction = 1f,
            height = SKELETON_CARD_HEIGHT,
            shape = RoundedCornerShape(Radius.xl),
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SkeletonBar(pulse, widthFraction = 0.9f, height = 16.dp)
            SkeletonBar(pulse, widthFraction = 0.78f, height = 16.dp)
            SkeletonBar(pulse, widthFraction = 0.5f, height = 16.dp)
        }
    }
}

/** A single rounded placeholder block whose alpha breathes with [pulse] (read in the layer pass). */
@Composable
private fun SkeletonBar(
    pulse: State<Float>,
    widthFraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.sm),
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .graphicsLayer { alpha = pulse.value }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeLoadingContentPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeLoadingContent(statusInsetTop = 0.dp, navInsetBottom = 0.dp)
        }
    }
}
