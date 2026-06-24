@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

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
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = Spacing.lg,
    title: @Composable () -> Unit,
    status: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val ruleColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val headerHeight = nodeHeaderHeight()
    val anchorDiam = anchor.diameter()
    val anchorTop = verticalPadding + (headerHeight - anchorDiam) / 2
    val anchorCenter = anchorTop + anchorDiam / 2

    if (emphasized) {
        EmphasizedNode(
            anchor = anchor,
            isFirst = isFirst,
            isLast = isLast,
            ruleColor = ruleColor,
            anchorTop = anchorTop,
            anchorCenter = anchorCenter,
            headerHeight = headerHeight,
            verticalPadding = verticalPadding,
            onClick = onClick,
            modifier = modifier,
            title = title,
            status = status,
            content = content,
        )
    } else {
        StandardNode(
            anchor = anchor,
            isFirst = isFirst,
            isLast = isLast,
            ruleColor = ruleColor,
            anchorTop = anchorTop,
            anchorCenter = anchorCenter,
            verticalPadding = verticalPadding,
            onClick = onClick,
            modifier = modifier,
            title = title,
            status = status,
            content = content,
        )
    }
}

@Composable
private fun StandardNode(
    anchor: TimelineAnchor,
    isFirst: Boolean,
    isLast: Boolean,
    ruleColor: Color,
    anchorTop: Dp,
    anchorCenter: Dp,
    verticalPadding: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    title: @Composable () -> Unit,
    status: (@Composable () -> Unit)?,
    content: (@Composable () -> Unit)?,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(NODE_GUTTER))
            Spacer(Modifier.width(Spacing.xs))
            NodeContent(
                verticalPadding = verticalPadding,
                onClick = onClick,
                modifier = Modifier.weight(1f),
                title = title,
                status = status,
                content = content,
            )
        }
        GutterOverlay(
            anchor = anchor,
            isFirst = isFirst,
            isLast = isLast,
            ruleColor = ruleColor,
            anchorTop = anchorTop,
            anchorCenter = anchorCenter,
        )
    }
}

