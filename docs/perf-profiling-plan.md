# Agent-drivable performance profiling — implementation plan

> Agent-ready specification for #188. Read in full before implementing — this is the spec, the
> implementation happens on a Mac with a physical device or emulator attached (an adb-less cloud
> session cannot run any of this, only write the code and docs for it).
>
> **Confirmed by direct experiment (2026-08-31):** a cloud session can't currently build this
> project *at all*, independent of the device/adb gap above. This environment's egress policy
> denies `dl.google.com` outright (`connect_rejected — organization policy`, per the proxy's own
> status log), and `maven.google.com` — the alias Android's own docs point at — redirects straight
> back to `dl.google.com`, so there's no reachable path to Google's Maven repository under either
> name. Real Maven Central (`repo1.maven.org`) and the Gradle wrapper's own distribution host
> (`services.gradle.org`) are both fine; JDK 21 and 30GB of free disk are present. But this repo's
> `settings.gradle.kts` scopes the AGP plugin and every `androidx.*`/`com.google.*` dependency to
> `google()` specifically — so with that host blocked, no Gradle task can even configure, let alone
> run: not `assembleDebug`, not `testDebugUnitTest`, not the lightweight `checkFileLength`. This
> isn't a per-tool fix (no CA/proxy workaround applies to a policy denial, and the proxy's own
> guidance is explicit: don't retry or route around one) — it's this **environment's** network
> policy, set at environment creation. If cloud-session builds/tests are wanted for this project,
> the fix is recreating or reconfiguring the environment with a policy that allows Google's Maven
> host, not anything scriptable from inside a session. See
> https://code.claude.com/docs/en/claude-code-on-the-web for where that's configured.

## Problem

Every perf investigation in this repo (`docs/performance.md`, #176, #14) is diagnosis-by-reading-the-code.
There is no frame-timing instrumentation anywhere. Getting real numbers today means a human: install the
build, enable the GPU/profiler watcher in developer settings, perform the interaction, stop the recording,
open the dump in Android Studio's Profiler, read it by eye. This plan replaces that with a command an agent
can run and a file an agent can read.

## Non-goals (v1)

- **No CI integration.** This runs against a device/emulator attached to the machine driving the session.
  Wiring it into GitHub Actions against a managed device farm is a legitimate follow-up, not part of this
  plan — file a separate issue once the local workflow is proven out.
- **Not a replacement for `docs/performance.md`.** That playbook's reasoning (staged rendering, what's
  heavy in Compose) still applies. This plan supplies the missing *measurement* step so a hypothesis from
  that playbook can be confirmed or killed with a number instead of a guess.

## Prerequisites (on the Mac, not in a cloud session)

- Android SDK installed, `ANDROID_HOME`/`local.properties` pointing at it, `adb` on `PATH`.
- A device or emulator attached (`adb devices` shows it) with the screen unlocked and USB debugging
  authorized. **Prefer a physical device.** Macrobenchmark's own docs call emulator timing noisy/unreliable
  for anything but relative before/after comparisons on the *same* emulator — don't trust an emulator's
  absolute numbers, only deltas between two runs on it.
- The app installed via the benchmark-appropriate build (see below) — `assembleDebug` is **not** sufficient
  for representative numbers.

## Part 1 — Macrobenchmark module (the rigorous path)

### 1.1 New Gradle module

Add `:macrobenchmark` alongside `:app` in `settings.gradle.kts`. It uses the `com.android.test` plugin
(not `com.android.library` — this is a self-contained instrumented-test module with no production code of
its own) and targets `:app`:

