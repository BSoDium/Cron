package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.Spacing

private val GUTTER_WIDTH = 28.dp
private val TIMELINE_RULE_WIDTH = 2.dp
private val ICON_MASK_SIZE = 24.dp
/** Row content padding; doubles as the inter-step gap (sm keeps the timeline rule visible between discs). */
private val TIMELINE_CONTENT_VPAD = Spacing.sm

/** Stacks timeline rows so their per-row [TimelineRow] rules join into one thread. */
@Composable
internal fun TimelineColumn(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
internal fun TimelineRow(
    firstLineHeight: Dp,
    isFirst: Boolean,
    isLast: Boolean,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Low-emphasis connector tone with enough contrast for a 2dp line against the page.
    val ruleColor = CronColors.elementSurface
    // The disc masks the connector rule behind the icon, so it matches the page it sits on.
    val maskColor = CronColors.pageBackground
    // Centre the disc on the content's FIRST line (not the whole multi-line row): disc centre =
    // contentTopPad + firstLine/2, so its top inset is that minus half the disc.
    val discTop = (TIMELINE_CONTENT_VPAD + (firstLineHeight - ICON_MASK_SIZE) / 2)
        .coerceAtLeast(0.dp)
    val iconCenter = discTop + ICON_MASK_SIZE / 2
    Box(modifier = Modifier.fillMaxWidth()) {
        // Content drives the row height. No IntrinsicSize.Min here, so an animating-height child
        // (see ClippedReveal) can actually resize the row instead of being snapped to full.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = TIMELINE_CONTENT_VPAD, bottom = TIMELINE_CONTENT_VPAD, end = Spacing.md),
            ) { content() }
        }
        // Gutter overlay: matchParentSize reads the content-driven height (it doesn't drive it), so
        // the connector rule and the masking icon disc track the content as it animates.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(GUTTER_WIDTH)
                    .fillMaxHeight()
                    // Draw the rule centred in the gutter, capped at the first/last node's centre.
                    .drawBehind {
                        if (isFirst && isLast) return@drawBehind
                        val cx = size.width / 2f
                        val top = if (isFirst) iconCenter.toPx() else 0f
                        val bottom = if (isLast) iconCenter.toPx() else size.height
                        drawLine(
                            color = ruleColor,
                            start = Offset(cx, top),
                            end = Offset(cx, bottom),
                            strokeWidth = TIMELINE_RULE_WIDTH.toPx(),
                        )
                    },
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = discTop)
                            .size(ICON_MASK_SIZE)
                            .clip(CircleShape)
                            .background(maskColor),
                        contentAlignment = Alignment.Center,
                    ) { icon() }
                }
            }
        }
    }
}
