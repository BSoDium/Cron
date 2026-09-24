# Floating Card Layout System

## Overview

The floating card layout is a reusable system for displaying stacked, dismissible cards at the top of the home screen that floats above content, with smooth animations and a gradient underlay that dims the content as cards stack.

Currently, only the `NextAlarmCard` occupies this space. This document describes the upgrade to support multiple floating cards (e.g., alarm, commute map, warnings, widgets) without breaking layout or interaction feel.

## Design Principles

### Visual Hierarchy & Depth
- Cards float in a fixed position at the top, anchored to the safe area.
- Cards are constrained to a maximum width (full width with edge spacing matching `Spacing.horizontal`).
- All cards share identical border radii (`Radius.cardCorner`).
- Cards are spaced vertically by `Spacing.md` or consistent card-gap token (to be defined).
- **No elevation shadows.** Depth is conveyed through a gradient overlay that darkens content underneath.

### Content Gliding & Dimming
- Main content scrolls underneath the floating cards without clipping.
- A gradient from 100% darkness at the top (card start) to 0% by a defined distance below cards creates visual separation.
- As cards collapse/dismiss, the gradient retracts smoothly.
- The effect preserves the illusion that content passes underneath the card layer.

### Dismiss & Collapse Behavior
- Cards can be individually dismissed (e.g., warning pills, ephemeral alerts).
- Cards collapse smoothly when dismissed (animate height to 0, opacity out).
- The gradient and card stack reflow without jarring layout shifts.
- Dismissed cards should remain in a "dismissed" state (persisted in DataStore or session state) unless:
  - The underlying data changes (e.g., next alarm time updates → redisplay alarm card).
  - User explicitly re-enables the card.

## Architecture

### Card Registry
Define a sealed class for each floating card type to enable type-safe rendering:

```kotlin
sealed class FloatingCard {
    data class Alarm(val alarm: AlarmUiModel) : FloatingCard()
    data class CommuteMap(val route: RouteInfo) : FloatingCard()
    data class Warning(val warning: WarningCard) : FloatingCard()
}
```

Each card type maps to a composable that knows how to render and dismiss itself.

### Layout Structure
```
┌─────────────────────────────────────────┐
│  FloatingCardStack (fixed position)     │
│  ├─ Card 1 (e.g., Alarm)                │
│  ├─ Spacer(gap)                         │
│  ├─ Card 2 (e.g., Commute)              │
│  └─ Spacer(gap)                         │
├─ Gradient Overlay (scrim, dynamic height)
│  └─ Fades from top card bottom to 0 by Y
└─ Scrollable Content (flows under stack) │
    └─ DismissalGradient clipped          │
```

### ViewModel State
Track floating cards in the home screen ViewModel:

```kotlin
data class HomeUiState(
    // ... existing fields
    floatingCards: List<FloatingCard> = emptyList(),
    dismissedCardIds: Set<String> = emptySet(), // Persisted preference
)
```

Expose flows for:
- `floatingCards` — what cards are currently active.
- `stackHeight` — calculated height of all cards + gaps, used for scroll content padding.
- `gradientHeight` — height of the dimming gradient below cards.

### Composition

#### Root Composable: `FloatingCardScaffold`
Wraps the home screen, manages the card stack + gradient + scrollable content.

```kotlin
@Composable
fun FloatingCardScaffold(
    floatingCards: List<FloatingCard>,
    onDismiss: (FloatingCard) -> Unit,
    gradientHeight: Dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable content underneath
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
        
        // Gradient overlay (dims content beneath cards)
        GradientScrim(
            modifier = Modifier
                .fillMaxWidth()
                .height(gradientHeight)
                .align(Alignment.TopCenter),
            color = Color.Black,
            alpha = 0.1f, // Tunable
        )
        
        // Floating card stack (fixed, above content)
        FloatingCardStack(
            cards = floatingCards,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = Spacing.horizontal),
        )
    }
}
```

#### `FloatingCardStack` Composable
Manages vertical layout, spacing, and collective animations.

```kotlin
@Composable
fun FloatingCardStack(
    cards: List<FloatingCard>,
    onDismiss: (FloatingCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var stackHeightDp by remember { mutableStateOf(0.dp) }
    
    Column(
        modifier = modifier
            .onGloballyPositioned { stackHeightDp = with(density) { it.size.height.toDp() } }
            .verticalScroll(rememberScrollState(), enabled = false), // Not scrollable
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEach { card ->
            FloatingCardItem(
                card = card,
                onDismiss = { onDismiss(card) },
            )
        }
    }
}
```

