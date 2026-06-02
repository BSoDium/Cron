package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private val STEP_ICON_SIZE = 16.dp

/** Centre the glyph in its line box so the first line's optical centre lands at lineHeight/2 (where the gutter disc sits). */
private val STEP_LINE_HEIGHT = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private const val REASONING_COLLAPSE_CHARS = 280
private const val REASONING_COLLAPSED_LINES = 6
// Soft dissolve into "See more"; the band's height sets how gradual the fade reads.
private val REASONING_FADE_HEIGHT = 56.dp
private val REASONING_HEIGHT_SPEC = tween<Int>(durationMillis = 200, easing = FastOutSlowInEasing)

@Composable
private fun ThinkingIcon() = Icon(
    painter = painterResource(R.drawable.ic_thinking),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(STEP_ICON_SIZE),
)

/** Line height of a step's first line, used to centre its icon on that line. */
@Composable
private fun stepFirstLineHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
}

@Composable
internal fun ProcessTextRow(text: String, isFirst: Boolean, isLast: Boolean) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val collapsible = text.length > REASONING_COLLAPSE_CHARS
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeightStyle = STEP_LINE_HEIGHT,
    )
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = { ThinkingIcon() },
    ) {
        Column {
            // The full markdown is always rendered (parsed once — never blanks on toggle); collapsing
            // just clips an animated height, so expand/collapse glides instead of jumping.
            if (collapsible) {
                ClippedReveal(expanded = expanded, collapsedHeight = reasoningCollapsedHeight()) {
                    MarkdownBlock(text = text, bodyStyle = bodyStyle, serif = false)
                }
                // Snug transparent pill — ripple only on press. minimumInteractiveComponentSize keeps
                // the touch target at 48dp while the visible pill stays compact.
                Box(
                    modifier = Modifier
                        // Tuck the toggle up under the fade so the soft dissolve leads straight into it.
                        .offset(x = -Spacing.xs, y = -Spacing.sm)
                        // No start padding so the label aligns with the reasoning-text column; the
                        // 48dp touch target is preserved by minimumInteractiveComponentSize.
                        .minimumInteractiveComponentSize()
                        .clip(Radius.full)
                        .clickable { expanded = !expanded }
                        .padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(STEP_ICON_SIZE),
                        )
                        Text(
                            text = if (expanded) "See less" else "See more",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                MarkdownBlock(text = text, bodyStyle = bodyStyle, serif = false)
            }
        }
    }
}

/** Collapsed clamp height for a long reasoning block — about [REASONING_COLLAPSED_LINES] lines. */
@Composable
private fun reasoningCollapsedHeight(): Dp {
    val density = LocalDensity.current
    val lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    return with(density) { lineHeight.toDp() } * REASONING_COLLAPSED_LINES
}

/**
 * Renders [content] at full height but reports an animated, clamped height: expanding/collapsing
 * clips the bottom rather than swapping the text, so the markdown is parsed once and the motion is
 * smooth both ways. Top-anchored — the top stays put while the bottom reveals/clips — with a soft
 * [fadeBottom] edge while content remains below the fold.
 */
@Composable
private fun ClippedReveal(
    expanded: Boolean,
    collapsedHeight: Dp,
    content: @Composable () -> Unit,
) {
    val collapsedPx = with(LocalDensity.current) { collapsedHeight.roundToPx() }
    var fullPx by remember { mutableIntStateOf(0) }
    // Collapsed target is the constant collapsedPx (never `minOf(..., fullPx)` which is 0 before the first
    // measure) — so animateIntAsState starts at the resting height and a static preview never catches it at 0.
    val target = if (expanded) (if (fullPx > 0) fullPx else collapsedPx) else collapsedPx
    val animatedPx by animateIntAsState(target, REASONING_HEIGHT_SPEC, label = "reasoning-reveal")
    val fading = fullPx > 0 && animatedPx < fullPx
    SubcomposeLayout(
        modifier = Modifier
            .clipToBounds()
            .then(if (fading) Modifier.fadeBottom(REASONING_FADE_HEIGHT) else Modifier),
    ) { constraints ->
        val placeable = subcompose(Unit, content).first().measure(constraints.copy(minHeight = 0))
        if (placeable.height != fullPx) fullPx = placeable.height
        val h = if (fullPx == 0) placeable.height else animatedPx.coerceIn(0, placeable.height)
        layout(placeable.width, h) { placeable.place(0, 0) }
    }
}

/** Outlined icon for each tool's operation; wrench for anything unmapped. */
internal fun toolIcon(name: String): ImageVector = when (name) {
    "read_calendar" -> Icons.Outlined.CalendarMonth
    "set_alarm" -> Icons.Outlined.Alarm
    "cancel_alarm" -> Icons.Outlined.AlarmOff
    "estimate_commute", "estimate_commute_multi_mode" -> Icons.Outlined.DirectionsCar
    "geocode_address" -> Icons.Outlined.LocationOn
    "notify_warning" -> Icons.Outlined.WarningAmber
    "send_brief" -> Icons.AutoMirrored.Outlined.Article
    "do_nothing" -> Icons.Outlined.Bedtime
    else -> Icons.Outlined.Build
}

@Composable
internal fun ToolStepRow(step: ProcessItem.Tool, isFirst: Boolean, isLast: Boolean) {
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = {
            Icon(
                imageVector = toolIcon(step.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STEP_ICON_SIZE),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // "Calling" + name pill take their natural width (priority); the name pill never
            // char-wraps. The result takes the remaining space and ellipsizes (e.g. long addresses).
            Text(
                text = "Calling",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeightStyle = STEP_LINE_HEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Radius.sm),
            ) {
                Text(
                    text = step.name,
                    style = CronTypography.labelMonoSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                when {
                    !step.isComplete -> CircularProgressIndicator(
                        modifier = Modifier.size(SPINNER_SIZE),
                        strokeWidth = SPINNER_STROKE,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    step.isError -> Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(STEP_ICON_SIZE),
                    )
                    step.contextLabel != null -> Text(
                        text = step.contextLabel,
                        style = CronTypography.labelMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    else -> Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(STEP_ICON_SIZE),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DoneRow(isFirst: Boolean, isLast: Boolean) {
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STEP_ICON_SIZE),
        )
    }) {
        Text(
            text = "Done",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeightStyle = STEP_LINE_HEIGHT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
