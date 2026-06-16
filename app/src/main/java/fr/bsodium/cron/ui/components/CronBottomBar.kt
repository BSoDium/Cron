package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ROUTE_HISTORY
import fr.bsodium.cron.ROUTE_HOME
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

/**
 * Floating bottom action bar: a pill housing the three tab icons on the left, with the primary
 * action FAB on the right. The pill centers when the FAB is absent and shifts left when it appears.
 *
 * In debug builds the FAB becomes a split button (main action + chevron mode-selector) via the
 * optional [fabChevron] slot populated by [rememberFabChevron].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronFloatingNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    fabAction: FabAction?,
    modifier: Modifier = Modifier,
    fabChevron: FabChevronSlot? = null,
) {
    val systemBars: PaddingValues = WindowInsets.navigationBars.asPaddingValues()
    val visible = fabAction != null
    val targetFabWidth = when {
        !visible -> 0.dp
        fabChevron != null -> SPLIT_FAB_SLOT_WIDTH
        else -> FAB_SLOT_WIDTH
    }
    val fabSlotWidth by animateDpAsState(
        targetValue = targetFabWidth,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "fab-slot-width",
    )
    // Retain last non-null action so the FAB has content to fade out during the exit transition.
    var lastShown by remember { mutableStateOf(fabAction) }
    if (fabAction != null && fabAction != lastShown) lastShown = fabAction

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = systemBars.calculateBottomPadding())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pillBias by animateFloatAsState(
            targetValue = if (visible) -1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "pill-bias",
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = BiasAlignment(horizontalBias = pillBias, verticalBias = 0f),
        ) {
            NavPill(currentRoute = currentRoute, onNavigate = onNavigate)
        }
        FabSlot(
            fabSlotWidth = fabSlotWidth,
            visible = visible,
            lastShown = lastShown,
            fabChevron = fabChevron,
        )
    }
}

// Extracted from the Row lambda so RowScope.AnimatedVisibility is not in scope.
@Composable
private fun FabSlot(
    fabSlotWidth: Dp,
    visible: Boolean,
    lastShown: FabAction?,
    fabChevron: FabChevronSlot?,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(
        modifier = Modifier
            .width(fabSlotWidth)
            .height(FAB_SLOT_HEIGHT),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            modifier = Modifier.wrapContentWidth(align = Alignment.End, unbounded = true),
            visible = visible,
            enter = slideInHorizontally(spatialSpec) { it } + fadeIn(alphaSpec),
            exit = slideOutHorizontally(spatialSpec) { it } + fadeOut(alphaSpec),
        ) {
            if (fabChevron != null) SplitActionFab(lastShown, fabChevron)
            else PrimaryActionFab(lastShown)
        }
    }
}

private val FAB_SLOT_WIDTH = 56.dp
private val FAB_SLOT_HEIGHT = 56.dp
private val SPLIT_MAIN_WIDTH = 108.dp
private val SPLIT_CHEVRON_WIDTH = 52.dp
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SPLIT_FAB_SLOT_WIDTH = SPLIT_MAIN_WIDTH + SplitButtonDefaults.Spacing + SPLIT_CHEVRON_WIDTH

data class FabAction(
    val onClick: () -> Unit,
    val working: Boolean = false,
    val onCancel: (() -> Unit)? = null,
    /** When set, an onboarding callout with this text points at the FAB (see [OnboardingTooltip]). */
    val hint: String? = null,
)

/**
 * Carries the debug-only chevron slot for the split FAB. Defined in main so [CronFloatingNav] can
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
private fun SplitActionFab(action: FabAction?, fabChevron: FabChevronSlot) {
    if (action == null) return
    val haptics = rememberCronHaptics()
    val iconAlphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val chevronColor by animateColorAsState(
        targetValue = if (fabChevron.isMockActive) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chevron-color",
    )
    val chevronContentColor by animateColorAsState(
        targetValue = if (fabChevron.isMockActive) MaterialTheme.colorScheme.onSecondary
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
            IconTooltip(label = if (action.working) "Cancel" else "Run alarm plan") {
                SplitButtonDefaults.LeadingButton(
                    onClick = {
                        if (action.working) { haptics.reject(); action.onCancel?.invoke() }
                        else { haptics.confirm(); action.onClick() }
                    },
                    modifier = Modifier.size(width = SPLIT_MAIN_WIDTH, height = 56.dp),
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    elevation = null,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    AnimatedContent(
                        targetState = action.working,
                        transitionSpec = {
                            fadeIn(iconAlphaSpec) togetherWith fadeOut(iconAlphaSpec)
                        },
                        contentAlignment = Alignment.Center,
                        label = "split-fab-icon",
                    ) { isWorking ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                Symbol(
                                    symbol = if (isWorking) MaterialSymbol.Stop
                                        else MaterialSymbol.PlayArrow,
                                    contentDescription = null,
                                    fill = 1f,
                                )
                            }
                            Text(
                                text = if (isWorking) "Stop" else "Run",
                                style = MaterialTheme.typography.labelLarge,
                            )
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
private fun PrimaryActionFab(action: FabAction?) {
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
    IconTooltip(label = if (working) "Cancel" else "Run alarm plan") {
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
            modifier = Modifier.graphicsLayer {
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
                targetState = working,
                transitionSpec = {
                    (fadeIn(iconAlphaSpec)) togetherWith (fadeOut(iconAlphaSpec))
                },
                contentAlignment = Alignment.Center,
                label = "fab-icon",
            ) { isWorking ->
                Symbol(
                    symbol = if (isWorking) MaterialSymbol.Stop else MaterialSymbol.PlayArrow,
                    contentDescription = if (isWorking) "Cancel" else "Run alarm plan",
                    fill = 1f,
                )
            }
        }
    }
}

// FAB sits at Spacing.lg from the right screen edge (within Row horizontal padding); its centre
// is 28dp (half of 56dp) inward from there.
private val FAB_RIGHT_INSET = Spacing.lg + 28.dp
private val POINTER_WIDTH = 16.dp
private val POINTER_HEIGHT = 8.dp

private val PointerDown = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

/**
 * Onboarding callout whose pointer sits over the play FAB. Render as the LAST child of the
 * full-screen content [Box] — after [EdgeFades] — so the bottom scrim doesn't fade it out.
 * [navBottom] is the navigation-bar inset so it clears the floating nav.
 */
@Composable
fun BoxScope.OnboardingTooltip(navBottom: Dp, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .offset(x = -FAB_RIGHT_INSET)
            .padding(bottom = navBottom + Spacing.navBarClearance - Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(Radius.md),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
        Box(
            modifier = Modifier
                .size(width = POINTER_WIDTH, height = POINTER_HEIGHT)
                .background(MaterialTheme.colorScheme.primary, PointerDown),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronFloatingNavPreview() {
    CronTheme {
        CronFloatingNav(
            currentRoute = ROUTE_HOME,
            onNavigate = {},
            fabAction = FabAction(onClick = {}),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronFloatingNavNoFabPreview() {
    CronTheme {
        CronFloatingNav(
            currentRoute = ROUTE_HISTORY,
            onNavigate = {},
            fabAction = null,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronFloatingNavSplitPreview() {
    CronTheme {
        val mockState = remember { mutableStateOf(true) }
        val expandedState = remember { mutableStateOf(false) }
        CronFloatingNav(
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