@Composable
private fun EmphasizedNode(
    anchor: TimelineAnchor,
    isFirst: Boolean,
    isLast: Boolean,
    ruleColor: Color,
    anchorTop: Dp,
    anchorCenter: Dp,
    headerHeight: Dp,
    verticalPadding: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    title: @Composable () -> Unit,
    status: (@Composable () -> Unit)?,
    content: (@Composable () -> Unit)?,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val containerPadding = Spacing.md

    val outerAnchorCenter = containerPadding + anchorCenter
    val innerGutterWidth = NODE_GUTTER - containerPadding

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .drawBehind {
                if (isFirst && isLast) return@drawBehind
                val cx = (NODE_GUTTER / 2).toPx()
                val center = outerAnchorCenter.toPx()
                val top = if (isFirst) center else 0f
                val bottom = if (isLast) center else size.height
                drawLine(ruleColor, Offset(cx, top), Offset(cx, bottom), TRACK_WIDTH.toPx(), StrokeCap.Round)
            },
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            onClick = onClick ?: {},
            enabled = onClick != null,
        ) {
            Box {
                Row(modifier = Modifier.fillMaxWidth().padding(containerPadding)) {
                    Spacer(Modifier.width(innerGutterWidth))
                    Spacer(Modifier.width(Spacing.xs))
                    NodeContent(
                        verticalPadding = verticalPadding,
                        onClick = null,
                        modifier = Modifier.weight(1f),
                        title = title,
                        status = status,
                        content = content,
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(start = containerPadding)
                        .drawBehind {
                            val cx = innerGutterWidth.toPx() / 2f
                            drawLine(ruleColor, Offset(cx, 0f), Offset(cx, size.height), TRACK_WIDTH.toPx(), StrokeCap.Round)
                        },
                ) {
                    GutterColumn(
                        anchor = anchor,
                        anchorTop = anchorTop,
                        maskColor = containerColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(innerGutterWidth)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeContent(
    verticalPadding: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    status: (@Composable () -> Unit)?,
    content: (@Composable () -> Unit)?,
) {
    val inner = @Composable {
        Column(
            modifier = Modifier.padding(
                top = verticalPadding,
                bottom = verticalPadding,
                end = Spacing.md,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(modifier = Modifier.weight(1f)) { title() }
                if (status != null) { status() }
            }
            if (content != null) {
                Box(modifier = Modifier.padding(top = Spacing.sm)) { content() }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { inner() }
    } else {
        Box(modifier = modifier) { inner() }
    }
}

@Composable
private fun BoxScope.GutterOverlay(
    anchor: TimelineAnchor,
    isFirst: Boolean,
    isLast: Boolean,
    ruleColor: Color,
    anchorTop: Dp,
    anchorCenter: Dp,
) {
    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(NODE_GUTTER)
                .fillMaxHeight()
                .drawBehind {
                    if (isFirst && isLast) return@drawBehind
                    val cx = size.width / 2f
                    val center = anchorCenter.toPx()
                    val top = if (isFirst) center else 0f
                    val bottom = if (isLast) center else size.height
                    drawLine(ruleColor, Offset(cx, top), Offset(cx, bottom), TRACK_WIDTH.toPx(), StrokeCap.Round)
                },
        ) {
            GutterColumn(
                anchor = anchor,
                anchorTop = anchorTop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(NODE_GUTTER)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun GutterColumn(
    anchor: TimelineAnchor,
    anchorTop: Dp,
    modifier: Modifier = Modifier,
    maskColor: Color = CronColors.pageBackground,
) {
    val anchorDiam = anchor.diameter()

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = anchorTop)
                .size(anchorDiam)
                .clip(CircleShape)
                .background(maskColor),
            contentAlignment = Alignment.Center,
        ) {
            AnchorContent(anchor)
        }
    }
}

@Composable
private fun AnchorContent(anchor: TimelineAnchor) {
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
            Symbol(
                symbol = anchor.symbol,
                contentDescription = null,
                tint = anchor.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                size = ICON_GLYPH_SIZE,
            )
        }
        TimelineAnchor.Loader -> {
            ContainedLoadingIndicator(
                modifier = Modifier.fillMaxSize(),
                containerShape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun nodeHeaderHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
}

@Preview(showBackground = true, name = "Anchor states")
@Composable
private fun TimelineNodeAnchorsPreview() {
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.Schedule),
                isFirst = true,
                isLast = false,
                title = { Text("Scheduled plan", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("23:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            TimelineNode(
                anchor = TimelineAnchor.Plain,
                isFirst = false,
                isLast = false,
                title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("23:40", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.Snooze),
                isFirst = false,
                isLast = false,
                title = { Text("Alarm snoozed", style = MaterialTheme.typography.bodyMedium) },
                status = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MonoPill("9 min")
                        Text("07:15", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
            TimelineNode(
                anchor = TimelineAnchor.Loader,
                isFirst = false,
                isLast = true,
                title = { Text("Replanning...", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("07:16", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
    }
}

@Preview(showBackground = true, name = "Emphasized node")
@Composable
private fun TimelineNodeEmphasizedPreview() {
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            TimelineNode(
                anchor = TimelineAnchor.Loader,
                isFirst = true,
                isLast = false,
                emphasized = true,
                title = {
                    Text("Replanning", style = MaterialTheme.typography.labelLarge)
                },
                status = {
                    Text(
                        "Latest · 07:16",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                content = {
                    Text(
                        "Adjusting alarm for calendar change...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.EventUpcoming),
                isFirst = false,
                isLast = false,
                title = { Text("Your schedule changed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                status = { Text("07:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            TimelineNode(
                anchor = TimelineAnchor.Icon(MaterialSymbol.Schedule),
                isFirst = false,
                isLast = false,
                onClick = {},
                title = { Text("Scheduled plan", style = MaterialTheme.typography.bodyMedium) },
                status = { Text("23:14", style = CronTypography.labelMonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
