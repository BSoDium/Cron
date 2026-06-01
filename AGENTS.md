# Agent guidance for the Cron repo

These rules exist because LLM passes have repeatedly violated them. Follow them on every diff in this repo.

## Visual design

- The design is flat. **Do not** add `Modifier.shadow(...)`, `shadowElevation > 0`, or any `FloatingActionButtonDefaults.elevation` with non-zero values. Convey depth through `tonalElevation` and `MaterialTheme.colorScheme.surfaceContainer*` shades only.
- Use the design tokens in `ui/theme/Tokens.kt` (`Spacing`, `Radius`) instead of literal `dp` constants for paddings, spacers, and `RoundedCornerShape`. Add a new token before reaching for a literal. Clearance reserved below content for the floating nav pill / FAB is the `Spacing.navBarClearance` token — not a per-screen `+ 96.dp` literal.
- Use the named roles in `Type.kt` — `CronTypography.dateLabel`, `bodySerif`, `labelMono`, `pageTitle`, plus the Material 3 scale — rather than `TextStyle.copy(fontFamily = ...)` inline. If you find yourself copying a `TextStyle` in three or more sites, add a new role.
- Never rotate a Material icon to fake another direction. Pick a different icon, or commit a custom vector under `res/drawable/`.
- Converting a Material Symbols SVG (`viewBox="0 -960 960 960"`) to a vector drawable: wrap the path in `<group android:translateY="960">`. That's the correct coordinate mapping into Android's 0-based viewport, **not** an icon flip — see `res/drawable/ic_thinking.xml`. Don't "fix" it.

## Locale & formatting

- Always pass `Locale.US` to `String.format` for numeric/clock display (`"%02d"`, `"%.2f"`, etc.). Locale-default formatting renders non-ASCII digit glyphs on Arabic/Farsi/Bengali devices, breaking the LCD readout.
- Always pass `Locale.ROOT` (or `Locale.US`) to `.uppercase()` / `.lowercase()` unless the string is user-visible natural language. Without it, Turkish locale rules mangle ASCII identifiers.
- The only place we want locale-default formatting is human-language strings: weekday names in `formatDateLabel`, greeting prefixes, etc. Comment the intent at those sites.

## Compose specifics

