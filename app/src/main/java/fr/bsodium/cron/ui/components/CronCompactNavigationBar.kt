package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ROUTE_HOME
import fr.bsodium.cron.ROUTE_MEMORY
import fr.bsodium.cron.ui.screens.settings.SETTINGS_ROOT
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Floating bottom action bar: a pill housing the tab icons on the left, with the primary
 * action FAB on the right. The pill centers when the FAB is absent and shifts left when it appears.
 *
 * In debug builds the FAB becomes a split button (main action + chevron mode-selector) via the
 * optional [fabChevron] slot populated by [rememberFabChevron].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronCompactNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    fabAction: FabAction?,
    modifier: Modifier = Modifier,
    fabChevron: FabChevronSlot? = null,
) {
    val systemBars: PaddingValues = WindowInsets.navigationBars.asPaddingValues()
    val density = LocalDensity.current
    var measuredFabWidth by remember { mutableStateOf(0.dp) }
    // Retain last non-null action so the FAB has content to fade out during the exit transition.
    var lastShown by remember { mutableStateOf(fabAction) }
    if (fabAction != null && fabAction != lastShown) lastShown = fabAction
    /**
     * Tab navigation updates the route before the destination publishes its action. Keep the
     * existing slot through that handoff so Home <-> Memory morphs the FAB instead of moving the pill.
     */
    val visible = fabAction != null ||
            (currentRoute == ROUTE_HOME || currentRoute == ROUTE_MEMORY) && lastShown != null
    val fabSlotWidth by animateDpAsState(
        targetValue = if (visible) measuredFabWidth else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fab-slot-width",
    )
    val pillBias by animateFloatAsState(
        targetValue = if (visible) -1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pill-bias",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = systemBars.calculateBottomPadding())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = BiasAlignment(
                horizontalBias = pillBias,
                verticalBias = 0f,
            ),
        ) {
            NavPill(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
            )
        }

        FabSlot(
            modifier = Modifier.align(Alignment.CenterEnd),
            fabSlotWidth = fabSlotWidth,
            visible = visible,
            lastShown = lastShown,
            fabChevron = fabChevron,
            onWidthMeasured = {
                measuredFabWidth = with(density) { it.toDp() }
            },
        )
    }
}

@Composable
private fun FabSlot(
    fabSlotWidth: Dp,
    visible: Boolean,
    lastShown: FabAction?,
    fabChevron: FabChevronSlot?,
    onWidthMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(
        modifier = modifier
            .width(fabSlotWidth)
            .height(FAB_SLOT_HEIGHT),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            modifier = Modifier.wrapContentWidth(align = Alignment.End, unbounded = true),
            visible = visible,
            enter = slideInHorizontally(spatialSpec) { it } + fadeIn(alphaSpec),
            exit = slideOutHorizontally(spatialSpec) { it } + fadeOut(alphaSpec),
            label = "fab-visibility",
        ) {
            Box(Modifier.onSizeChanged { onWidthMeasured(it.width) }) {
                if (fabChevron != null) SplitActionFab(lastShown, fabChevron)
                else PrimaryActionFab(lastShown)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronCompactNavigationBarPreview() {
    CronPreview {
        CronCompactNavigationBar(
            currentRoute = ROUTE_HOME,
            onNavigate = {},
            fabAction = FabAction(onClick = {}),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronCompactNavigationBarNoFabPreview() {
    CronPreview {
        CronCompactNavigationBar(
            currentRoute = SETTINGS_ROOT,
            onNavigate = {},
            fabAction = null,
        )
    }
}