```kotlin
// macrobenchmark/build.gradle.kts
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fr.bsodium.cron.macrobenchmark"
    compileSdk = /* match :app */

    defaultConfig {
        minSdk = 24 // FrameTimingMetric's floor
        targetSdk = /* match :app */
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

Add the matching version catalog entries (`androidx-benchmark-macro-junit4`, `androidx-uiautomator`,
`android-test` plugin alias) to `gradle/libs.versions.toml`, pinned to current stable at implementation
time — don't copy a version number out of this doc, it'll be stale.

### 1.2 `:app` needs a benchmarkable build type

Running against plain `debug` gives useless numbers (JIT is cold, R8 hasn't run, some measurements are
outright blocked on a debuggable-and-unprofileable app). Add to `app/build.gradle.kts`:

```kotlin
buildTypes {
    create("benchmark") {
        initWith(getByName("release"))
        signingConfig = signingConfigs.getByName("debug") // installable without release keys
        matchingFallbacks += listOf("release")
        isDebuggable = false
        proguardFiles("benchmark-rules.pro")
    }
}
```

and a `<profileable android:shell="true" />` tag in the manifest (guarded so it never ships in the real
release build — AGP's `benchmark` build type applies it automatically when set up via the
`androidx.benchmark` plugin; follow the current AndroidX Macrobenchmark setup guide for the exact
mechanism at implementation time, since this has shifted across AGP versions).

### 1.3 Making Compose nodes findable by UiAutomator

Macrobenchmark drives the UI through `UiAutomator`, which finds views by Android resource id — Compose's
`Modifier.testTag(...)` doesn't produce one on its own. Enable the bridge once, near the root of the
Compose tree (e.g. in `CronTheme` or `MainActivity`'s `setContent`):

```kotlin
Box(Modifier.semantics { testTagsAsResourceId = true }) {
    // app content
}
```

Then tag the two targets this plan cares about:
- The home timeline's `LazyColumn` (`HomeContent.kt`) → `Modifier.testTag("home_timeline")`.
- The thinking-thread expand affordance (`AiThinkingThread.kt` / `PlanDetailScreen.kt`) →
  `Modifier.testTag("thinking_thread_expand")`.

These test tags are a real (tiny) production-code change, gated behind nothing special — `testTagsAsResourceId`
is inert outside of instrumented tests, so this is safe to land ahead of the rest of the module.

### 1.4 The benchmark tests

```kotlin
// macrobenchmark/src/main/java/fr/bsodium/cron/macrobenchmark/HomeTimelineScrollBenchmark.kt
@RunWith(AndroidJUnit4::class)
class HomeTimelineScrollBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun flingScroll() = benchmarkRule.measureRepeated(
        packageName = "fr.bsodium.cron",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        compilationMode = CompilationMode.Full(), // AOT-compiled — isolates the fix, not JIT warm-up
        startupMode = StartupMode.WARM,
        setupBlock = { startActivityAndWait() },
    ) {
        val timeline = device.findObject(By.res(packageName, "home_timeline"))
        timeline.setGestureMargin(device.displayWidth / 5)
        timeline.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
```

A second test (`ThinkingThreadExpandBenchmark`) does the equivalent for the expand/collapse toggle #14
suspects — navigate to `PlanDetailScreen` for a fixture session with a non-trivial thread, then repeatedly
tap `thinking_thread_expand` and measure frame timing across the reveal animation.

A third (`ColdStartBenchmark`, `StartupTimingMetric`, `StartupMode.COLD`) gives a number for the splash/
off-main work `docs/performance.md` §4.4 already did — useful as a regression guard, not a new
investigation.

### 1.5 Running it and reading the result — no Studio involved

```sh
adb devices                                                    # confirm a device is attached
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  --tests "fr.bsodium.cron.macrobenchmark.HomeTimelineScrollBenchmark"
```

Gradle prints a summary table to stdout directly (median/P50/P90/P99 frame duration, jank-frame %). The
full per-iteration data also lands as JSON under
`macrobenchmark/build/outputs/connected_android_test_additional_output/**/*-benchmarkData.json` — the exact
path segment names vary by AGP version, so locate it with
`find macrobenchmark/build -name '*-benchmarkData.json'` the first time rather than hardcoding it. Either
way: readable directly (stdout or `cat`/`Read`), no Profiler UI required.

## Part 2 — JankStats (the cheap, always-on path)

Macrobenchmark is deliberate and repeatable but only measures the scripted interaction you wrote. JankStats
is the complement: wire it once, and it observes jank during *any* session — yours, an agent's, a real
overnight run — for free, no dedicated benchmark invocation needed.

```kotlin
// MainActivity.kt, after setContent { }
val metricsStateHolder = PerformanceMetricsState.getForHierarchy(window.decorView).state
JankStats.createAndTrack(window) { frameData ->
    if (frameData.isJank) {
        Log.w("JankStats", "${frameData.frameDurationUiNanos / 1_000_000}ms — ${frameData.states}")
    }
}
```

Tag what's on screen at the point of interest so a jank frame's log line says *what* was slow, not just
that something was:

```kotlin
metricsStateHolder.putState("Screen", "HomeTimeline")
// ...on leaving the screen:
metricsStateHolder.removeState("Screen")
```

Reading it needs nothing but Logcat, scriptably:

```sh
adb logcat -s JankStats:W
```

This is the lower-effort, ship-first half of this plan — land it independently of the Macrobenchmark
module if the two end up sequenced separately.

## Part 3 — root-causing a confirmed jank (stretch, only once Part 1 or 2 finds something real)

Frame timing tells you a frame was slow; it doesn't say which composable cost the time. Wrap the specific
suspects `docs/performance.md` and #176 already name in trace sections:

```kotlin
import androidx.tracing.trace

trace("TimelineTrackOverlay.onDraw") {
    // the DrawScope block
}
```

Capture a full trace during a Macrobenchmark run (`measureRepeated`'s `traceSectionMetrics` param can
promote a named section straight into the summary table — check current AndroidX Macrobenchmark API,
this parameter's shape has changed across versions), or pull a raw Perfetto trace with
`adb shell perfetto -o /data/misc/perfetto-traces/trace.perfetto-trace -t 10s` around a manual interaction
and query it with Perfetto's `trace_processor` (scriptable, no Perfetto UI) instead of eyeballing a
flame graph.

This tier turns "the timeline is janky" into "68% of dropped-frame time on fling is
`TimelineTrackOverlay.onDraw`, and it scales with event count" — the kind of number #187's pagination
hypothesis needs to go from a guess to a decision.

## Suggested landing order

1. JankStats (Part 2) — smallest diff, immediate always-on signal.
2. `testTagsAsResourceId` + the two test tags (§1.3) — trivial, unlocks everything else, safe to land alone.
3. `:macrobenchmark` module + `HomeTimelineScrollBenchmark` (§1.1–1.5) — proves the loop end-to-end on one
   screen before spending time on the other two benchmark tests.
4. `ThinkingThreadExpandBenchmark` + `ColdStartBenchmark` — same pattern, second and third screens.
5. Trace-section root-causing (Part 3) — only once a benchmark from step 3/4 actually shows a real budget
   overrun worth explaining.

## Open questions to settle during implementation, not now

- Exact `androidx.benchmark`/`androidx.test.uiautomator`/AGP-plugin version pins (use current stable at
  implementation time).
- Whether the `benchmark` build type needs its own `google-services.json`/API-key handling given this app
  ships an Anthropic API key via `local.properties` — check `SecureKeyStore.kt`'s build-time wiring before
  assuming `release`'s config carries over cleanly via `initWith`.
- Physical test device to standardize on for before/after comparisons (an emulator's absolute numbers
  aren't trustworthy per the prerequisites note above).
