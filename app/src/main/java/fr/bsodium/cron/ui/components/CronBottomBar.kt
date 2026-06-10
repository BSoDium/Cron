package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ROUTE_HISTORY
import fr.bsodium.cron.ROUTE_HOME
import fr.bsodium.cron.ui.screens.settings.SETTINGS_ROOT
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

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
    // Scale pops on a bouncy spatial spring; alpha rides a no-bounce effects spec so the fade reads clean
    // (a bouncy alpha looks muddy). This mirrors how M3's own FAB show/hide separates the two.
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // Retain the last non-null action so the exit transition has a FAB to animate out — AnimatedVisibility
    // recomposes its content with the new null the instant the action clears, which would otherwise leave
    // the exit fading an empty box.
    var lastShown by remember { mutableStateOf(fabAction) }
    if (fabAction != null && fabAction != lastShown) lastShown = fabAction

    val width by animateDpAsState(
        targetValue = if (visible) FAB_SLOT_WIDTH else 0.dp,
        // The pill re-centre stays on the smooth default spatial spec while the FAB itself pops.
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "fab-slot-width",
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(FAB_SLOT_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedVisibility(
            // Measure the FAB at its full size even while the slot width animates, so the entrance reads
            // as a scale-up + fade (not a horizontal squeeze). Aligning the overflow to the START (with the
            // leading gap as start padding) makes the FAB collapse into the empty space to its right rather
            // than drifting LEFT over the pill — End-alignment in a shrinking box pushes it onto the navbar.
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .padding(start = FAB_LEADING_GAP),
            visible = visible,
            enter = scaleIn(scaleSpec, initialScale = FAB_HIDDEN_SCALE) + fadeIn(alphaSpec),
            exit = scaleOut(scaleSpec, targetScale = FAB_HIDDEN_SCALE) + fadeOut(alphaSpec),
        ) {
            PrimaryActionFab(lastShown)
        }
    }
}

private val FAB_LEADING_GAP = Spacing.md // gap between the pill and the FAB
private val FAB_SLOT_WIDTH = 56.dp + FAB_LEADING_GAP // 56dp FAB + leading gap
private val FAB_SLOT_HEIGHT = 56.dp
private val NAV_SLOT_SIZE = 48.dp

// Hidden scale the FAB pops up from / collapses to on enter/exit (crisper than a half-size grow).
private const val FAB_HIDDEN_SCALE = 0.3f

// The play/stop glyph scales in from this as it crossfades on a working-state change.
private const val FAB_ICON_SWAP_SCALE = 0.6f

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
            NavSlot(currentRoute, ROUTE_HOME, MaterialSymbol.Alarm, "Home", onNavigate)
            NavSlot(currentRoute, ROUTE_HISTORY, MaterialSymbol.History, "History", onNavigate)
            // Settings landing route (the graph's start); sub-screen routes hide the pill entirely.
            NavSlot(currentRoute, SETTINGS_ROOT, MaterialSymbol.Settings, "Settings", onNavigate)
        }
    }
}

@Composable
private fun RowScope.NavSlot(
    currentRoute: String?,
    route: String,
    symbol: MaterialSymbol,
    label: String,
    onNavigate: (String) -> Unit,
) {
    val selected = currentRoute == route
    // Selected: filled accent indicator (matches the FAB) with the glyph morphing outline→filled along
    // the FILL axis; unselected: outlined glyph on the pill.
    val targetContainer = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val targetTint = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    val colorSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by animateColorAsState(targetContainer, animationSpec = colorSpec, label = "nav-container")
    val iconTint by animateColorAsState(targetTint, animationSpec = colorSpec, label = "nav-tint")
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "nav-fill",
    )
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
            Symbol(
                symbol = symbol,
                contentDescription = label,
                tint = iconTint,
                fill = fill,
            )
        }
    }
}

private val NAV_INDICATOR_SIZE = 44.dp

@Composable
private fun PrimaryActionFab(action: FabAction?) {
    if (action == null) return
    val working = action.working
    val haptics = rememberCronHaptics()
    // Springy press feedback: squish on touch-down and bounce back on release (Expressive spatial spec).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fab-press",
    )
    // Captured here because AnimatedContent's transitionSpec lambda is not composable.
    val iconScaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val iconAlphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
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
                (scaleIn(iconScaleSpec, initialScale = FAB_ICON_SWAP_SCALE) + fadeIn(iconAlphaSpec)) togetherWith
                    (scaleOut(iconScaleSpec, targetScale = FAB_ICON_SWAP_SCALE) + fadeOut(iconAlphaSpec))
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
            currentRoute = ROUTE_HOME,
            onNavigate = {},
            fabAction = FabAction(onClick = {}),
        )
    }
}
