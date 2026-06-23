# Compose gotchas

Pitfalls that have caused bugs or broken builds in this repo. Read before writing or modifying composables.

## Font resolution

- When reading `LocalFontFamilyResolver.current.resolve(...).value`, cast with `as? Typeface`, never `as Typeface`. The resolver returns `State<Any>`; resolution can fail and the platform contract isn't pinned to Typeface.
- Don't capture the typeface inside a bare `remember(...)` if the font family contains downloadable fallbacks — measurement will use the fallback and never update. List bundled `Font(R.font.*)` first so resolution is synchronous, or observe via `produceState` keyed on the resolved typeface.

## Effect keys

- `DisposableEffect(...)` keys should be the things whose change *requires* tearing down and re-registering — not every state value the effect happens to read. If a value only needs to update an existing registration, use a `LaunchedEffect` or `SideEffect`. (See `HomeScreen.kt`'s split between `DisposableEffect(viewModel, fabRegistry)` for register/clear and `LaunchedEffect(isRetrying, ...)` for spinner updates.)

## Hoisting heavyweight objects

- Hoist pure heavyweight objects to `private val` constants at file top — `AnimationSpec` (see `NAV_COLOR_SPEC` in `CronBottomBar.kt`) **and** `Regex` literals (see the `MD_*` patterns in `AiThinkingThread.kt`). A `Regex(...)` built inside a function that runs during composition recompiles on every call. Conversely, never wrap a `@Composable` factory (`markdownColor`, `markdownTypography`, `markdownComponents`, any `rememberX`) in `remember { }` — they must be invoked in composition; memoize the plain values they feed instead (e.g. `remember(source) { parseGfmTable(source) }`).

## Modifier ordering & padding

- `Modifier.padding(...)` overloads don't mix: `start/top/end/bottom` and `horizontal/vertical` are separate overloads, so `padding(start = …, vertical = …)` does not compile. Pick one set. (This broke a build.)
- Apply `Modifier.clip(shape)` **before** `.clickable(...)` so the ripple respects the shape. Putting `clickable` first gives you a square highlight on a rounded surface.

## Touch targets

- Inner clickable rows must hit a minimum 48dp touch target. Don't ship a 44dp pill because it "looks right" — bump the inner box; the visual radius will absorb it.
- **Edge-to-edge tap targets in a padded column**: when a screen column has horizontal padding (e.g. `Spacing.xl`) and a child row must show a full-width ripple, use `.bleedHorizontally(Spacing.xl)` (from `BleedHorizontally.kt`) *before* `.clickable` and `.padding(horizontal = Spacing.xl)` *after*. The column padding stays for non-interactive siblings; only the tappable row bleeds. Without this, the ripple stops at the inset edge and looks cramped. See `CheckboxRow` in `SettingsDetailScaffold` for the canonical example.

## LargeFlexibleTopAppBar subtitle trap

- **Don't put screen description text in `LargeFlexibleTopAppBar`'s `subtitle` slot.** The bar renders `smallSubtitle` in the collapsed bar too (see M3 source: `smallSubtitle = subtitle ?: {}`), so long text crowds the collapsed state and clips the title. The canonical Android pattern (used by Android Settings) is: functional description text is the first item in the scrolling content column, where it naturally scrolls away. In this project that means the `subtitle: String?` parameter on `SettingsDetailScaffold`. The `LargeFlexibleTopAppBar` subtitle slot is reserved for very short complementary labels that should remain visible at all scroll positions — not used in this project.

## Previews

- Provide a `@Preview` for any non-trivial or reusable composable — **mandatory for important components** (cards, the nav/FAB, the thinking thread, each screen's key pieces). Keep the preview `private`, wrap it in `CronTheme`, and feed representative sample data so it renders without a device. A component you can't preview without real DB/network data is a sign its rendering should be split from its data-loading.
