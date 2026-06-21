# Material 3 Expressive — motion & style for Cron

Cron targets the **Material 3 Expressive** update, not static Material 3. The default failure mode of an LLM pass is to reach for plain M3 (hardcoded `tween`s, `primary` everywhere, no springy feedback). This file is the rule set that prevents that. Read it before adding or animating any component.

The app wraps `MaterialExpressiveTheme` (`ui/theme/Theme.kt`), so the Expressive motion scheme, shapes, and component variants are already in scope — you just have to use them.

## Motion: pull every spec from the motion scheme

Because the theme is Expressive, `MaterialTheme.motionScheme` resolves to the bouncy Expressive springs. **Get animation specs from it — never hand-roll them.**

- **Spatial** — anything that *moves*: position, size, scale, offset, rotation, shape morph, layout. Use `MaterialTheme.motionScheme.fastSpatialSpec<T>()`, `defaultSpatialSpec<T>()`, or `slowSpatialSpec<T>()`.
- **Effects** — anything that *doesn't move*: color, alpha, tint. Use `fastEffectsSpec<T>()`, `defaultEffectsSpec<T>()`, or `slowEffectsSpec<T>()`.

```kotlin
val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spec, label = "press")
```

**Do not** write `tween(…, FastOutSlowInEasing)` or `spring(dampingRatio = …, stiffness = …)` literals for UI motion — replace them with a scheme spec. (A one-off non-visual timing, e.g. a debounce, is fine.)

What the scheme resolves to under Expressive (for intuition — don't hardcode these, just know the feel):

| token | damping | stiffness | feel |
| --- | --- | --- | --- |
| FastSpatial | 0.6 | 800 | bouncy, quick — press/selection feedback |
| DefaultSpatial | 0.8 | 380 | smooth, natural — most layout/shape moves |
| SlowSpatial | 0.8 | 200 | languid — hero reveals/morphs |
| *Effects | 1.0 | high | no bounce, snappy — color/alpha |

Rules of thumb: press & selection feedback → `fastSpatialSpec`; enter/exit pops & moving elements → spatial; color/alpha crossfades → effects.

## Components: reach for the Expressive variant

- **Reactive button groups** — wrap children in the `ButtonGroup` layout and put `Modifier.animateWidth(interactionSource)` on each child (sharing the child's own `interactionSource`). Pressing one expands it and **squishes its neighbours** — the signature Expressive bounce. A plain `Row` of buttons does **not** do this; it only gets per-button shape morph.
- **Selectable buttons** — `ToggleButton` with `ToggleButtonDefaults.shapes()`, or connected via `ButtonGroupDefaults.connectedLeading/Middle/TrailingButtonShapes()`. It morphs shape on press/check (round → squarer) using a FastSpatial spring for free.
- **Shapes** — `MaterialShapes` + `androidx.graphics.shapes.Morph` are the shape vocabulary (see `ThinkingShape.drawMorph`). Morph between two `MaterialShapes` for a state change rather than swapping a static shape.
- **FAB / icon buttons** — the Expressive `FloatingActionButton`; add a press-scale spring; keep it flat.

## Color: apply varied accents

Dynamic color (Android 12+) generates `primary`, `secondary`, `tertiary` and their `*Container` roles from the wallpaper. **Use them** — painting everything `primary` is what makes the app read monochrome.

- Inject variety with `secondaryContainer`/`tertiaryContainer` + their `on*` roles at focal points; keep boldness **restrained** (containers, not saturated fills).
- Map semantic categories to accent roles so colour carries meaning (e.g. event-trigger categories → distinct accents).
- Keep the non-dynamic fallback scheme's `secondary`/`tertiary` distinct too, so older devices aren't monochrome.

## Shape & flatness

Rounded, MaterialShapes-based. **Flat**: no `Modifier.shadow`, no `shadowElevation > 0`, no non-zero FAB elevation — convey depth with `tonalElevation` and `surfaceContainer*` tones only (see CLAUDE.md).

## Adding a component — checklist

- [ ] Motion specs come from `MaterialTheme.motionScheme` (spatial for movement, effects for colour) — zero hardcoded `tween`/`spring`.
- [ ] Used the Expressive variant where one exists (`ButtonGroup` + `animateWidth`, `ToggleButton`, Expressive FAB).
- [ ] Press/selection has spring feedback.
- [ ] Uses accent roles (not only `primary`); flat; `Spacing`/`Radius` tokens; ships a `@Preview`.
- [ ] Every animation call has a `label`; animated components ship an interactive `@Preview` (enforced by `checkAnimationPreviews`).

## Sanctioned exceptions to the motionScheme rule

The "zero hardcoded tween/spring" rule has exactly these standing exceptions — each exists because a
spring is *structurally* wrong for the job, not as a tuning preference. Anything not listed here uses
`MaterialTheme.motionScheme`. When adding one, list it here and reference this section from the code.

- **Duration-coupled pairs** (`ThinkingShape.kt` writing morph + spin): two animations that must end on
  the exact same frame. Springs expose no fixed duration, so the pair shares a `tween` duration.
- **Magnetic settles** (`AlarmCollapseEffects.kt` `ALARM_SNAP_SPEC`): a snap that must land
  deterministically — a spring's asymptotic tail reads as a stall at the end of a scroll gesture.
- **Navigation transitions** (`SettingsNavGraph.kt`, `MainActivity.kt`): `NavGraphBuilder` transition
  lambdas are not composable, so they structurally cannot read `MaterialTheme.motionScheme`. The full
  rule set for tab / push / pop / predictive-back motion lives in `docs/navigation.md` — read it before
  adding a destination.
- **Content-reveal choreography** (`NextAlarmCard.kt` LCD digit reveal): the reveal drives a 0→1
  progress that gates digit rolling; a spring's overshoot would push progress past 1 and re-roll digits.
