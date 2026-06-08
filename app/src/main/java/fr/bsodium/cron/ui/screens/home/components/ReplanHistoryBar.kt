@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlin.math.roundToInt

private val TAB_MIN_HEIGHT = 48.dp
private val ICON_GLYPH = 18.dp

/**
 * The planning-iteration strip: a genuine Material 3 Expressive connected [ButtonGroup] (pressing a tab
 * grows it and squishes its neighbours via [animateWidth]), full-bleed and horizontally scrollable.
 * Per the connected spec, the **selected** tab is a full pill and the others are rounded rectangles
 * (end tabs capped on their outer edge). Each tab shows the trigger icon + the replan type over its
 * time. Latest sits at the right (selected by default); selecting any tab brings it into view and swaps
 * the thread below. A light tic fires on switch.
 */
@Suppress("DEPRECATION") // simple ButtonGroup overload — the non-deprecated one forces an overflow MENU,
// which is incompatible with the horizontal scroll we want for overflow.
@Composable
internal fun ReplanHistoryBar(
    iterations: List<AiIterationUi>,
    selectedTurn: Int,
    onSelect: (Int) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCronHaptics(hapticsEnabled)
    val scrollState = rememberScrollState()
    // Each tab's [left, right] x within the scroll content, for bring-into-view.
    val spans = remember { mutableStateMapOf<Int, IntArray>() }
    var viewportPx by remember { mutableIntStateOf(0) }
    var didInitialScroll by remember { mutableStateOf(false) }
    val padPx = with(LocalDensity.current) { Spacing.md.roundToPx() }

    // Centre the selected tab in the viewport as far as the scroll range allows; the coerce clamps the
    // first/last tabs (which can't reach centre) to the nearest end, so they still rest aligned with the
    // card. spans are in the ButtonGroup's coords; +padPx converts to scroll coords. Instant on first
    // layout, animated after.
    LaunchedEffect(selectedTurn, viewportPx, spans[selectedTurn]?.get(0), spans[selectedTurn]?.get(1)) {
        val span = spans[selectedTurn] ?: return@LaunchedEffect
        if (viewportPx <= 0) return@LaunchedEffect
        val tabCenter = (span[0] + span[1]) / 2 + padPx
        val target = (tabCenter - viewportPx / 2).coerceIn(0, scrollState.maxValue)
        if (!didInitialScroll) {
            scrollState.scrollTo(target)
            didInitialScroll = true
        } else {
            scrollState.animateScrollTo(target)
        }
    }

    Box(modifier = modifier.fillMaxWidth().onSizeChanged { viewportPx = it.width }) {
        ButtonGroup(
            // Full-width scroll viewport (tabs clip at the screen edge); the md padding is INSIDE the
            // scroll so the resting first/last tabs line up with the card, not jammed against the border.
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            iterations.forEachIndexed { index, iteration ->
                val interaction = remember(iteration.turnIndex) { MutableInteractionSource() }
                val shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    iterations.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ReplanTab(
                    iteration = iteration,
                    selected = iteration.turnIndex == selectedTurn,
                    // The most recent run reads as the live one — accent-coloured + labelled — once there's
                    // history to set it apart from.
                    isLatest = index == iterations.lastIndex && iterations.size > 1,
                    shapes = shapes,
                    interaction = interaction,
                    modifier = Modifier
                        .animateWidth(interaction)
                        .onPlaced { coords ->
                            val b = coords.boundsInParent()
                            spans[iteration.turnIndex] = intArrayOf(b.left.roundToInt(), b.right.roundToInt())
                        },
                    onClick = {
                        if (iteration.turnIndex != selectedTurn) {
                            haptics.tick()
                            onSelect(iteration.turnIndex)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReplanTab(
    iteration: AiIterationUi,
    selected: Boolean,
    isLatest: Boolean,
    shapes: ToggleButtonShapes,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // The latest run wears the primary accent; older history reads in the quieter secondary.
    val containerColor = if (isLatest) scheme.primaryContainer else scheme.secondaryContainer
    val onContainerColor = if (isLatest) scheme.onPrimaryContainer else scheme.onSecondaryContainer
    val checkedContainerColor = if (isLatest) scheme.primary else scheme.secondary
    val onCheckedColor = if (isLatest) scheme.onPrimary else scheme.onSecondary
    val content = if (selected) onCheckedColor else onContainerColor
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier.heightIn(min = TAB_MIN_HEIGHT),
        shapes = shapes, // connected: selected → full pill, others → rounded-rect / end caps
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = containerColor,
            contentColor = onContainerColor,
            checkedContainerColor = checkedContainerColor,
            checkedContentColor = onCheckedColor,
        ),
        elevation = null,
        interactionSource = interaction,
        contentPadding = PaddingValues(start = Spacing.md, top = Spacing.sm, end = Spacing.xl, bottom = Spacing.sm),
    ) {
        Symbol(symbol = triggerSymbol(iteration.trigger), contentDescription = null, tint = content, size = ICON_GLYPH)
        Spacer(Modifier.width(Spacing.sm))
        Column {
            // Clip (not Ellipsis) so the transient animateWidth squeeze on press/bounce doesn't flicker "…".
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

@Preview(showBackground = true)
@Composable
private fun ReplanHistoryBarPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                PREVIEW_ITER(0, "21:30", "Planned", null),
                PREVIEW_ITER(1, "23:10", "Your schedule changed", TriggerType.CalendarChange),
                PREVIEW_ITER(2, "02:10", "A good moment to wake", TriggerType.WakeWindowOpportunity),
            ),
            selectedTurn = 2,
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}

@Suppress("FunctionName")
private fun PREVIEW_ITER(turn: Int, time: String, message: String, trigger: TriggerType?) = AiIterationUi(
    turnIndex = turn,
    timeLabel = time,
    systemMessage = message,
    trigger = trigger,
    thread = AiThreadUi(turnIndex = turn, summary = message, process = emptyList(), response = null),
)
