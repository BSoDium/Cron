package fr.bsodium.cron.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val PACKAGE_NAME = "fr.bsodium.cron"
private const val ITERATIONS = 10

// MainActivity resolves its start destination asynchronously (a DataStore read + SecureKeyStore
// check on Dispatchers.IO) before the home timeline composes at all — startActivityAndWait()
// returns once the window draws, not once that resolution lands, so a bare findObject() can race
// it. Generous since CompilationMode.Full()'s first iteration is also paying real disk/JIT cost.
private const val FIND_TIMEOUT_MS = 10_000L

/**
 * Repeatable, scriptable replacement for the live-device Perfetto sessions used to root-cause the
 * Home timeline's scroll jank (#176, PR #205/#206) — run `./gradlew
 * :macrobenchmark:connectedBenchmarkAndroidTest --tests
 * "fr.bsodium.cron.macrobenchmark.HomeTimelineScrollBenchmark"` against a connected device and read
 * the printed median/P50/P90/P99 frame duration + jank-frame % directly, no Perfetto trace capture
 * or Android Studio Profiler needed. See docs/perf-profiling-plan.md.
 */
class HomeTimelineScrollBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun flingScroll() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        setupBlock = { startActivityAndWait() },
    ) {
        val tag = By.res(packageName, "home_timeline")
        check(device.wait(Until.hasObject(tag), FIND_TIMEOUT_MS)) {
            "home_timeline never appeared — is onboarding complete and an Anthropic key set on this device?"
        }
        val timeline = device.findObject(tag)
        timeline.setGestureMargin(device.displayWidth / 5)
        timeline.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
