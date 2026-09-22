@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ROUTE_HOME
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

internal val FAB_SLOT_HEIGHT = 56.dp
internal val SPLIT_CHEVRON_WIDTH = 56.dp

data class FabAction(
    val onClick: () -> Unit,
    val working: Boolean = false,
    val onCancel: (() -> Unit)? = null,
    /** Idle label shown in the primary (non-split) FAB. */
    val label: String = "Re-plan",
    /** Shorter label for the split FAB (dev mode); falls back to [label]. */
    val splitLabel: String = label,
    /** Idle icon — overrides the default [MaterialSymbol.Update]. */
    val icon: MaterialSymbol = MaterialSymbol.Update,
    /** Whether [icon] renders filled or outlined. Defaults to filled, matching Home's rocket. */
    val filled: Boolean = true,
    /** Accessible tooltip text, independent from the visible FAB label. */
    val tooltipLabel: String = label,
)

/**
 * What a FAB button is showing right now — either the working ("Stop") overlay or the resolved
 * idle icon/label. A single [AnimatedContent] keyed on this (rather than on [FabAction.working]
 * alone) means a tab switch that swaps in a different screen's [FabAction] — same as a
 * working/idle flip — crossfades and scales through [fabContentTransition] instead of snapping,
 * so two different FAB identities read as one continuous morph (docs/expressive.md).
 */
internal data class FabButtonDisplay(
    val working: Boolean,
    val icon: MaterialSymbol,
    val label: String,
    val filled: Boolean
)

internal fun fabContentTransition(
    alphaSpec: FiniteAnimationSpec<Float>,
    spatialSpec: FiniteAnimationSpec<Float>,
    sizeSpec: FiniteAnimationSpec<IntSize>,
): AnimatedContentTransitionScope<FabButtonDisplay>.() -> ContentTransform = {
    ((fadeIn(alphaSpec) + scaleIn(spatialSpec, initialScale = 0.8f)) togetherWith
            (fadeOut(alphaSpec) + scaleOut(spatialSpec, targetScale = 0.8f)))
        .using(SizeTransform(clip = false) { _, _ -> sizeSpec })
}

/**
 * Carries the debug-only chevron slot for the split FAB. Defined in main so [CronCompactNavigationBar] can
 * accept it; populated by [rememberFabChevron] from the debug/release source sets.
 *
 * [isMockActiveState] is a [State] reference so reads of [isMockActive] inside [SplitActionFab]
 * are tracked by Compose — changing the mock preference propagates the color change instantly.
 */
