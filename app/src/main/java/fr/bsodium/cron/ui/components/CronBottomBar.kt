package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Square
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Floating bottom action bar: a pill housing the three tab icons, optionally
 * paired with a square FAB to the right that hosts the primary action for the
 * current screen (currently only Home, which uses it to re-run the AI plan).
 *
 * Sits above the system gesture/navigation inset rather than docking to the
 * bottom edge, matching the Material 3 Expressive "floating" pattern.
 */
@Composable
fun CronFloatingNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    fabAction: FabAction?,
    modifier: Modifier = Modifier,
) {
    val systemBars: PaddingValues = WindowInsets.navigationBars.asPaddingValues()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = systemBars.calculateBottomPadding())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavPill(currentRoute = currentRoute, onNavigate = onNavigate)
        FabSlot(fabAction)
    }
}

/**
 * Slot for the play FAB whose width animates between [FAB_SLOT_WIDTH] (FAB +
 * leading gap) and 0.dp. With a `Arrangement.Center` parent Row, this lets the
 * pill+FAB block re-center smoothly when the FAB hides on non-Home tabs.
 */
@Composable
private fun FabSlot(fabAction: FabAction?) {
    val visible = fabAction != null
    val width by animateDpAsState(
        targetValue = if (visible) FAB_SLOT_WIDTH else 0.dp,
        animationSpec = FAB_SLOT_SPEC,
        label = "fab-slot-width",
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(FAB_SLOT_HEIGHT),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = FAB_ENTER,
            exit = FAB_EXIT,
        ) {
            PrimaryActionFab(fabAction)
        }
    }
}

private val FAB_SLOT_WIDTH = 68.dp // 56dp FAB + 12dp leading gap
private val FAB_SLOT_HEIGHT = 56.dp
private val NAV_SLOT_SIZE = 48.dp
private val FAB_SLOT_SPEC = tween<Dp>(durationMillis = 200, easing = FastOutSlowInEasing)
// A pronounced grow-from-centre + fade so the FAB pops rather than slides in.
private val FAB_ENTER =
    fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.5f)
private val FAB_EXIT =
    fadeOut(tween(140)) + scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = 0.5f)

data class FabAction(
    val onClick: () -> Unit,
    val working: Boolean = false,
    val onCancel: (() -> Unit)? = null,
    /** When set, an onboarding callout with this text points at the FAB (see [OnboardingTooltip]). */
    val hint: String? = null,
)

@Composable
private fun NavPill(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = Radius.full,
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs + Spacing.xxs, vertical = Spacing.xs + Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSlot(currentRoute, "home", Icons.Filled.Home, Icons.Outlined.Home, "Home", onNavigate)
            NavSlot(currentRoute, "history", Icons.Filled.History, Icons.Outlined.History, "History", onNavigate)
            NavSlot(currentRoute, "settings", Icons.Filled.Settings, Icons.Outlined.Settings, "Settings", onNavigate)
        }
    }
}

@Composable
private fun RowScope.NavSlot(
    currentRoute: String?,
    route: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    onNavigate: (String) -> Unit,
) {
    val selected = currentRoute == route
    // Selected: filled accent indicator (matches the FAB); unselected: outlined icon on the pill.
    val targetContainer = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val targetTint = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    val container by animateColorAsState(targetContainer, animationSpec = NAV_COLOR_SPEC, label = "nav-container")
    val iconTint by animateColorAsState(targetTint, animationSpec = NAV_COLOR_SPEC, label = "nav-tint")
    val haptics = rememberCronHaptics()
    Box(
        modifier = Modifier
            // Square slot so the circular indicator nests with an equal margin on every side
            // (x and y) inside the pill, rather than wider side gaps.
            .size(NAV_SLOT_SIZE)
            // Clip BEFORE clickable so the ripple respects the slot shape.
            .clip(Radius.full)
            .clickable(enabled = !selected) {
                haptics.contextClick()
                onNavigate(route)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(NAV_INDICATOR_SIZE)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = iconTint,
            )
        }
    }
}

private val NAV_COLOR_SPEC = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)
private val NAV_INDICATOR_SIZE = 44.dp

@Composable
private fun PrimaryActionFab(action: FabAction?) {
    if (action == null) return
    val working = action.working
    val haptics = rememberCronHaptics()
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
        shape = RoundedCornerShape(Radius.lg),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
    ) {
        Icon(
            imageVector = if (working) Icons.Outlined.Square else Icons.Outlined.PlayArrow,
            contentDescription = if (working) "Cancel" else "Run alarm plan",
        )
    }
}

// The play FAB's horizontal distance right of screen centre, derived from the centred nav row
// (pill 164dp + 68dp FAB slot, FAB at the slot's far end). Device-width independent.
private val FAB_CENTER_OFFSET = 88.dp
private val POINTER_WIDTH = 16.dp
private val POINTER_HEIGHT = 8.dp

// Downward-pointing triangle for the onboarding callout's tail.
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
            .align(Alignment.BottomCenter)
            .offset(x = FAB_CENTER_OFFSET)
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

@Preview(showBackground = true)
@Composable
private fun CronFloatingNavPreview() {
    CronTheme {
        CronFloatingNav(
            currentRoute = "home",
            onNavigate = {},
            fabAction = FabAction(onClick = {}),
        )
    }
}