- When reading `LocalFontFamilyResolver.current.resolve(...).value`, cast with `as? Typeface`, never `as Typeface`. The resolver returns `State<Any>`; resolution can fail and the platform contract isn't pinned to Typeface.
- Don't capture the typeface inside a bare `remember(...)` if the font family contains downloadable fallbacks — measurement will use the fallback and never update. List bundled `Font(R.font.*)` first so resolution is synchronous, or observe via `produceState` keyed on the resolved typeface.
- `DisposableEffect(...)` keys should be the things whose change *requires* tearing down and re-registering — not every state value the effect happens to read. If a value only needs to update an existing registration, use a `LaunchedEffect` or `SideEffect`. (See `HomeScreen.kt`'s split between `DisposableEffect(viewModel, fabRegistry)` for register/clear and `LaunchedEffect(isRetrying, ...)` for spinner updates.)
- Hoist pure heavyweight objects to `private val` constants at file top — `AnimationSpec` (see `NAV_COLOR_SPEC` in `CronBottomBar.kt`) **and** `Regex` literals (see the `MD_*` patterns in `AiThinkingThread.kt`). A `Regex(...)` built inside a function that runs during composition recompiles on every call. Conversely, never wrap a `@Composable` factory (`markdownColor`, `markdownTypography`, `markdownComponents`, any `rememberX`) in `remember { }` — they must be invoked in composition; memoize the plain values they feed instead (e.g. `remember(source) { parseGfmTable(source) }`).
- `Modifier.padding(...)` overloads don't mix: `start/top/end/bottom` and `horizontal/vertical` are separate overloads, so `padding(start = …, vertical = …)` does not compile. Pick one set. (This broke a build.)
- Inner clickable rows must hit a minimum 48dp touch target. Don't ship a 44dp pill because it "looks right" — bump the inner box; the visual radius will absorb it.
- Apply `Modifier.clip(shape)` **before** `.clickable(...)` so the ripple respects the shape. Putting `clickable` first gives you a square highlight on a rounded surface.
- Provide a `@Preview` for any non-trivial or reusable composable — **mandatory for important components** (cards, the nav/FAB, the thinking thread, each screen's key pieces). Keep the preview `private`, wrap it in `CronTheme`, and feed representative sample data so it renders without a device. A component you can't preview without real DB/network data is a sign its rendering should be split from its data-loading.

## Coroutines & lifecycle

- ViewModels: don't instantiate context-scoped objects (`LocationProvider`, repositories, DAOs) per call. Hold them as `private val` fields constructed once from `application` in the VM's init block.
- Don't use `kotlinx.coroutines.delay(...)` as a stand-in for "show feedback until work finishes". If the underlying flow doesn't expose a completion signal, add one (a `StateFlow<Boolean>` on the repository) — don't paper over it with a fixed sleep. The current 2.5s spinner in `retryAiPlan` is a known wart; if you touch that code, fix it.
- BroadcastReceivers using `goAsync()` must finish within ~10s. Don't add long-running work behind `CoroutineScope(Dispatchers.IO).launch` inside one — schedule a WorkManager job.

## Error handling

- `runCatching { ... }.getOrNull()` is fine for tolerant parsing, but always chain `.onFailure { Log.w(TAG, "msg", it) }` first so production failures are diagnosable. Silent swallowing is a debugging trap.
- Don't add `try/catch` or null checks for conditions that *cannot* happen given the function's contract. Validate at module boundaries (DB rows, network responses, user input) — trust internal callers.

## Comments & files

- Default to writing no comment. Add one only when the *why* is non-obvious (a workaround for a specific bug, a hidden invariant, a constraint from an external system). Don't restate what the code does — clean names and whitespace explain far more than prose.
- No back-to-back `//` paragraph comments above a declaration. Several `//` lines forming a paragraph should become a single `/** … */` KDoc placed **immediately above** the declaration: KDoc flows into IDE quick-docs and hover; stacked `//` lines do not. (A single inline `//` explaining one line *inside* a function body is fine.)
- No section-banner comments (`// ──── Foo ────`, `// ---- Foo ----`, `/* ===== Foo ===== */`). They're a code smell that signals a file is doing too much. **Split into atomic files instead** — one file, one responsibility — rather than partitioning one file with banners.
- Don't add a multi-paragraph KDoc block to a private composable describing what it renders — the name and signature already say that. One short line max.
- Don't leave `// TODO`, `// for now`, or hand-tuned magic numbers without a measurement-based replacement plan. The pattern of `Modifier.offset(x = (-6).dp) // compensate for X` has been removed from this repo (see `AlignedFirstGlyph` in `NextAlarmCard.kt` for the proper measurement-based approach); don't reintroduce it.

## Workflow

- Concise responses, no celebration paragraphs, no end-of-turn "what I did" recaps — the diff is the recap.
- Before claiming a UI task is done, build the app and visually confirm on a device or emulator. Type-checking is not feature-checking. Use:
  ```sh
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
  ```
- CI runs `:app:lintDebug` and fails the build on lint **errors** (not warnings) — e.g. `UnusedMaterial3ScaffoldPaddingParameter`. Run `./gradlew :app:lintDebug` alongside `assembleDebug` before declaring a UI task done; `assembleDebug` passing locally is not enough. An intentionally-ignored Scaffold padding parameter in an edge-to-edge layout needs `@Suppress("UnusedMaterial3ScaffoldPaddingParameter")` on the enclosing function.
- If a task spans 3+ steps, use TaskCreate to track them and mark `completed` as soon as each is done — don't batch.
- Don't run destructive git operations (`reset --hard`, force-push, branch deletion) without explicit user approval; the redesign branch carries hand-tuned visual work that doesn't always show up in diffs.
