package fr.bsodium.cron.perf

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

private const val TAG = "JankStats"

/**
 * Wires always-on jank logging for [activity]'s window — every dropped frame logs its duration and
 * whatever state tags are current (see [rememberJankMetricsState]) straight to Logcat
 * (`adb logcat -s JankStats:W`), no Profiler UI required. See docs/perf-profiling-plan.md.
 */
fun trackJank(activity: Activity) {
    JankStats.createAndTrack(activity.window) { frameData ->
        if (frameData.isJank) {
            Log.w(TAG, "${frameData.frameDurationUiNanos / 1_000_000}ms — ${frameData.states}")
        }
    }
}

/**
 * The composition's [PerformanceMetricsState] — null until [LocalView] is attached to a window,
 * which happens before the first frame draws. Use [PerformanceMetricsState.putState] on the result
 * to tag jank log lines with what was on screen.
 */
@Composable
fun rememberJankMetricsState(): PerformanceMetricsState? {
    val view = LocalView.current
    return remember(view) { PerformanceMetricsState.getHolderForHierarchy(view).state }
}