class FabChevronSlot(
    private val isMockActiveState: State<Boolean>,
    private val isExpandedState: State<Boolean>,
    val onExpandedChange: (Boolean) -> Unit,
    val menuContent: @Composable () -> Unit,
) {
    val isMockActive: Boolean get() = isMockActiveState.value
    val isExpanded: Boolean get() = isExpandedState.value
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SplitActionFab(action: FabAction?, fabChevron: FabChevronSlot) {
    if (action == null) return
    val haptics = rememberCronHaptics()
    val iconAlphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val contentSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    var idleLabel by remember { mutableStateOf(action.splitLabel) }
    var idleIcon by remember { mutableStateOf(action.icon) }
    if (!action.working) {
        idleLabel = action.splitLabel; idleIcon = action.icon
    }
    val display = if (action.working) {
        FabButtonDisplay(working = true, icon = MaterialSymbol.Stop, label = "Stop", filled = true)
    } else {
        FabButtonDisplay(
            working = false,
            icon = idleIcon,
            label = idleLabel,
            filled = action.filled
        )
    }
    val chevronColor by animateColorAsState(
        targetValue = if (fabChevron.isExpanded && fabChevron.isMockActive)
            MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chevron-color",
    )
    val chevronContentColor by animateColorAsState(
        targetValue = if (fabChevron.isExpanded && fabChevron.isMockActive)
            MaterialTheme.colorScheme.onSecondary
        else MaterialTheme.colorScheme.onPrimary,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chevron-content-color",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (fabChevron.isExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevron-rotation",
    )
    SplitButtonLayout(
        leadingButton = {
            IconTooltip(label = if (action.working) "Cancel" else action.tooltipLabel) {
                SplitButtonDefaults.LeadingButton(
                    onClick = {
                        if (action.working) {
                            haptics.reject(); action.onCancel?.invoke()
                        } else {
                            haptics.confirm(); action.onClick()
                        }
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription =
                                if (action.working) "Cancel" else action.tooltipLabel
                        },
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    elevation = null,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    AnimatedContent(
                        targetState = display,
                        transitionSpec = fabContentTransition(
                            iconAlphaSpec,
                            contentSpatialSpec,
                            sizeSpec
                        ),
                        contentAlignment = Alignment.Center,
                        label = "split-fab-content",
                    ) { d ->
                        Row(
                            modifier = Modifier.padding(end = Spacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Symbol(
                                symbol = d.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
                                fill = if (d.filled) 1f else 0f,
                            )
                            Column {
                                Text(
                                    text = d.label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                AnimatedVisibility(
                                    label = "mock-badge",
                                    visible = !d.working && fabChevron.isMockActive,
                                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                            slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                                            expandVertically(
                                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                                clip = false
                                            ),
                                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                            slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                                            shrinkVertically(
                                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                                clip = false
                                            ),
                                ) {
                                    Text(
                                        text = "Mocked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TrailingButton(
                    checked = fabChevron.isExpanded,
                    onCheckedChange = { haptics.contextClick(); fabChevron.onExpandedChange(it) },
                    modifier = Modifier
                        .size(width = SPLIT_CHEVRON_WIDTH, height = 56.dp)
                        .semantics {
                            contentDescription = if (fabChevron.isMockActive)
                                "Mock run active — tap to change"
                            else "Run mode — tap to change"
                        },
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = chevronColor,
                        contentColor = chevronContentColor,
                    ),
                    elevation = null,
                ) {
                    Symbol(
                        symbol = MaterialSymbol.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        fill = 1f,
                    )
                }
                fabChevron.menuContent()
            }
        },
    )
}

@Composable
internal fun PrimaryActionFab(action: FabAction?) {
    if (action == null) return
    val working = action.working
    val haptics = rememberCronHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fab-press",
    )
    val iconAlphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val contentSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    var idleLabel by remember { mutableStateOf(action.label) }
    var idleIcon by remember { mutableStateOf(action.icon) }
    if (!working) {
        idleLabel = action.label; idleIcon = action.icon
    }
    val display = if (working) {
        FabButtonDisplay(working = true, icon = MaterialSymbol.Stop, label = "Stop", filled = true)
    } else {
        FabButtonDisplay(
            working = false,
            icon = idleIcon,
            label = idleLabel,
            filled = action.filled
        )
    }
    IconTooltip(label = if (working) "Cancel" else action.tooltipLabel) {
        FloatingActionButton(
            onClick = {
                if (working) {
                    haptics.reject()
                    action.onCancel?.invoke()
                } else {
                    haptics.confirm()
                    action.onClick()
                }
            },
            modifier = Modifier
                .wrapContentWidth()
                .height(FAB_SLOT_HEIGHT)
                .semantics {
                    contentDescription = if (working) "Cancel" else action.tooltipLabel
                }
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
            shape = RoundedCornerShape(Radius.lg),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
            interactionSource = interaction,
        ) {
            AnimatedContent(
                targetState = display,
                transitionSpec = fabContentTransition(iconAlphaSpec, contentSpatialSpec, sizeSpec),
                contentAlignment = Alignment.Center,
                label = "fab-content",
            ) { d ->
                Row(
                    modifier = Modifier.padding(end = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Symbol(
                        symbol = d.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
                        fill = if (d.filled) 1f else 0f,
                    )
                    Text(
                        text = d.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronCompactNavigationBarSplitPreview() {
    CronPreview {
        val mockState = remember { mutableStateOf(true) }
        val expandedState = remember { mutableStateOf(false) }
        CronCompactNavigationBar(
            currentRoute = ROUTE_HOME,
            onNavigate = {},
            fabAction = FabAction(onClick = {}),
            fabChevron = FabChevronSlot(
                isMockActiveState = mockState,
                isExpandedState = expandedState,
                onExpandedChange = { expandedState.value = it },
                menuContent = {},
            ),
        )
    }
}
