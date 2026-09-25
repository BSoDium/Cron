package fr.bsodium.cron.testutil

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext

/** Frame offsets (ms since the mutation that triggers an entrance/placement animation) [captureFilmstrip]
 *  samples by default. A single end-state screenshot (the pattern most `*ScreenshotTest.kt` files use)
 *  can only catch a defect that's still visible once everything has settled; a defect that's only
 *  visible *during* the transition (a placement overlap, a row briefly double-exposed) needs the whole
 *  filmstrip, not one frame.
 *
 *  Starts at 16ms (one frame), not a literal 0: `MainTestClock.advanceTimeBy` only actually pumps a
 *  frame (composing/measuring/laying out whatever state mutation just landed) once the requested delta
 *  crosses a real frame boundary — a `0`-delta "frame" silently captures the *pre*-mutation tree in
 *  practice, missing the just-inserted row's very existence, not just its unanimated entrance. */
internal val FILMSTRIP_FRAMES_MS = listOf(16L, 50L, 100L, 170L, 290L, 500L)

/** One frame's worth of `mainClock` advancement — see [captureFilmstrip]'s `primeForLaunchedEffect` KDoc. */
internal val PRIMING_FRAME_MS = FILMSTRIP_FRAMES_MS.first()

/** Advances [ComposeContentTestRule.mainClock] to each absolute offset in [atMillis] (relative to
 *  wherever the clock already sits) and captures one frame per offset under `$filePrefix-$name-tXXXms`,
 *  so a single scenario yields a filmstrip rather than one resting screenshot, and a human reviewing
 *  `app/build/outputs/roborazzi/` sees the transition as an ordered sequence.
 *
 *  [primeForLaunchedEffect] (default `true`) ticks one priming frame before the [atMillis] loop starts:
 *  a `LaunchedEffect(Unit)` fired during `setContent` doesn't run its body — and so doesn't perform the
 *  state mutation that triggers the very animation being captured — until the *first* `mainClock` frame
 *  tick after `setContent` returns (`autoAdvance = false` means `setContent` itself never ticks one).
 *  Without it, [atMillis]'s own first tick is spent running the effect rather than animating its
 *  result, so every frame in the loop would silently be one tick short of what its file name claims.
 *  Pass `false` when the triggering mutation is instead a plain `MutableState` write already applied
 *  directly in the test body right before this call — priming again there would double-count a frame
 *  that already happened, making the first captured frame land one tick later than its name claims. */
internal fun ComposeContentTestRule.captureFilmstrip(
    filePrefix: String,
    name: String,
    atMillis: List<Long> = FILMSTRIP_FRAMES_MS,
    primeForLaunchedEffect: Boolean = true,
) {
    if (primeForLaunchedEffect) mainClock.advanceTimeBy(PRIMING_FRAME_MS)
    waitForIdle()
    var elapsed = 0L
    for (target in atMillis) {
        val delta = target - elapsed
        if (delta > 0) mainClock.advanceTimeBy(delta)
        elapsed = target
        onRoot().captureRoboImage(filmstripFrameFile(filePrefix, "$name-t${target}ms"))
    }
}

/** An explicit `filePath` given to `captureRoboImage` resolves relative to the JVM's *current working
 *  directory* by default (Roborazzi's `RelativePathFromCurrentDirectory` strategy), not the module's
 *  `build/outputs/roborazzi/` — only its own zero-arg `generateFilePath()` honors
 *  [provideRoborazziContext]'s `outputDirectory` automatically. Every frame [captureFilmstrip] records is
 *  named explicitly, so this prefixes that same output directory back on by hand to land in the usual
 *  place `docs/screenshot-testing.md` documents. */
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
internal fun filmstripFrameFile(filePrefix: String, name: String): String =
    "${provideRoborazziContext().outputDirectory}/${filePrefix}_$name.png"
