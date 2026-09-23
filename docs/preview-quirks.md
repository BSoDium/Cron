# Compose Preview quirks

Known places where Layoutlib's JVM renderer diverges from the on-device HarfBuzz/Skia stack
and requires a workaround gated on `LocalInspectionMode.current`.

---

## Preview background doesn't match the real page background

**Symptom:** An element with fine contrast against the app's actual background — e.g. a
surface-tinted icon or a translucent card — renders correctly on-device but is barely visible
or invisible in Compose Preview.

**Root cause:** `MainActivity`'s root `Scaffold` sets `containerColor = CronColors.pageBackground`
(`surfaceContainer` in light mode, `surface` in dark mode) — not M3's default
`colorScheme.background`. A bare `CronTheme { ... }` preview has no `Surface` behind it, so
Layoutlib renders it on a transparent/white canvas (or, if manually wrapped in
`Surface(color = MaterialTheme.colorScheme.background)`, the wrong shade) instead of the app's
real page color.

**Fix:** Use `CronPreview { ... }` (`ui/theme/CronPreview.kt`) instead of `CronTheme { ... }` in
every `@Preview` function. It wraps content in `CronTheme` plus a `Surface` painted with
`CronColors.pageBackground`, matching what actually ships. Don't hand-roll a
`Modifier.background(CronColors.pageBackground)` on the root of a preview — `CronPreview`
already covers it.

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

---

## `BlendMode`-masked fades don't render in Interactive/Compose Preview

**Symptom:** A shape faded with the `graphicsLayer` + `drawWithContent` + `BlendMode.DstIn`/`SrcIn`
gradient-mask pattern (see `ui/components/TextShimmer.kt`, `ui/screens/home/components/Fades.kt`)
renders the full gradient correctly under Roborazzi (`GraphicsMode.NATIVE`, real native Skia) and
would on a real device, but shows **no fade at all** in Android Studio's Interactive Preview
panel — the shape stays fully opaque right to its edge.

**Root cause:** Layoutlib's JVM/software rendering path doesn't correctly apply `BlendMode`
content-masking composited through an offscreen `graphicsLayer`, unlike Robolectric's
`GraphicsMode.NATIVE` (genuine native Skia) or a real device's Skia/RenderThread pipeline.

**Fix:** Don't reach for `BlendMode`-masked fades on anything you need to actually see in
Preview. Prefer a plain alpha-blended scrim instead — a `Box` with
`Modifier.background(Brush.verticalGradient(...))` fading toward the real background color —
which needs no special compositing and renders identically in Preview, Roborazzi, and on-device.
See `ui/screens/home/components/TimelineSkeleton.kt`'s fade-out scrim for the pattern.

`TextShimmer.kt` and `Fades.kt` both predate this finding and still use the `BlendMode` pattern
unguarded — not fixed here since they're outside this change's scope, but any agent touching
them should be aware their fade is Preview-invisible.
