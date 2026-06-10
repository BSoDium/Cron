# Navigation — transitions & predictive back

The rule set for how screens enter, exit, and respond to the back gesture. Read this before adding a
destination. Three motion classes, one per navigation kind:

| Navigation kind | Motion | Where |
|---|---|---|
| Switch between nav-bar tabs (Home / History / Settings) | **none — instant** | `MainActivity.kt` |
| Drill down a level (Settings → a category) | **push**: deeper page slides in from the right, parent recedes left + dims | `SettingsNavGraph.kt` |
| Go back a level (category → Settings), incl. the predictive-back drag | **pop**: current page scales into a rounded card and slides right; parent scales up from behind | `SettingsNavGraph.kt` |

## Why these are `tween`, not `motionScheme`

Every transition below is defined in a `NavGraphBuilder` lambda
(`enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition`). **Those lambdas are
not `@Composable`**, so they structurally cannot read `MaterialTheme.motionScheme`. This is the standing
*sanctioned exception* (see `docs/expressive.md` § Sanctioned exceptions) — use `tween` here, and only
here. Everything inside a screen still pulls from `motionScheme`.

Durations: `PUSH_MS = 240` (drill-down, "pretty fast" so it never drags), `POP_MS = 300` (back / commit).
Easing: `EaseOutCubic` for the directional movement.

## 1. Tabs — instant

Tab destinations never animate; the data already lives in a surviving ViewModel, so any transition is pure
latency. Set both directions to `None`:

```kotlin
composable(
    ROUTE_HISTORY,
    enterTransition = { EnterTransition.None },
    exitTransition  = { ExitTransition.None },
) { … }
```

The single exception is the one-off onboarding → Home hand-off, which fades (`forwardTween`, 350ms). Home's
`enterTransition` branches on `initialState.destination.route == ROUTE_ONBOARDING` to fade only in that
case and stay instant for tab switches.

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

This is the important one. The behaviour we want, matching Pixel Settings:

- **As the back thumb is pulled:** the current (deep) page scales down into a rounded-rectangle card while
  the previous (shallow) page peeks behind it — dimmed, shifted slightly left, scaled down.
- **On release (commit):** the deep card slides right and fades out; the shallow page de-dims, slides back,
  and scales up to full.

**We get this for free from Navigation Compose's built-in seekable predictive back — no custom
`PredictiveBackHandler`, no manual two-layer rendering.** The requirements:

1. `android:enableOnBackInvokedCallback="true"` on `<application>` (already set).
2. Navigation Compose ≥ 2.8 (we're on 2.8.5).
3. Define `popExitTransition` and `popEnterTransition`. NavHost composes **both** destinations during the
   gesture and *seeks* these two transitions to the gesture progress — that seeking is what reveals the
   real previous page behind the shrinking card.

Why it reads as two phases even though it's one continuous transition: the user only ever pulls **partway**
before releasing, so during the drag the scale-down dominates (small progress = small slide), and the
remaining slide + fade play out on the commit animation from wherever the finger let go. "The rest happens
on release" falls out of the commit, for free.

```kotlin
private val popExit: ExitSpec = {                         // the deep page leaving — seeked during the drag
    scaleOut(tween(POP_MS, easing = EaseOutCubic), targetScale = 0.90f) +   // → rounded card
        slideOutHorizontally(tween(POP_MS, easing = EaseOutCubic)) { it / 3 } +  // → right, mostly on release
        fadeOut(tween(POP_MS))
}
private val popEnter: EnterSpec = {                        // the shallow page revealed behind
    scaleIn(tween(POP_MS, easing = EaseOutCubic), initialScale = 0.92f) +
        slideInHorizontally(tween(POP_MS, easing = EaseOutCubic)) { -it / 6 } + // slightly left
        fadeIn(tween(POP_MS), initialAlpha = 0.55f)                             // dimmed peek → full
}
```

### The rounded-card trick

The scale-down only *looks* like a card if the shrinking page is opaque and rounded. In
`SettingsDetailScaffold.kt`:

```kotlin
Scaffold(
    modifier = modifier.fillMaxSize()
        .clip(RoundedCornerShape(Radius.xl))                 // 28dp
        .nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.background,   // opaque, == app background
    …
)
```

Two non-obvious points, both load-bearing:

- **Opaque background, not transparent.** During the pop NavHost draws the leaving page *on top of* the
  entering one. A transparent leaving page would show the parent *through* it; an opaque one reads as a
  solid card with the dimmed parent visible *around* it in the margins the scale-down opens up.
- **Container colour == the app background.** At rest (scale 1.0) the page fills the screen, so the rounded
  clip cuts background-coloured pixels against the identical background behind it — the corners are
  **invisible**. They only become visible once the page shrinks and a *different* (dimmed parent) colour
  sits behind the corners. So the clip can be always-on; no transition-state plumbing needed.

## 4. Mixed destinations (tab **and** drill-down parent)

`SETTINGS_ROOT` is both a tab (instant in/out) and the parent of the category screens (push). Its
`exitTransition` must branch on where it's going:

```kotlin
composable(
    SETTINGS_ROOT,
    enterTransition    = { EnterTransition.None },                                  // tab switch in
    exitTransition     = { if (targetState.isSettingsChild()) pushExit() else ExitTransition.None },
    popEnterTransition = popEnter,                                                   // a child popped back
    popExitTransition  = { ExitTransition.None },                                    // popped to Home → instant
) { … }

private fun NavBackStackEntry.isSettingsChild() =
    destination.parent?.route == SETTINGS_GRAPH && destination.route != SETTINGS_ROOT
```

## 5. Template — a new nested graph

Copy this shape. Push for forward, predictive-back pop for return; the detail helper folds the four params
so each screen stays a one-liner.

```kotlin
private const val PUSH_MS = 240
private const val POP_MS = 300
private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec  = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

// pushEnter / pushExit / popExit / popEnter as above.

private fun NavGraphBuilder.fooDetail(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition    = pushEnter,
    exitTransition     = pushExit,
    popEnterTransition = { EnterTransition.None },   // sub-screens aren't re-entered via pop
    popExitTransition  = popExit,
    content = content,
)
```

A detail screen wanting the card effect should use a scaffold with the opaque-background + rounded-clip
pattern from §3 (e.g. reuse `SettingsDetailScaffold`).

## Verifying

Predictive back's *in-progress* animation needs **API 34+**; on older devices it animates only on commit.
On device: tab switches are instant; a drill-down slides in from the right and the parent dims; a
back-thumb pull shrinks the page into a rounded card with the parent dimmed behind it, completing on
release and snapping back cleanly on cancel.
