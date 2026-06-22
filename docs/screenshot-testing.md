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
