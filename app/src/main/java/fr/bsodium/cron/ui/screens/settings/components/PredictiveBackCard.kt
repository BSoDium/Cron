package fr.bsodium.cron.ui.screens.settings.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.ui.screens.settings.LocalSettingsListState
import fr.bsodium.cron.ui.screens.settings.SettingsScreen
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val COMMIT_MS = 300
private const val CANCEL_MS = 220

/** How far the lifted card scales down at full pull, and how far it shifts toward the gesture edge. */
private const val CARD_MIN_SCALE = 0.90f
private val CARD_PREVIEW_SHIFT = Spacing.lg

/** The parent peeking behind: scaled down, shifted left and dimmed throughout the hold, restored on commit. */
private const val PARENT_MIN_SCALE = 0.92f
private const val PARENT_MAX_DIM = 0.35f
private const val PARENT_SHIFT_FRACTION = 0.18f

/**
 * Two-phase predictive back, matching the Pixel-Settings gesture. Navigation Compose's built-in seekable
 * predictive back maps the whole pop transition to the drag, so at max pull it's already finished and
 * release does nothing — it structurally can't separate preview from commit. This drives the two phases by
 * hand off [PredictiveBackHandler]:
 *
 * - **Drag (progress 0→1):** the current page ([content]) shrinks into an opaque rounded card that shifts
 *   toward the gesture edge; the parent ([SettingsScreen], stateless so a second instance is free) is
 *   revealed behind it, scaled down and dimmed. At max pull it's a held, fully-readable card.
 * - **Release / commit (progress 1→2):** the card slides off-screen and fades while the parent de-dims and
 *   scales to full; then [onBack] performs the real pop (the NavHost swap is `None`, hidden under this).
 * - **Cancel:** the card animates back to rest.
 *
 * [content] receives an `animatedBack` callback so the app-bar back arrow and the system back button play
 * the same commit animation as the gesture.
 */
@Composable
internal fun PredictiveBackCard(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (animatedBack: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var edgeLeft by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }

    fun animatedBack() {
        if (committing) return
        committing = true
        active = true
        scope.launch {
            progress.animateTo(2f, tween(COMMIT_MS, easing = EaseOutCubic))
            onBack()
        }
    }

    PredictiveBackHandler(enabled = !committing) { events ->
        active = true
        try {
            events.collect { event ->
                edgeLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                progress.snapTo(decelerate(event.progress))
            }
            committing = true
            progress.animateTo(2f, tween(COMMIT_MS, easing = EaseOutCubic))
            onBack()
        } catch (cancel: CancellationException) {
            scope.launch {
                progress.animateTo(0f, tween(CANCEL_MS, easing = EaseOutCubic))
                active = false
            }
            throw cancel
        }
    }

    PredictiveBackLayers(
        progress = { progress.value },
        edgeLeft = edgeLeft,
        active = active,
        modifier = modifier,
    ) { content(::animatedBack) }
}

/**
 * Pure visual layers for [PredictiveBackCard], parameterised by a [progress] provider (0=rest, 1=held
 * preview, 2=committed) so the transforms re-read in the layer pass without recomposing [content].
 */
@Composable
private fun PredictiveBackLayers(
    progress: () -> Float,
    edgeLeft: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = with(density) { (screenWidthDp.dp + 48.dp).toPx() }
    val previewShiftPx = with(density) { CARD_PREVIEW_SHIFT.toPx() }
    val parentShiftPx = with(density) { screenWidthDp.dp.toPx() } * PARENT_SHIFT_FRACTION

    Box(modifier = modifier.fillMaxSize()) {
        if (active) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val commit = (progress() - 1f).coerceIn(0f, 1f)
                        val sign = if (edgeLeft) -1f else 1f
                        val scale = lerp(PARENT_MIN_SCALE, 1f, commit)
                        scaleX = scale
                        scaleY = scale
                        // Parked on the side the card vacates (opposite the card, which moves toward the
                        // gesture edge) so the geometry mirrors for both edges; slides to centre on commit.
                        translationX = -sign * parentShiftPx * (1f - commit)
                    },
            ) {
                SettingsScreen(
                    onOpenCategory = {},
                    listState = LocalSettingsListState.current,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val commit = (progress() - 1f).coerceIn(0f, 1f)
                        alpha = PARENT_MAX_DIM * (1f - commit)
                    }
                    .background(Color.Black),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress()
                    val preview = p.coerceIn(0f, 1f)
                    val commit = (p - 1f).coerceIn(0f, 1f)
                    val sign = if (edgeLeft) -1f else 1f
                    val scale = lerp(1f, CARD_MIN_SCALE, preview)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - commit
                    translationX = sign * (previewShiftPx * preview + (screenWidthPx - previewShiftPx) * commit)
                    clip = true
                    shape = RoundedCornerShape(Radius.xl * preview)
                },
        ) {
            content()
        }
    }
}

/** Decelerate the raw gesture progress so the card eases as it's pulled, per the Material back spec. */
private fun decelerate(raw: Float): Float {
    val x = raw.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

@Preview(showBackground = true)
@Composable
private fun PredictiveBackCardPreview() {
    CronTheme {
        // A held mid-drag preview (progress = 1): the detail as a lifted card over the dimmed root.
        PredictiveBackLayers(progress = { 1f }, edgeLeft = false, active = true) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        "Schedule",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(Spacing.xl),
                    )
                }
            }
        }
    }
}
