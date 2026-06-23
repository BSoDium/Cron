# Animation previews & inspectability

The primary workflow for testing motion is Android Studio's **Animation Inspector**. Every animated component must be previewable and scrubbable without deploying. CI enforces this via `./gradlew :app:checkAnimationPreviews`.

## Label every animation

Every call to `animate*AsState`, `updateTransition`, `AnimatedVisibility`, `AnimatedContent`, `Crossfade`, and `rememberInfiniteTransition` **must** include a `label` parameter — short kebab-case, scoped to the component, describes the semantic purpose:

```kotlin
val scale by animateFloatAsState(
    targetValue = if (pressed) 0.92f else 1f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "card-press-scale",
)
```

Without labels the Inspector shows "Float 1", "Float 2" — useless. When touching a file with unlabeled calls, add labels before finishing.

## What requires an animation preview

A `@Preview` is mandatory for any composable that directly uses animation APIs (`animate*AsState`, `AnimatedVisibility`, `AnimatedContent`, `updateTransition`, `Crossfade`, `rememberInfiniteTransition`, `Animatable`). Suppress with `@Suppress("AnimationPreviewNotRequired")` for animations that only run inside a parent's preview.

Do NOT preview: pure layout containers, static wrappers, composables whose only motion is inherited from a parent's enter/exit.

## Preview design for the Inspector

**Interactive previews (preferred).** Internal `remember { mutableStateOf(...) }` toggled by `Modifier.clickable` — the Inspector captures the live transition:

```kotlin
@Preview(name = "ExpandableCard — interactive")
@Composable
private fun ExpandableCardPreview() {
    CronTheme {
        var expanded by remember { mutableStateOf(false) }
        ExpandableCard(
            expanded = expanded,
            modifier = Modifier.clickable { expanded = !expanded },
        )
    }
}
```

For >2 states or gesture-driven animations, add multi-variant static previews instead.

**Infinite animations** are self-driving — one preview, the Inspector picks them up.

## State hoisting for inspectability

Hoist the triggering state as a parameter. Never wire a ViewModel or repository into a Preview — use `private val PREVIEW_*` constants or `private fun preview*(...)` factories.

## Preview file organization

Co-locate at file bottom by default. Split to a sibling `*Previews.kt` when mock data setup exceeds ~20 lines.

## Naming

- Preview functions: `private`, `*Preview` suffix, variant after `—`: `"Card — expanded"`.
- Labels: kebab-case, semantic: `"reasoning-reveal"`, `"nav-container"`, `"skeleton-pulse"`.
- Mock data: `private val PREVIEW_<THING>` or `private fun preview<Thing>(...)`.
