# Performance — keeping screens fast

This is the playbook for the home-screen optimisation work (PRs #81–#84). Read it before optimising any
screen that "lags when you open it." The home screen went from a ~0.66s click-to-content delay to
near-instant; this is what actually worked, and how to diagnose the next one.

## 1. Diagnose first: is it data or composition?

Symptom that pins it down: **the screen lags on open / tab re-entry, but the other tabs are instant.**

- The ViewModel survives navigation (`popUpTo(saveState = true)` / `restoreState = true` in `MainActivity`),
  so on re-entry the data is **already loaded** — the `StateFlow` replays its last value. The lag is the
  **synchronous first composition** of a heavy tree, before the first frame paints.
- Other tabs (History/Settings) are plain `LazyColumn`s of text rows (~10ms). The slow tab is doing
  something they aren't.
- Rule: if killing the data load wouldn't help (because the data's already there), it's composition cost.
  Reach for **staged rendering** (§2). If the *first ever* load or a cold start is what's slow, also look at
  the data/startup levers (§4).

## 2. The fix that worked: staged rendering

Paint the cheap, above-the-fold content (the header + the primary card) on the first frame; defer the heavy
subtree (markdown, `SubcomposeLayout`, pagers) to the next frame, off the critical paint path.

```kotlin
// Paint the cheap header + card on frame 1; let the heavy subtree compose one frame later.
var deferHeavy by remember { mutableStateOf(true) }
LaunchedEffect(Unit) {
    withFrameNanos {}      // wait for frame 1 (header + card) to actually draw…
    deferHeavy = false     // …then admit the heavy subtree on frame 2+
}

// cheap content — always composed
HeaderRow(...)
PrimaryCard(...)

// heavy content — gated, with a height-reserving placeholder so nothing jumps when it appears
if (deferHeavy) {
    Spacer(Modifier.height(reservedHeight))
} else {
    HeavyThing(...)   // markdown pager, SubcomposeLayout strip, etc.
}
```

See the real implementation in `ui/screens/home/HomeContent.kt` (`HomePlanContent`'s `deferHeavy` flag,
the gated `thread` item, and `belowCardVisible = hasTabs && !deferHeavy`).

Why each piece matters:

- **`withFrameNanos {}`** guarantees frame 1 is drawn before the flag flips — without it Compose may batch
  the recomposition into the same frame and you defer nothing.
- **Plain `remember` (no key)** → the flag resets every time the composable is created, so it re-stages on
  *every* tab entry, not just cold start. (A `rememberSaveable` or a key tied to surviving state would
  defeat it.)
- **Reserve the deferred slot's height** (a `Spacer`, or an `AnimatedVisibility` that animates its own
  height in) so the list/layout doesn't jump when the heavy content fades in.

Safety checklist before you gate a subtree:

- **Hoist shared state above the gate.** Anything the heavy subtree and the cheap subtree both need
  (pager state, current selection, scroll-derived geometry) must live in the parent's body, above the
  `if (deferHeavy)`, so it survives the one-frame absence. On home, `pagerState`, `selectedTurn`,
  `pullStates`, and the collapse geometry are all hoisted; only the *UI* (`ThreadPager`, `ReplanHistoryBar`)
  is deferred.
- **Don't let other layout read the deferred node.** The sticky-card collapse measures a list item to know
  how far it's scrolled — it reads the always-present `"alarm-spacer"` item, **not** the deferred thread, so
  deferring the thread can't break it. If your collapse/anchoring reads the heavy item, point it at a stable
  placeholder instead.
- **Defer the secondary content, not what the user came for.** The header + primary card are the point of
  the screen; the thread/strip are below-the-fold detail. Never defer the thing the user is looking at.

## 3. Know what's heavy vs cheap in Compose

Before deferring, know what you're deferring. On this codebase:

- **Heavy (defer or move off-main):**
  - Markdown parsing — `rememberMarkdownState(immediate = true)` parses **synchronously on the main thread**
    (`ui/screens/home/components/ResponseMarkdown.kt`). `immediate = true` is deliberate (it avoids a
    blank-flash per streamed token) — **don't** flip it off globally; defer the whole block instead.
  - `SubcomposeLayout` probe passes — `ReplanHistoryBar` measures every tab off-screen to decide
    stretched-vs-scrollable; `CollapsibleAlarmCard` subcomposes four pieces. Measure-time composition is
    real composition cost.
  - `HorizontalPager` with `beyondViewportPageCount > 0` — pre-composes neighbour pages, so entry pays for
    *N+1* markdown parses. Set it to `0` if entry speed matters more than instant swipes (the neighbour then
    composes when a swipe starts — a deliberate gesture where a few ms is fine).
  - Synchronous downloadable-font resolution + `Paint` text measurement (`LcdMetrics.kt`,
    `GreetingHeader.kt`, `AlignedFirstGlyph.kt`) — see §4.
- **Cheap:** `Text`, a `Surface`/`Box` card, provider-driven layout (e.g. the collapse fraction read as a
  `() -> Float` inside the measure pass, so a scroll frame never recomposes the card).

## 4. Supporting levers (apply when the diagnosis points at them)

These don't replace staged rendering; they remove cost from the other paths.

1. **Build UI state off the main thread.** A `combine`/`map` that decodes JSON or maps DB rows runs on the
   collector's context — `viewModelScope` is **Main**. Add `.flowOn(Dispatchers.Default)` to the heavy
   sub-flow (PR #81: `HomeViewModel.aiPlanFlow`/`sleepStatsFlow`). Behaviourally invisible; `stateIn`'s
   initial value covers the first frame.
2. **Memoize immutable per-item work.** If a builder re-decodes *every* item on every emission but most
   items never change, cache by a cheap content signature (PR #81: `TurnThreadCache` keys settled turns by
   their row ids, so only the changed turn rebuilds). Keep the mapper pure — put the cache in the VM.
3. **Bundle a font fallback for download-only families.** A `FontFamily` built only from downloadable
   `GoogleFont`s resolves to a wide system fallback in `@Preview` *and* blocks/falls-back on first paint
   until the download lands. List a bundled `Font(R.font.*)` last so resolution is synchronous offline/in
   previews while the downloadable stays primary on device (PR #82: Roboto Flex for `ExpressiveFontFamily`).
   Per the CLAUDE.md rule, don't capture the resolved `Typeface` in a bare `remember` if the family has
   downloadable fallbacks.
4. **Cold start: splash + off-main start decision.** Never construct `EncryptedSharedPreferences`/read the
   keystore or DataStore synchronously in `onCreate`/composition — that's a 200–800ms main-thread stall, and
   with no splash it shows as a white window. Install `core-splashscreen`, keep it up
   (`setKeepOnScreenCondition`) while you resolve the start destination in `withContext(Dispatchers.IO)`
   (PR #83).

## 5. When NOT to stage

- A one-shot screen (not re-entered) where the cost is paid once — not worth the placeholder bookkeeping.
- A light screen (plain list) — there's nothing heavy to defer.
- The thing the user navigated to see — defer secondary/below-the-fold content only.

## 6. Known follow-ups

- The deferred content fades in via a one-frame appearance; if the entrance reads oddly, tune that
  transition separately (don't remove the deferral).
- The primary card's own first-frame cost is the LCD/greeting font resolution + `Paint` measurement,
  recomputed on every re-entry. If a card alone still feels slow after staging, hoist those measurements to
  a process-level cache keyed by density — but only once the font resolves synchronously (PR #82), per the
  typeface-capture caveat above.
