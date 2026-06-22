# Agent guidance for the Cron repo

These rules exist because LLM passes have repeatedly violated them. Follow them on every diff in this repo. Sections below link to deep-dive guides under `docs/` — **read the linked doc before working in that area.**

## Visual design

- The design is flat. **Do not** add `Modifier.shadow(...)`, `shadowElevation > 0`, or any `FloatingActionButtonDefaults.elevation` with non-zero values. Convey depth through `tonalElevation` and `MaterialTheme.colorScheme.surfaceContainer*` shades only.
- Use the design tokens in `ui/theme/Tokens.kt` (`Spacing`, `Radius`) instead of literal `dp` constants for paddings, spacers, and `RoundedCornerShape`. Add a new token before reaching for a literal. Clearance reserved below content for the floating nav pill / FAB is the `Spacing.navBarClearance` token — not a per-screen `+ 96.dp` literal.
- Use the named roles in `Type.kt` — `CronTypography.dateLabel`, `bodySerif`, `labelMono`, `pageTitle`, plus the Material 3 scale — rather than `TextStyle.copy(fontFamily = ...)` inline. If you find yourself copying a `TextStyle` in three or more sites, add a new role.
- Never rotate a Material icon to fake another direction. Pick a different icon, or commit a custom vector under `res/drawable/`.
- Converting a Material Symbols SVG (`viewBox="0 -960 960 960"`) to a vector drawable: wrap the path in `<group android:translateY="960">`. That's the correct coordinate mapping into Android's 0-based viewport, **not** an icon flip — see `res/drawable/ic_thinking.xml`. Don't "fix" it.

## Motion & Expressive

This app targets **Material 3 Expressive**, not static M3. **Read `docs/expressive.md` before adding or animating any component** — it's the rule set, grounded in the real APIs.

- **Pull every animation spec from `MaterialTheme.motionScheme`** (the theme is Expressive, so these are the bouncy springs): spatial specs (`fast/default/slowSpatialSpec()`) for anything that moves/resizes/scales/morphs/rotates; effects specs (`*EffectsSpec()`) for colour/alpha. **Do not** hardcode `tween(…, FastOutSlowInEasing)` or `spring(dampingRatio = …)` for UI motion.
- Reach for the Expressive component variant: `ButtonGroup` + `Modifier.animateWidth(interactionSource)` (neighbours react on press), `ToggleButton` (shape morph), the Expressive FAB. A plain `Row` of buttons has no neighbour reaction.
- Don't paint everything `primary` — apply varied accents via `secondary`/`tertiary` + `*Container` roles (restrained), which dynamic colour generates from the wallpaper.

## Locale & formatting

- Always pass `Locale.US` to `String.format` for numeric/clock display (`"%02d"`, `"%.2f"`, etc.). Locale-default formatting renders non-ASCII digit glyphs on Arabic/Farsi/Bengali devices, breaking the LCD readout.
- Always pass `Locale.ROOT` (or `Locale.US`) to `.uppercase()` / `.lowercase()` unless the string is user-visible natural language. Without it, Turkish locale rules mangle ASCII identifiers.
- The only place we want locale-default formatting is human-language strings: weekday names in `formatDateLabel`, greeting prefixes, etc. Comment the intent at those sites.

## Compose specifics

**Read `docs/compose-gotchas.md` before writing or modifying composables** — it covers font resolution, effect keys, padding overloads, touch targets, and the `LargeFlexibleTopAppBar` subtitle trap. Key reminders:

- `Modifier.clip(shape)` **before** `.clickable(...)` so the ripple respects the shape.
- `Modifier.padding(...)` overloads don't mix: `start/end` and `horizontal/vertical` are separate. Pick one set.
- Provide a `@Preview` for any non-trivial composable — **mandatory for important components**.

## Animation previews & inspectability

**Read `docs/animation-previews.md` before adding or modifying any animation.** CI enforces via `./gradlew :app:checkAnimationPreviews`.

- Every `animate*AsState`, `AnimatedVisibility`, `AnimatedContent`, `updateTransition`, `Crossfade`, `rememberInfiniteTransition` call **must** have a `label` parameter (short kebab-case, semantic).
- Every file using animation APIs must ship a `@Preview` (or `@Suppress("AnimationPreviewNotRequired")`).

