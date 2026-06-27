@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import fr.bsodium.cron.ui.theme.TightTextStyle

internal val NODE_GUTTER = 40.dp
private val TRACK_WIDTH = 1.5.dp
private val PLAIN_DOT_SIZE = 8.dp
private val ICON_DOT_SIZE = 24.dp
private val ICON_GLYPH_SIZE = 18.dp
private val LOADER_DOT_SIZE = 28.dp

sealed interface TimelineAnchor {
    data object Plain : TimelineAnchor
    data class Icon(
        val symbol: MaterialSymbol,
        val tint: Color? = null,
        val containerColor: Color? = null,
    ) : TimelineAnchor

    data object Loader : TimelineAnchor
}

private fun TimelineAnchor.diameter(): Dp = when (this) {
    TimelineAnchor.Plain -> PLAIN_DOT_SIZE
    is TimelineAnchor.Icon -> ICON_DOT_SIZE
    TimelineAnchor.Loader -> LOADER_DOT_SIZE
}

@Composable
internal fun TimelineNode(
    anchor: TimelineAnchor,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = Spacing.md,
    title: @Composable () -> Unit,
    status: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val ruleColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val anchorDiam = anchor.diameter()
    // Anchor center is always verticalPadding + anchorDiam/2:
    // the Row puts anchor + title side-by-side with CenterVertically, so the Row height is
    // max(anchorDiam, titleHeight). Since anchorDiam typically >= titleHeight, the Row
    // is anchorDiam tall and the anchor sits at its own center = verticalPadding + anchorDiam/2.
    val anchorCenter = verticalPadding + anchorDiam / 2

    val inner = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            // includeFontPadding=true shifts the visual glyph center above the circle center; TightTextStyle strips it.
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(TightTextStyle)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = verticalPadding, end = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(NODE_GUTTER),
                    contentAlignment = Alignment.Center,
                ) {
                    AnchorCircle(anchor)
                }
                Spacer(Modifier.width(Spacing.xs))
                Box(modifier = Modifier.weight(1f).wrapContentHeight()) { title() }
                if (status != null) status()
            }
            }
            if (content != null) {
                Box(
                    modifier = Modifier.padding(
                        start = NODE_GUTTER + Spacing.xs,
                        end = Spacing.md,
                        top = Spacing.sm,
                    ),
                ) { content() }
            }
            Spacer(Modifier.height(verticalPadding))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (isFirst && isLast) return@drawBehind
                val cx = (NODE_GUTTER / 2).toPx()
                val center = anchorCenter.toPx()
                val top = if (isFirst) center else 0f
                val bottom = if (isLast) center else size.height
                drawLine(ruleColor, Offset(cx, top), Offset(cx, bottom), TRACK_WIDTH.toPx(), StrokeCap.Round)
            },
    ) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) { inner() }
        } else {
            inner()
        }
    }
}

@Composable
private fun AnchorCircle(anchor: TimelineAnchor) {
    when (anchor) {
        TimelineAnchor.Plain -> {
            Box(
                modifier = Modifier
                    .size(PLAIN_DOT_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        is TimelineAnchor.Icon -> {
            val bg = anchor.containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
            val fg = anchor.tint ?: MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(ICON_DOT_SIZE)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    symbol = anchor.symbol,
                    contentDescription = null,
                    tint = fg,
                    size = ICON_GLYPH_SIZE,
                )
            }
        }
        TimelineAnchor.Loader -> {
            ContainedLoadingIndicator(
                modifier = Modifier.size(LOADER_DOT_SIZE),
                containerShape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true, name = "Anchor states")
@Composable
private fun TimelineNodeAnchorsPreview() {
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            TimelineNode(
                anchor = TimelineAnchor.Loader,
                isFirst = true,
                isLast = false,
                title = { Text("Replanning...", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("07:16", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.Snooze),
                isFirst = false,
                isLast = false,
                title = { Text("Alarm snoozed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                status = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MonoPill("9 min")
                        Text("07:15", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(
                    symbol = MaterialSymbol.Schedule,
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                isFirst = false,
                isLast = false,
                onClick = {},
                title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            TimelineNode(
                anchor = TimelineAnchor.Plain,
                isFirst = false,
                isLast = false,
                title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                status = { Text("23:40", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                isFirst = false,
                isLast = true,
                title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                status = { Text("22:30", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
    }
}

@Preview(showBackground = true, name = "Node with content area")
@Composable
private fun TimelineNodeWithContentPreview() {
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            TimelineNode(
                anchor = TimelineAnchor.Icon(
                    symbol = MaterialSymbol.Schedule,
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                isFirst = true,
                isLast = false,
                onClick = {},
                title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                content = {
                    Text(
                        "Set alarm for 07:45. You have an 08:30 standup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                isFirst = false,
                isLast = true,
                title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                status = { Text("23:40", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
    }
}
