# Compose Preview quirks

Known places where Layoutlib's JVM renderer diverges from the on-device HarfBuzz/Skia stack
and requires a workaround gated on `LocalInspectionMode.current`.

---

## Negative letter-spacing clips the last glyph

**Symptom:** The last character of an expanded `LargeFlexibleTopAppBar` title is clipped in
Android Studio's Preview renderer.

**Root cause:** `LargeFlexibleTopAppBar` sets `LocalTextStyle` to `DisplaySmall` inside its
title slot (via `ProvideContentColorTextStyle`). The project's custom `Typography.displaySmall`
carries `letterSpacing = (-0.02).em`. Layoutlib's JVM text shaper applies negative tracking to
all N characters **including the last**, shrinking the measured advance slightly below the
terminal glyph's actual ink extent. Because `TopAppBarLayout` clips its rows to bounds, the
rightmost ink is cut off.

On a real device, HarfBuzz/Skia handles negative tracking differently and the glyph is not
clipped.

**Fix (in `PageAppBar.kt`):** Branch on `LocalInspectionMode.current` and zero `letterSpacing`
in the preview path. Also pin `fontFamily = FontFamily.SansSerif` (synchronous, no download
wait) and `fontWeight = FontWeight.Normal` so the preview uses a stable, resolved typeface.

```kotlin
style = if (LocalInspectionMode.current) {
    LocalTextStyle.current.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
} else {
    LocalTextStyle.current.copy(
        fontFamily = CronTypography.pageTitle.fontFamily,
        fontWeight = FontWeight.Normal,
    )
}
```

---

## Status-bar inset inflates Scaffold content offset

**Symptom:** Content in a `Scaffold`-based preview rendered at 300 dp height is pushed partially
off the bottom of the canvas because the top padding is larger than expected.

**Root cause:** Layoutlib simulates a status-bar window inset even in `@Preview`. The `Scaffold`
respects this inset and inflates `calculateTopPadding()`, eating into the available canvas
height.

**Fix (in `PageAppBar.kt`):** Pass `WindowInsets(0)` to the app bar in inspection mode so the
Scaffold receives zero insets and the full canvas height is available to content.

```kotlin
windowInsets = if (LocalInspectionMode.current) WindowInsets(0) else TopAppBarDefaults.windowInsets
```