## Coroutines & lifecycle

- ViewModels: don't instantiate context-scoped objects (`LocationProvider`, repositories, DAOs) per call. Hold them as `private val` fields constructed once from `application` in the VM's init block.
- Don't use `kotlinx.coroutines.delay(...)` as a stand-in for "show feedback until work finishes". If the underlying flow doesn't expose a completion signal, add one (a `StateFlow<Boolean>` on the repository) — don't paper over it with a fixed sleep. The current 2.5s spinner in `retryAiPlan` is a known wart; if you touch that code, fix it.
- BroadcastReceivers using `goAsync()` must finish within ~10s. Don't add long-running work behind `CoroutineScope(Dispatchers.IO).launch` inside one — schedule a WorkManager job.

## Performance

- A screen that lags on open or tab re-entry **while other tabs are instant** is paying *first-composition* cost, not data cost (the VM survives nav, so the data's already loaded). **Read `docs/performance.md` before optimising a slow screen** — it's the playbook from the home-screen work (PRs #81–#84): the staged-render technique that fixed it (paint the cheap header + card on frame 1 via a `withFrameNanos`-gated `deferHeavy` flag, defer the markdown/`SubcomposeLayout` subtree to frame 2+), plus the supporting levers (build UI state off-main with `.flowOn(Dispatchers.Default)`, memoize immutable per-item work, bundle a font fallback for download-only families, splash + off-main start destination for cold start).

## Error handling

- `runCatching { ... }.getOrNull()` is fine for tolerant parsing, but always chain `.onFailure { Log.w(TAG, "msg", it) }` first so production failures are diagnosable. Silent swallowing is a debugging trap.
- Don't add `try/catch` or null checks for conditions that *cannot* happen given the function's contract. Validate at module boundaries (DB rows, network responses, user input) — trust internal callers.

## Kotlin style

- **`val` first.** Default to `val`; keep any `var` a confined, private implementation detail. Never return `MutableList`/`MutableMap` from a public signature — expose immutable `List`/`Map` and build with `buildList`/`buildString`. Surface evolving state as a read-only `StateFlow`, not a public mutable field.
- **Closed sets are `sealed` or `enum`, matched by an exhaustive `when` with no `else`** — adding a variant should be a compile error, not a silent fall-through (see `ProcessItem`, `EventData`, `SessionStatus`, `ContentBlock`). Use `else` only for genuinely open input (e.g. an unknown tool name → wrench icon) and say so in a comment.
- **No `!!`.** Restructure so the value is non-null, or assert at a boundary with `requireNotNull(x) { "why" }` / `checkNotNull` for a diagnosable message. Don't write `pendingIntent(create = true)!!`.
- **Expression bodies for one-liners** (`fun isArmed(...) = … != null`) — the repo norm for setters, predicates, and mappers.
- **Smallest visibility, `private` by default.** File-scoped constants (dp, ms, `Regex`, `AnimationSpec`, vibration patterns) live as top-level `private const val`/`private val`, not in a `companion object` — reserve the companion for class-associated keys/factories (DAO/DataStore keys, `Instruction.doNothing`). No bare magic numbers or literal arrays inline.
- **Named arguments for booleans and adjacent same-type params** (`create = true`, `serif = false`, the `HomeUiState(...)` fields) so a call site reads without opening the signature. Trailing commas on multi-line argument and parameter lists.
- **Scope functions with intent:** `apply` to configure a builder (Intent, prefs editor), `also` for a side-effect, `let` for a null-safe transform. Don't nest them into an unreadable chain.
- **Extension functions to adapt or decorate types** — db `toEntity`/`toModel`, `SleepSession.sleepSegments()`, `Modifier.bleedHorizontally(...)`; keep them `private` when file-local.
- **`kotlinx.datetime` for domain/time logic** (`Instant`/`LocalDate`/`LocalTime`/`TimeZone`/`Clock`). Reserve `java.time` for the UI formatters that need `DateTimeFormatter`, converting with `toJavaLocalDate()`.
- **String templates over concatenation.** Prefer `as?` (and handle null) over unchecked `as` on app-owned types; an unavoidable `getSystemService(...) as X` for an Android system service is the one accepted cast.

## Comments & files

- Default to writing no comment. Add one only when the *why* is non-obvious (a workaround for a specific bug, a hidden invariant, a constraint from an external system). Don't restate what the code does — clean names and whitespace explain far more than prose.
- **No multi-line `//` blocks anywhere** — above a declaration or inside a function body. The only permitted inline comment is a single `//` line. If the why genuinely needs more than one line: write a `docs/<topic>.md` and leave `// See docs/<topic>.md — <one-line summary>.` at the call site. Stacked `//` above a named token (function, class, `val`) must become a `/** … */` KDoc — it surfaces in IDE hover; stacked `//` lines don't.
- No section-banner comments (`// ──── Foo ────`, `// ---- Foo ----`, `/* ===== Foo ===== */`). They're a code smell that signals a file is doing too much. **Split into atomic files instead** — one file, one responsibility — rather than partitioning one file with banners.
- **Hard cap: 500 lines per `src/main` Kotlin file**, enforced by the `checkFileLength` Gradle task in CI (`./gradlew :app:checkFileLength`). The cap is a regression backstop, not the rule — the rule is one file, one responsibility, and most files should sit far below it (the repo mean is ~130). When a file approaches the cap, split it (extract reusable composables to a `components/` package, pull pure parsing/mapping logic out of a ViewModel/Worker into a testable object); don't suppress. A genuinely cohesive single unit near the cap (e.g. one self-contained card composable) is fine.
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
- **Branch before touching any file.** At the start of every task, create a fresh branch from `main` (or the appropriate base) and never commit directly to `main`. Name it using the standard prefixes: `feat/`, `fix/`, `chore/`, `docs/`, `style/`, matching the project's commit-message convention.
- **Commit incrementally.** Commit at each logical milestone — at minimum one commit per completed TaskCreate task — rather than staging everything in a single commit at the end. Each intermediate commit must build (`assembleDebug`) before moving on.
- **Push and open a draft PR when implementation is complete.** Once all tasks are done and the branch is clean, push and open a draft PR immediately (following the existing draft + assignee + closing-keyword rules above). Don't wait for the user to ask.
- **Promote to "ready for review" only on explicit user confirmation.** After the user confirms the changes are tested and good to go, run `gh pr ready <number>` to remove the draft status. Never promote the PR unilaterally.
- Don't run destructive git operations (`reset --hard`, force-push, branch deletion) without explicit user approval; the redesign branch carries hand-tuned visual work that doesn't always show up in diffs.
- When work originates from an issue, link every PR back to it with a GitHub closing keyword in the PR body (`Closes #XX`, or `Fixes #XX`). If the issue is an epic split across several PRs, each PR closes the specific sub-issue it resolves; the epic closes when its last sub-issue does. Use a non-closing reference (`Part of #XX`, `Refs #XX`) only when a PR advances an issue without fully resolving it.
- **Every new issue must be added to the "Open-source development" GitHub project (project #5, owner BSoDium)** immediately after creation — `gh project item-add 5 --owner BSoDium --url <issue-url>`. Then set four fields via `gh api graphql`: **Status** (Backlog for new work), **Priority** (P0–P4 relative to existing issues), **Size** (XS/S/M/L/XL by effort), and **Category** (classify as Feature, Bug, Refactor, Config, or Documentation based on the issue's nature). Fetch current field and option IDs from the project API — do not hardcode them. Also apply relevant **GitHub labels** (e.g. `bug`, `enhancement`) at creation time.
- **Only orphan PRs go on the project board.** If a PR closes an issue via a closing keyword (`Closes`, `Fixes`, `Resolves`) and that issue is already on the board, **do NOT add the PR** — the issue tracks it. Adding both creates duplicate noise. Only PRs with no closing keyword must be added to the project with Status/Priority/Size/Category set. Apply relevant **GitHub labels** to every PR regardless.
- Open PRs as **draft by default** (`gh pr create --draft`) unless the user says otherwise — mark ready for review only on request.
- Assign the PR to whoever requested the work — the person whose name is on the commits — at creation time (`gh pr create --assignee @me`, which resolves to the authenticated `gh` user driving the session).