#### `FloatingCardItem` Composable
Renders individual cards with dismiss animation.

```kotlin
@Composable
fun FloatingCardItem(
    card: FloatingCard,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDismissing = rememberUpdatedState(false) // Track dismiss state
    val heightAnimSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
    val heightDp by animateDpAsState(
        targetValue = if (isDismissing.value) 0.dp else Dp.Unspecified,
        animationSpec = heightAnimSpec,
        label = "floating-card-dismiss-height",
        finishedListener = { if (isDismissing.value) onDismiss() },
    )
    
    Box(
        modifier = modifier
            .height(heightDp)
            .fillMaxWidth(),
    ) {
        when (card) {
            is FloatingCard.Alarm -> NextAlarmCard(
                alarm = card.alarm,
                onDismissRequest = { /* optionally dismissible */ },
            )
            is FloatingCard.CommuteMap -> CommuteMapCard(
                route = card.route,
                onDismissRequest = onDismiss,
            )
            is FloatingCard.Warning -> WarningCard(
                warning = card.warning,
                onDismissRequest = onDismiss,
            )
        }
    }
}
```

## Gradient Overlay Implementation

The gradient underlay darkens content flowing beneath the card stack, reinforcing visual separation without shadows.

```kotlin
@Composable
fun GradientScrim(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    alpha: Float = 0.1f,
) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = with(LocalDensity.current) { 200.dp.toPx() }, // Fade distance, tunable
            ),
        ),
    )
}
```

Clip the gradient to match the card stack's safe area padding so edges align.

## Scroll Behavior

The home screen content must scroll under the floating card stack, not be pushed down by it:

1. Use `Box` layout (not `Column` + vertical spacing).
2. Apply top padding to the scrollable content equal to `stackHeight + gradientHeight` to avoid initial overlap.
3. Animate the padding when cards are added/removed.

```kotlin
val contentTopPadding by animateDpAsState(
    targetValue = calculateStackHeight(floatingCards),
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "card-stack-padding",
)

LazyColumn(
    modifier = Modifier.padding(top = contentTopPadding),
    // ... rest of content
)
```

## State Persistence

### Dismissed Cards
Store dismissed card IDs in `DataStore<HomePreferences>`:

```proto
message HomePreferences {
    repeated string dismissed_floating_cards = 1; // e.g., ["warning_devtools", "settings_changed"]
}
```

When rebuilding the card list each frame:
- Include cards whose IDs are not in `dismissedFloatingCards`.
- Reset dismissal state when underlying data changes (e.g., next alarm updates → always show alarm card, even if previously dismissed).

### Card Ordering
Define a stable priority order (alarm → commute → warnings → custom widgets) so the stack doesn't reflow unexpectedly as new cards arrive.

## Interaction Guidelines

### Multi-Touch & Input
- Cards should not intercept scroll input destined for content below.
- Use `Modifier.pointerInput` sparingly; let Compose's default semantics handle it.
- Ensure tap targets (dismiss buttons, action buttons) are ≥ 48.dp.

### Animation Timing
- Pull all animation specs from `MaterialTheme.motionScheme` (springs for Expressive).
- Label every animation (e.g., `"floating-card-dismiss-height"`, `"card-stack-padding"`).
- See `docs/animation-previews.md` for animation preview requirements.

### Accessibility
- Ensure cards have semantic identities: `semantics { contentDescription = "Next alarm card" }`.
- Dismiss actions must be keyboard-accessible.
- The gradient overlay should not impact text contrast; test with real content.

## Testing & Validation

### Screenshots
Capture Roborazzi screenshots for key states:
- Single card (alarm only).
- Multiple cards (alarm + commute + warning).
- Collapsed/dismissed state (smooth animation).
- Content scrolling under cards (grid/list visible through gradient).

See `docs/screenshot-testing.md` for instructions.

### Edge Cases
- Empty card list (stack collapses, no gradient).
- Very tall card (e.g., tall map) — ensure content remains accessible.
- Rapid dismiss/re-add (animations chain smoothly).
- Orientation change (cards reflow, padding recalculated).

## Future Enhancements

1. **Swipe-to-dismiss**: Add horizontal swipe gesture for quicker card dismissal.
2. **Custom widgets**: Allow third-party card definitions (via a plugin interface).
3. **Card reordering**: Let users customize card priority (drag-and-drop in a settings UI).
4. **Haptic feedback**: Vibrate on dismiss or card arrival.
