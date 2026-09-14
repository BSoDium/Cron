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

## 7. A second axis: sustained scroll cost, not first paint (#176)

Sections 1–6 are about the cost of **one** composition — a screen opening or a tab re-entering. Scroll jank
is a different problem: cost paid **repeatedly, per frame, while the user is already looking at the
screen**. The same "diagnose before fixing" discipline applies, but the tools and the traps are different
enough to need their own section. This is the writeup from the Home-timeline scroll-jank investigation
(#176, PRs #205/#206/#208/#209/#189) — four rounds, several wrong hypotheses corrected only by fresh
measurement, and one confound that made every round's numbers harder to trust than they should have been.

### What actually moved the numbers

1. **Gate expensive `remember { }` calls behind the branch that needs them, not the top of the function.**
   `TimelineNode` used to unconditionally construct two `androidx.graphics.shapes.Morph`s (real geometric
   vertex-matching work) at the top of every row, even though the overwhelming majority of rows use neither.
   Moving each `remember { Morph(...) }` inline, into only the specific `when` branch that reads it, cut
   `TimelineNode`'s first-composition cost from 12.04ms avg/29.56ms max to 4.48ms avg/11.73ms max — the
   single biggest lever in this whole investigation. `remember` is scoped per call-site, not globally, so
   this is Compose-legal even inside an `if`/`when` branch — it does **not** need to be hoisted to satisfy
   any rule about "calling remember unconditionally"; that rule is about not skipping a remember call
   *within the same branch* across recompositions, not about which branch it lives in.
2. **A component-level `Crossfade`/`updateTransition` still pays setup cost even when it never animates.**
   `AiRunNode`'s title/status slots wrapped their content in `Crossfade` for a one-shot hero→demoted fade
   that, for the overwhelming majority of rows (anything demoted before it ever scrolls into view), never
   actually transitions — `targetState` is `false` from the first composition onward. `Crossfade` still
   unconditionally builds a `MutableTransitionState` and an internal `animateFloat` child regardless. Track
   whether a row could *ever* need the transition (`remember(item.id) { item.isLatest }`, since that flag
   only ever goes true→false, never back) and skip the `Crossfade` entirely for rows that can't.
   **Watch this pattern for regressions**: it's easy to compute something *inside* the gated branch that
   should stay gated, then accidentally hoist it back above the gate later — see the `heroMinHeight`
   redundancy this investigation itself introduced and then had to clean up, below.
3. **`LazyColumn`'s default prefetch is only one item ahead.** `DefaultLazyListPrefetchStrategy` schedules
   exactly one item of look-ahead composition regardless of how fast the user is flinging. A fast fling can
   easily outrun that by several rows, which shows up as rare, severe P99 tail spikes rather than a uniform
   slowdown (this investigation measured P50/P90/P95 of 6/9/10ms but a P99 of 93ms — a smoking gun for
   exactly this). `rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = <N>.dp))`
   (`@ExperimentalFoundationApi`) keeps a real pixel-sized window of rows composed ahead of the viewport
   instead of one item at a time. Tune the window empirically against a real fling capture, not a guess —
   too wide burns CPU/battery composing rows the user may never reach.
4. **A dedicated overlay reading `LazyListState.layoutInfo` in its own `drawBehind` is cheap, and not
   automatically desynced from the rows it decorates** — this investigation suspected `TimelineTrackOverlay`
   might visually lag behind the pills/rows it draws sockets for (matching a real user-reported symptom:
   "the pills or background of the timeline lagging behind"), and measured it directly rather than assuming
   either way: the overlay redraws on 100% of frames where layout actually ran, and its own draw-phase cost
   was ~1.35ms avg, ~2.44ms max across 316 draws — never the bottleneck. **The actual explanation for a
   "pills lagging behind" symptom, when this exact class of theory doesn't pan out, is very likely just
   general frame-time jank making everything look laggy together** — don't chase a *desync* theory past the
   point real measurement rules it out.

### Two things measured and correctly *not* shipped

Not every well-reasoned fix helps, and it's worth recording the ones that didn't as much as the ones that
did — this investigation had the discipline to measure before/after on every candidate and revert what
didn't pan out, rather than shipping structurally-plausible changes on faith:

- **Removing a `Modifier.height(IntrinsicSize.Min)` alignment pass** (Round 4) looked like free wins —
  intrinsic-measure passes are documented as expensive, and this one existed only to align a colon glyph.
  Measured before/after: it made the clock's own measure cost *worse* (0.348ms → 0.400ms avg), because
  `ParagraphLayoutCache.intrinsicHeight` caches per input width and the pass was hitting a warm cache, not
  doing wasted work. Verified against the actual pinned Compose sources, not assumed from general
  "intrinsics are expensive" knowledge. Reverted.
- **A component's own over-broad `remember` key** (`AlignedFirstGlyph`, Round 4) genuinely was re-running
  expensive font measurement on every animation frame — a real, confirmed redundancy (220 redundant
  measurements in one 12s capture). Fixed anyway, but the fix showed **zero measurable end-to-end effect**
  in an interleaved before/after. Landed because it's a correctness fix with no downside, not because the
  numbers moved — and the PR said so plainly rather than claiming a win it didn't have. If a fix doesn't
  move the number you're chasing, say that; don't let "this feels like it should help" stand in for
  re-measuring.

### The debug-build trap

**This was the single biggest confound in the whole investigation, and it invalidated the "feel" of every
round's live-device testing until it was found.** A debug build (`isDebuggable = true`) can **never** be
AOT-compiled — `adb shell cmd package compile -m speed -f <pkg>` silently downgrades to `status=verify`
on a debuggable APK, no error, no warning. Every scroll captured on a debug build pays real, live JIT
compilation cost mixed into the measurement: one investigation session's own trace showed **550 "Compiling
baseline" JIT jobs firing during a single 12-second scroll capture**. Every number this investigation
measured through Rounds 1–4 — on the actual device, "live-testing" the feel after each fix — was
JIT/interpreter-bound, not representative of what a real user with a release-signed, AOT-compiled app
actually experiences.

The practical consequence: real code fixes (Rounds 1–4 combined) moved the AOT-compiled P99 frame overrun
from +3.6ms to +0.8ms — both already near-imperceptible, a small but real difference. Yet switching from
the same **unfixed** code's debug build to the **fixed** code's AOT-compiled (`benchmark` build type)
build felt like night and day live, on the same device, same session. The fixes were real and worth
doing, but the single biggest lever for "does this feel smooth" was simply **measuring/testing on the
right build type**, not any one code change.

**Rule going forward: never judge "is this smooth enough" from a debug build.** Use debug builds for
correctness and iteration speed; use the `benchmark` build type (or a release build) — see `:macrobenchmark`
below — for every "does this feel right" judgment call, live-device testing included. A debug build will
always feel somewhat worse than production, and no amount of app-code optimization changes that; it's an
Android build-system property, not a bug in this codebase.

### `:macrobenchmark` — repeatable AOT-compiled numbers, no more manual Perfetto sessions

The `:macrobenchmark` Gradle module (`com.android.test`, PR #189) + `HomeTimelineScrollBenchmark`
(`FrameTimingMetric`, `CompilationMode.Full()`, `StartupMode.WARM`) replaces the manual live-device
Perfetto sessions below with one repeatable command that gives real, AOT-compiled numbers. See
`docs/perf-profiling-plan.md` for the module's own spec/history.

**Never run it via `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest` against a real, non-disposable
device.** That Gradle task family uninstalls the app-under-test as standard, unconditional teardown — this
is not a bug, it's by design, and it isn't exposed as a toggle in the DSL. Running it against this
project's own test device once wiped months of real local session data. Use an emulator for that task if
one is available and convenient, or — better, and what this investigation settled on — drive the same
three steps by hand:

```sh
# 1. Build (local-only, no device touched)
./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark

# 2. Install as an UPDATE, not a fresh install — the `benchmark` build type shares the debug signing
#    key and applicationId with no suffix, so this is a same-signature in-place update. Verify:
adb shell dumpsys package fr.bsodium.cron | grep firstInstallTime   # unchanged before/after == safe
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb install -r macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk  # separate
    # package (fr.bsodium.cron.macrobenchmark), holds no user data, safe to freely install/remove

# 3. Find the real installed instrumentation — don't guess the package/runner name
adb shell pm list instrumentation

# 4. Run it directly, bypassing Gradle's own install/uninstall lifecycle entirely
adb shell am instrument -w -e class fr.bsodium.cron.macrobenchmark.HomeTimelineScrollBenchmark \
  fr.bsodium.cron.macrobenchmark/androidx.test.runner.AndroidJUnitRunner

# 5. Restore the normal build afterward (also an in-place update, same reasoning as step 2)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

This is the default way to run this benchmark against a real device — no destructive-task confirmation
needed for it specifically, since nothing in this flow uninstalls anything with user data.

### Compose + UiAutomator's `By.res()` trap

`androidx.compose.ui.semantics.testTagsAsResourceId = true` (the bridge that lets `Modifier.testTag(...)`
be found by UiAutomator) sets `AccessibilityNodeInfo`'s resource-id to the **bare testTag string**
(`"home_timeline"`), **not** a namespaced `"package:id/name"` identifier the way a real Android View's
`android:id` would be. `androidx.test.uiautomator.By.res(packageName, "home_timeline")` (the two-arg
overload) builds and matches against that namespaced form internally — and so **never** matches a Compose
tag, silently, with no error, just an empty search result. Use the single-arg `By.res("home_timeline")`
instead, which matches the raw resource-id string (or regex) directly.

**This one selector bug produced hours of chased-and-rejected red herrings** before it was found: an
event-driven `Until.hasObject` wait timing out was blamed on Compose's semantics tree not emitting the
right `AccessibilityEvent` types (plausible, and a real documented rough edge, just not the cause here); a
third-party accessibility service on the test device was suspected of stealing the system's single
`UiAutomation` connection slot (also plausible, also not it); stale keyguard/window-manager state was
suspected badly enough to warrant a full device reboot (didn't help either). None of those theories were
unreasonable to check — they just weren't it. What finally settled it: `device.findObjects(By.pkg(pkg))`
(no resource-id filter) found the node fine, and reading `.resourceName` directly off it showed the bare,
unnamespaced string — proof the selector, not the environment, was the mismatch. **When a UiAutomator
query can't find a node that a full unfiltered dump (`adb shell uiautomator dump`, or
`device.dumpWindowHierarchy()`) finds fine in the same moment, suspect the selector before the
environment.**

### Checklist for the next graphically-intensive page

1. **Diagnose with real device data before writing a fix.** Every wrong hypothesis in this investigation
   (and there were several) looked structurally reasonable on a code read; only live measurement — Perfetto
   traces, `dumpsys gfxinfo`, or `:macrobenchmark` — separated the real culprits from the plausible ones.
2. **Judge "is it smooth enough" only on an AOT-compiled build**, never a debug build — see "The
   debug-build trap" above. This applies to your own live testing, not just to numbers you report.
3. **Gate expensive `remember { }` work (shape/path construction, typeface resolution, anything doing real
   geometric or measurement work) behind the specific branch that uses it**, not unconditionally at the top
   of the composable — most rows/items in a list use a small fraction of the possible branches.
4. **A one-shot `Crossfade`/`AnimatedVisibility`/`updateTransition` still pays setup cost even for content
   that will never actually animate.** If you can determine up front that a given instance can never
   transition, skip the animation wrapper entirely for it — but watch for a value computed *only* for the
   animated path accidentally staying hoisted above the gate (see the `heroMinHeight` note above).
5. **`LazyColumn`'s default prefetch is one item ahead.** For a list with real per-item first-composition
   cost, tune `LazyLayoutCacheWindow`'s `ahead` value against a real fast-fling capture rather than assuming
   the default is enough.
6. **Verify a "clearly expensive" API against the pinned library's real source before removing it** —
   `IntrinsicSize.Min` above made things measurably worse, not better, once actually measured. General
   knowledge about what's "expensive in Compose" is a starting hypothesis, not a substitute for checking the
   specific, currently-pinned version's actual behavior.
7. **Temporary `androidx.tracing` `trace()` sections are diagnostic tooling, not permanent infra — strip
   them before merging, every time, including on long-lived investigation branches.** This repo's own
   Macrobenchmark infra branch left two `trace()` spans in for weeks after they'd done their job, because
   the "strip before commit" discipline was applied on the branches doing the actual fix work but not on
   the parallel branch doing the profiling infra. `JankStats` (Part 2, permanently wired in `MainActivity`)
   is the correct always-on signal; temporary `trace()` sections are for one investigation, then gone.

## 8. SubcomposeLayout vs. a plain Layout: a per-frame remeasure trap (#14)

`AiThinkingThread`'s expand/collapse (`ThinkingDisclosure`/`ExpandReveal`, `ui/screens/home/components/AiThinkingThread.kt`)
was janky on the pull-to-reveal gesture. No physical device was available for this investigation (the
Pixel 7 was locked, fingerprint-required, for the whole window) — so root-causing happened entirely
against a JVM/Robolectric probe, not a real trace. Worth recording precisely because the diagnosis still
held up, and because the caveat below is a real gap, not a formality.

**Diagnosis.** `ExpandReveal` used `SubcomposeLayout` to measure its content at full height (to report
[onFullHeight]) while clipping the visible portion to a per-frame `targetPx()` value. A Compose test
stepping `expandPx()` through 20 simulated pull-gesture frames, counting invocations of the measure
lambda via a temporary instrumented counter, showed a strict 1:1 — every single frame fully re-measured
the entire process timeline, not just when content actually changed. That part matched the hypothesis
exactly. What didn't fully match: `SubcomposeLayout`'s content here never actually varies by the incoming
constraints — no constraint-based composition decision was being made — so subcomposition wasn't buying
anything over a plain nested `Layout`, only paying its bookkeeping cost on every one of those frames.

**Fix.** Swapped `SubcomposeLayout` for a plain `Layout` in `ExpandReveal`. Same probe, same 20-frame
walk, with a warmup phase added to control for JVM/JIT noise (the first test method in a Robolectric run
otherwise eats class-loading cost and skews any comparison against later ones): per-call cost dropped
roughly 3–5x, and cost's sensitivity to content size dropped from what full-remeasure-every-frame would
predict (near-linear) to clearly sub-linear (16x more content → ~4x more cost, once warmed up).

**What this fix is not.** The outer node still calls `.measure()` on its child once per frame by
construction — it has to, since the *reported outer size itself* is what's animating, and a `Placeable`
from a prior frame can't be reused in a later one. A tighter fix would cache the full height in a
`remember`ed `Int` (updated only when content-driven remeasure actually changes it) and split into two
layout nodes so the expensive one's own measure lambda never reads the per-frame `targetPx()` at all.
Not pursued here — meaningfully more structural complexity for a gain that's unverifiable without a
device to measure the delta against.

**On-device follow-up.** Once the Pixel 7 unlocked, ran a controlled A/B on the real device: same
`expand`/`collapse` tap on the same turn's detail screen (3 process items — `read_calendar`, one
narration line, `compute_commute`), `dumpsys gfxinfo <pkg> reset` immediately before each cycle,
`dumpsys gfxinfo <pkg>` immediately after, non-destructive `adb install -r -d` swap between the old
`SubcomposeLayout` build and the fixed `Layout` build (verified via unchanged `firstInstallTime` each
time), 4–5 trials per build. Result: **the aggregate frame-jank numbers overlapped heavily between the
two builds** — janky-frame % and p90/p95/p99 render times both swung more trial-to-trial *within* a
single build than the two builds differed from each other on average. No device-level win was
detectable at this content size.

That's not a contradiction of the JVM finding, just a scale mismatch: the JVM probe isolated
`ExpandReveal`'s own measure-lambda cost specifically, where the subcompose-vs-Layout delta is real and
repeatable (§ above). `dumpsys gfxinfo` instead measures the *entire* frame — ripple draw, status bar,
GPU compositing, everything — and with only three short process items, this composable's slice of that
total is apparently too small to clear the noise floor of ~150-frame samples. The fix is still correct
(subcomposition genuinely bought nothing here, and costs measurably more to call), but its device-level
payoff has only been shown to matter in proportion to content size, not confirmed as user-visible at
typical (short) thread lengths. **Untested**: whether the gap opens up on a long, many-tool-call thread
(where the JVM probe's 16x-content run showed the clearest relative win) — re-run this same A/B against
a turn with 20+ process items, ideally with a Perfetto trace isolating `ExpandReveal`'s own frame slice
rather than the whole app's frame time, before assuming this closes #14 for the janky-on-a-long-thread
case specifically.
