@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

internal val TAB_MIN_HEIGHT = 48.dp
internal val ICON_GLYPH = 22.dp
internal val TAB_LOADER = 32.dp

/** Connected-tab corners: unselected inner corners rest at the M3 default (CornerValueSmall = 8.dp),
 *  morphing to a full pill when selected; the end tabs keep their pill OUTER corner in both states. */
internal val TAB_INNER_CORNER = CornerSize(8.dp)
internal val TAB_PILL_CORNER = CornerSize(50)


/** Connected-tab shape, lerped from a rounded rectangle (unselected) to a full pill (selected) by
 *  [fraction]; the outer corner of an end tab stays pill-round in both states. */
internal fun tabShape(index: Int, lastIndex: Int, fraction: Float): RoundedCornerShape {
    val inner = innerCorner(fraction)
    val start = if (index == 0) TAB_PILL_CORNER else inner
    val end = if (index == lastIndex) TAB_PILL_CORNER else inner
    return RoundedCornerShape(topStart = start, topEnd = end, bottomEnd = end, bottomStart = start)
}

/** A tab's inner corner, morphed from the rounded-rect default (dp) to a full pill (percent). The
 *  mixed dp/percent units only resolve at draw time, so the lerp lives in [CornerSize.toPx]. */
private fun innerCorner(fraction: Float): CornerSize = when (fraction) {
    0f -> TAB_INNER_CORNER
    1f -> TAB_PILL_CORNER
    else -> LerpCornerSize(TAB_INNER_CORNER, TAB_PILL_CORNER, fraction)
}

private class LerpCornerSize(val start: CornerSize, val stop: CornerSize, val t: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val a = start.toPx(shapeSize, density)
        val b = stop.toPx(shapeSize, density)
        return a + (b - a) * t
    }
}

/** Quintic "gain" easing — slope 5 at the centre, flat at the ends. Applied to the COLOUR inversion only:
 *  a tab keeps its colour for most of the swipe and flips fast across the low-contrast crossover, so the
 *  label barely lingers unreadable. The shape morph stays linear. */
private fun fastMiddle(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return if (t < 0.5f) 16f * t * t * t * t * t else 1f - 16f * pow5(1f - t)
}

private fun pow5(v: Float): Float = v * v * v * v * v

@Composable
internal fun ReplanTab(
    iteration: AiIterationUi,
    fraction: Float, // 0 = unselected, 1 = selected; interpolates colour + corner radius
    isLatest: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // The latest run wears the primary accent; older iterations stay quiet (secondary).
    val accent = isLatest
    val unselectedContainer = if (accent) scheme.primaryContainer else scheme.secondaryContainer
    val selectedContainer = if (accent) scheme.primary else scheme.secondary
    val onUnselected = if (accent) scheme.onPrimaryContainer else scheme.onSecondaryContainer
    val onSelected = if (accent) scheme.onPrimary else scheme.onSecondary
    // Colour inverts fast through the middle (quintic) so the label doesn't dwell at the muddy midpoint;
    // the shape morph (in `shape`) stays linear with the swipe.
    val colorFraction = fastMiddle(fraction)
    val content = lerp(onUnselected, onSelected, colorFraction)
    // A tab added after the strip first appeared fades + scales in (once); the initial set starts settled.
    val enter = remember { Animatable(if (isNew) 0f else 1f) }
    val enterSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    if (isNew) LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                alpha = enter.value
                val s = 0.85f + 0.15f * enter.value
                scaleX = s
                scaleY = s
            }
            .heightIn(min = TAB_MIN_HEIGHT),
        shape = shape,
        color = lerp(unselectedContainer, selectedContainer, colorFraction),
        contentColor = content,
    ) {
        val streaming = iteration.thread.isStreaming
        Row(
            // Tight, even leading inset in both states so neither the loader nor the icon floats with a left gap.
            modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm, end = Spacing.xl, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A still-streaming turn shows the contained Expressive loader nested in the pill; a settled
            // one shows its trigger icon. Fixed slot so the text doesn't shift when one swaps for the other.
            Box(modifier = Modifier.size(TAB_LOADER), contentAlignment = Alignment.Center) {
                if (streaming) {
                    // Colours derive from `content` (dark on an unselected tab, light on the selected one),
                    // so the loader auto-inverts with the tab and the container stays a subtle nested disc.
                    ContainedLoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        containerShape = CircleShape,
                        containerColor = content.copy(alpha = 0.2f),
                        indicatorColor = content,
                    )
                } else {
                    Symbol(symbol = runSymbol(iteration.kind), contentDescription = null, tint = content, size = ICON_GLYPH)
                }
            }
            Spacer(Modifier.width(Spacing.xs))
            Column {
                // Clip (not Ellipsis): single-line labels sized to the tab read cleaner hard-clipped than with "…".
                Text(
                    text = iteration.systemMessage,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = if (isLatest) "Latest · ${iteration.timeLabel}" else iteration.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
