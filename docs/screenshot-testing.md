# Screenshot testing with Roborazzi

Roborazzi renders `@Composable` functions to PNG on the host JVM via Robolectric — no emulator or device needed. Agents use it to visually verify UI changes from the CLI.

## Why Roborazzi (and when to migrate)

The project already runs Robolectric with `@GraphicsMode(NATIVE)`. Roborazzi hooks into that same pipeline, so there's no second graphics renderer to maintain (unlike Paparazzi's standalone Layoutlib). Google's official Compose Screenshot Testing plugin is the planned long-term replacement once it exits experimental status and supports `private` previews.

## Agent workflow

**Record baselines** (after accepting a UI change):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:recordRoborazziDebug
```

**Compare against baselines** (check for regressions):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:compareRoborazziDebug
```

**Verify** (CI — same as compare, fails on diff):

```sh
./gradlew :app:verifyRoborazziDebug
```

## Reviewing a captured screenshot

A screenshot that rendered without crashing is not a screenshot that's correct. CLAUDE.md says so
explicitly, and `docs/color-roles.md`'s round-by-round history is forty rounds of evidence that
"it rendered" and "it looks right" are different bars — a holistic glance at the PNG reliably
misses exactly the defects this project has repeatedly shipped. Check it against this list; if you
can't say which of these you checked, you haven't reviewed the screenshot, you've looked at it:

- **Alignment** — is every icon/glyph actually centered on its anchor, not just placed inside a
  `Box` that happens to be centered? Most Material Symbols glyphs aren't symmetrically padded
  within their own bounding box, so "centered in the layout" and "looks centered" are different
  facts. Measure, don't eyeball — see `AlignedFirstGlyph.kt` for the pattern this repo settled on
  after the eyeballed-offset version kept drifting.
- **Spacing** — does every gap/padding trace to a `Spacing`/`Radius` token, not a value that
  happened to look right in this one screenshot at this one content length?
- **Weight** — does this icon's or text's weight match its siblings on the same screen? A weight
  choice that's fine in isolation reads as inconsistent next to the rest of the screen.
- **Contrast/tone** — does it hold up in both light and dark? A single-theme screenshot only
  proves one of the two.

## Known gaps in this workflow

- Robolectric/Layoutlib renders text and vector metrics differently from the real on-device
  Skia/HarfBuzz stack (see `docs/preview-quirks.md`). A screenshot that looks right here isn't a
  guarantee it looks right on a real device, particularly for font/icon metrics — treat it as one
  signal, not the final word, for anything measurement-sensitive.
- There are no committed baselines yet, so `verifyRoborazziDebug` has nothing to diff a new
  screenshot against — #147 tracks wiring that into CI. Until it lands, every screenshot is
  first-look judgment against the list above, not a regression check against a prior approved
  state.

## Where PNGs land

All output goes to `app/build/outputs/roborazzi/` (gitignored). The agent workflow is record-on-demand: capture before a change, make the change, compare, read diffs. No committed baselines needed for this workflow.

If CI regression testing is added later, configure `roborazzi.output.dir` to a source-tracked directory and commit the baselines.

## Adding a screenshot test

1. Create a test in the same package as the composable under test.
2. Use the standard test annotations and `createComposeRule()`.
3. Call the composable directly with deterministic data — don't call the `@Preview` function (they're `private`).
4. Capture with `composeTestRule.onRoot().captureRoboImage()`.

```kotlin
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MyComponentScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun default_state() {
        composeTestRule.setContent {
            CronTheme {
                MyComponent(/* deterministic args */)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
```

For composables with live timers or animations, set `composeTestRule.mainClock.autoAdvance = false` before `setContent` to prevent the test rule from waiting forever for idle.

## JDK consistency

Robolectric renders fonts differently across JDK versions. Record reference images on the same JDK that CI uses to avoid spurious diffs. The project uses the Android Studio bundled JBR.
