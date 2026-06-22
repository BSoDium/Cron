# Navigation — transitions & predictive back

The rule set for how screens enter, exit, and respond to the back gesture. Read this before adding a
destination. Three motion classes, one per navigation kind:

Pick the motion by the **relationship between the two screens** (Material's motion system):
fade-through = unrelated / lateral (top-level tabs), shared-axis = hierarchical / sequential (drill-down),
container-transform = an element expanding into its detail. Don't reach for a directional slide between
tabs — a slide implies the screens sit next to each other in space, which top-level tabs don't.

| Navigation kind | Relationship | Motion | Where |
|---|---|---|---|
| Switch between nav-bar tabs (Home / History / Settings) | lateral, unrelated | **fade-through**: outgoing fades + scales to 0.92, incoming fades + scales up (incoming delayed past outgoing → no overlap) | `MainActivity.kt` |
| Drill down a level (Settings → a category) | hierarchical | **shared-axis push**: deeper page slides in from the right, parent recedes left + dims | `SettingsNavGraph.kt` |
| Go back a level (category → Settings), incl. the predictive-back drag | hierarchical | **two-phase predictive back**: current page becomes a rounded card, parent peeks behind; commit slides the card off | `PredictiveBackCard.kt` |

## Why these are `tween`, not `motionScheme`

Every transition below is defined in a `NavGraphBuilder` lambda
(`enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition`). **Those lambdas are
not `@Composable`**, so they structurally cannot read `MaterialTheme.motionScheme`. This is the standing
*sanctioned exception* (see `docs/expressive.md` § Sanctioned exceptions) — use `tween` here, and only
here. Everything inside a screen still pulls from `motionScheme`. (`PredictiveBackCard` drives its motion
with an `Animatable` + `tween` for the same reason — it's a gesture-driven seek, not a component spring.)

Durations: tab fade-through `TAB_OUT_MS = 90` + `TAB_IN_MS = 160` (incoming delayed by the out duration),
`PUSH_MS = 240` (drill-down), `COMMIT_MS = 300` (predictive-back commit).

## 1. Tabs — fade-through

Tabs are unrelated top-level destinations, so they use Material **fade-through**, not a slide. The outgoing
screen fades out + scales down to ~0.92; the incoming fades in + scales up from 0.92, its fade **delayed**
past the outgoing fade so the two never sit fully opaque at once — that delay is what removes the
couple-frame ghost overlap a plain `None` (or a naive crossfade) would leave. All four slots use it, so a
predictive-back gesture between tabs also fade-throughs (a fade has no spatial distance to "finish on
release", so it reads clean at any seek fraction — no clamping or custom handler needed):

```kotlin
val tabEnter: …() -> EnterTransition = {
    fadeIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS, easing = LinearOutSlowInEasing), initialScale = 0.92f)
}
val tabExit: …() -> ExitTransition = {
    fadeOut(tween(TAB_OUT_MS, easing = FastOutLinearInEasing)) +
        scaleOut(tween(TAB_OUT_MS, easing = FastOutLinearInEasing), targetScale = 0.92f)
}
composable(ROUTE_HISTORY, enterTransition = tabEnter, exitTransition = tabExit,
    popEnterTransition = tabEnter, popExitTransition = tabExit) { … }
```

`tabEnter`/`tabExit` live in `MainActivity.kt` and are passed into `settingsGraph(navController, tabEnter,
tabExit)` so `SETTINGS_ROOT` (also a tab) shares them. `SETTINGS_ROOT`'s `exitTransition` branches: a
drill-down to a child uses the shared-axis `pushExit`, anything else (a tab switch) uses `tabExit`.
Onboarding → Home rides the same fade-through.

The floating nav bar itself (shown only on tab destinations, `currentRoute in TAB_ROUTES`) is wrapped in an
`AnimatedVisibility` with a `motionScheme.defaultEffectsSpec()` fade in/out — so leaving for / returning
from a Settings sub-screen fades the bar rather than popping it. That slot is `@Composable`, so it reads
`motionScheme` directly (no NavGraph exception needed).

## 2. Push — drill down

The deeper page enters at full scale from the right and *covers* the parent, which slides a quarter-width
left (parallax, not all the way off) and dims to 65%.

```kotlin
private val pushEnter: EnterSpec = {
    slideInHorizontally(tween(PUSH_MS, easing = EaseOutCubic)) { it } + fadeIn(tween(PUSH_MS / 2))
}
private val pushExit: ExitSpec = {
    slideOutHorizontally(tween(PUSH_MS, easing = EaseOutCubic)) { -it / 4 } +
        fadeOut(tween(PUSH_MS), targetAlpha = 0.65f)
}
```

(`EnterSpec` / `ExitSpec` are the typealiases for
`AnimatedContentTransitionScope<NavBackStackEntry>.() -> Enter/ExitTransition` declared at the top of
`SettingsNavGraph.kt`.)

## 3. Pop + predictive back — the Google-Settings gesture

This is the important one. It is **two-phase**, matching Pixel Settings:

- **Phase 1 — while the back thumb is dragged (progress 0→1):** the current (deep) page shrinks into an
  *opaque* rounded card that shifts toward the gesture edge; the previous (shallow) page is revealed behind
  it, scaled down and dimmed. At **max pull** the card is held — fully readable, nothing has slid off yet.
- **Phase 2 — on release / commit (progress 1→2):** the card slides off-screen and fades while the parent
  de-dims and scales up to full; then the real pop runs.

### Why NOT the built-in seekable predictive back

Navigation Compose's built-in predictive back *seeks* `popExitTransition`/`popEnterTransition` to the drag
progress — it maps the **whole** transition to 0→1. So at max pull the transition is already 100 % done
(page slid off and faded to nothing) and **release does nothing**. It structurally cannot separate a
preview from a commit. Don't reach for it for this effect.

### The pattern: `PredictiveBackCard` + a custom `PredictiveBackHandler`

`ui/screens/settings/components/PredictiveBackCard.kt` drives the two phases by hand and renders two
layers. It wraps each detail screen (via `SettingsDetailScaffold`), which automatically scopes the card to
sub-pages — tab destinations never get it. Key pieces:

```kotlin
val progress = remember { Animatable(0f) }   // 0 rest · 1 held preview · 2 committed
PredictiveBackHandler(enabled = !committing) { events ->
    try {
        events.collect { e -> progress.snapTo(decelerate(e.progress)) }   // drag → track finger (0→1)
        progress.animateTo(2f, tween(COMMIT_MS)); onBack()                 // release → commit (1→2), then pop
    } catch (c: CancellationException) {
        progress.animateTo(0f, tween(CANCEL_MS)); throw c                  // cancel → snap back
    }
}
```

Transforms read `progress()` in the **layer pass** (a `() -> Float` provider into `graphicsLayer`), so the
drag never recomposes the content. The split is `preview = p.coerceIn(0,1)` and `commit = (p-1).coerceIn(0,1)`:
card scale `lerp(1, 0.9, preview)`, card alpha `1 - commit`, card `translationX` small toward the edge in
preview then off-screen in commit, corner radius `Radius.xl * preview`. The parent scales `lerp(0.92, 1,
commit)`, is **shifted left a constant amount for the whole hold** (`-shift * (1 - commit)`, ~18 % of width
— *not* scaled by `preview`, so the destination is parked left and visible from the first frame) and slides
back to centre on release, under a dim scrim `MAX_DIM * (1 - commit)`. The lifted dark surface (Theme.kt
`liftedSurfaces`) is what makes the dimmed parent read against the opaque card — without it everything is
flat near-black.

The app-bar back arrow and the system back button call the **same** commit animation (the wrapper hands
`content` an `animatedBack` callback), so every way out looks identical — a non-gesture back just runs the
full 0→2 (scale-down then slide-off) with no held preview.

### Rendering the parent behind — the stateless-screen trick

The parent layer is just a second `SettingsScreen(onOpenCategory = {})`. That works **only because
`SettingsScreen` is completely stateless** — a static category list, no ViewModel, no DB/network — so a
second instance is free and pixel-identical to the real root. When the commit's `onBack()` finally pops,
NavHost swaps in the real root *under* the finished animation (its pop transition is `None`); since both are
the same composable, the hand-off is invisible. **If your parent screen is stateful, this trick doesn't
apply** — you'd need to hoist its state or snapshot it.

The detail scaffold keeps `containerColor = MaterialTheme.colorScheme.background` (opaque) so the card reads
as a solid surface lifted over the dimmed parent; the rounding is applied (animated) by the card's
`graphicsLayer`, not a static `clip`.

## 4. Mixed destinations (tab **and** drill-down parent)

`SETTINGS_ROOT` is both a tab (fade-through) and the parent of the category screens (push). Each slot does
a different job, so they don't all get `tabEnter`/`tabExit`:

```kotlin
composable(
    SETTINGS_ROOT,
    enterTransition    = tabEnter,                                                  // tab switch in → fade-through
    exitTransition     = { if (targetState.isSettingsChild()) pushExit() else tabExit() }, // drill-down push / tab away
    popEnterTransition = { EnterTransition.None },   // child pop → PredictiveBackCard owns the reveal (see below)
    popExitTransition  = tabExit,                    // tab switch away (→ Home) → fade-through
) { … }

private fun NavBackStackEntry.isSettingsChild() =
    destination.parent?.route == SETTINGS_GRAPH && destination.route != SETTINGS_ROOT
```

**`popEnter` must be `None`, not `tabEnter`.** It only fires when a child detail is popped back to the
root, and that reveal is already animated by the child's `PredictiveBackCard` (which scales its own copy of
the root up to full). Putting *any* enter transition here makes the real root re-animate (e.g. `scaleIn`
from 0.92) on top of the card's copy once `onBack()` lands — a ghostly scaled copy that "merges" into the
page. Leave it `None` so the real root just appears under the finished card animation.

## 5. Template — a new nested graph

Copy this shape. NavHost owns the **forward** push; **back** is owned by `PredictiveBackCard`, so every pop
transition is `None`. The detail helper folds the params so each screen stays a one-liner.

```kotlin
private const val PUSH_MS = 240
private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec  = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

// pushEnter / pushExit as in §2.

private fun NavGraphBuilder.fooDetail(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition    = pushEnter,
    exitTransition     = pushExit,
    popEnterTransition = { EnterTransition.None },   // pop visual is owned by PredictiveBackCard
    popExitTransition  = { ExitTransition.None },
    content = content,
)
```

A detail screen wanting the card effect wraps its scaffold in `PredictiveBackCard` (reuse
`SettingsDetailScaffold`), and its parent screen should be cheap to render a second time (see §3's
stateless-screen note).

## 6. Stateful screens — the Home pattern

When the parent screen is **stateful** (ViewModel-backed flows, markdown parsing, SubcomposeLayout probes,
font resolution), rendering a second instance as `parentContent` — like Settings does — is not viable: you'd
need to hoist all state or snapshot the composition, and the cost is not "free" the way a static category
list is. Home's plan-detail navigation solves this differently:

**Local state, not NavHost.** Plan detail is managed via `detailKey: PlanDetailKey?` in `HomeScreen`.
`HomeRootContent` is always composed in a `Box` behind the detail. When `detailKey` is set,
`PredictiveBackCard` overlays on top — its scrim naturally dims the live home during the gesture. No
snapshot, no second instance needed.

```kotlin
Box(Modifier.fillMaxSize()) {
    HomeRootContent(…)                                  // always composed

    detailKey?.let { key ->
        Box(Modifier.graphicsLayer { translationX = enterOffset * size.width }) {
            PredictiveBackCard(onBack = { detailKey = null }) { animatedBack ->
                PlanDetailScreen(…, onBack = animatedBack)
            }
        }
    }
}
```

**Forward entrance** uses `animateFloatAsState` with `motionScheme.defaultSpatialSpec()` to slide the detail
in from the right — the same visual direction as the Settings push, but driven by Compose state rather than
NavHost transitions (because NavHost isn't involved).

**Why the `PredictiveBackCard` has no `parentContent` here:** with the home always composed behind, the
card's scrim reveals the live home directly. `parentContent` would add a second stateful instance.

### What was tried and why it failed

Three approaches were attempted before arriving at the always-composed solution:

1. **Navigation 3 `NavDisplay`** — registers a `NavigationBackHandler` (from `androidx.navigationevent`)
   that intercepts the back gesture even with `None togetherWith None` transitions. It also corrupts
   GraphicsLayer snapshots via its exit animation. Abandoned entirely.

2. **`rememberGraphicsLayer()` snapshot** — `drawWithContent { record(); drawLayer() }` to capture the home
   screen as a bitmap during the back gesture. Child render nodes are destroyed when the composable leaves
   composition, making the display list stale — `drawLayer()` renders nothing.

3. **Conditional composition** — `if (detailKey == null) { HomeRootContent(…) }`. Causes a layout flash on
   return: `mutableIntStateOf(0)` values (`cardFullHeightPx`, `greetingHeightPx`) reset when the composable
   re-enters composition, so the alarm card jumps for a frame.

The working solution is the simplest. First-composition is expensive but ongoing recomposition is cheap, so
keeping the home composed behind the detail costs nothing.

## Verifying

Predictive back's *in-progress* (drag) animation needs **API 34+**; on older devices and the system back
button the commit animation still plays (progress 0→2). On device:

- Tab back gestures (History→Home, Settings-root→Home) — **no card, instant**.
- Detail back **drag**: page shrinks into an opaque rounded card, real parent dimmed behind; **hold at max
  pull** stays a lifted card; **release** slides the card off and brings the parent to full; **cancel**
  snaps back.
- Detail **back-arrow** and **system back** play the same commit animation.
- Forward drill-down still slides in from the right; no flicker at the pop hand-off.
