# Color roles — tone, containers, and nesting

Material 3's dynamic color (HCT) generates the whole scheme, including the wallpaper-derived accents used in `docs/expressive.md` ("apply varied accents"). This file is the rule set for using those accents without breaking contrast or clashing hues. Read it before adding a new accent-colored surface or nesting one component's color inside another's.

## Contrast comes from tone, not hue

MD3's HCT color space guarantees accessible contrast through **tone** (relative lightness) — every `on*` role is defined at a fixed tone distance from its paired role, regardless of what hue the dynamic palette lands on. Two colors can differ wildly in hue and still fail contrast if their tones are close; two colors can share a hue family and still be perfectly readable if their tones are far apart. Don't reach for "a darker shade of the same hue" as an ad hoc contrast fix — reach for the next role in the tone ladder instead (`surfaceContainer` → `surfaceContainerHigh` → `surfaceContainerHighest`, or a `Container`/`on*Container` pair).

## Container / on-container pairing is not optional

Every `*Container` role has exactly one matching foreground role, and they're generated as a pair:

- `primaryContainer` ↔ `onPrimaryContainer`
- `secondaryContainer` ↔ `onSecondaryContainer`
- `tertiaryContainer` ↔ `onTertiaryContainer`

**Never mix them** — don't put `onSecondaryContainer` text on a `tertiaryContainer` background. Mismatched pairs aren't guaranteed sufficient contrast (they happen to work with today's dynamic palette and can silently fail on a different wallpaper) and read as visually discordant even when they do pass contrast.

Worked example — `TriggerVisuals.kt`'s `TimelineAccent`: every category maps to a role pair together, never separately:

```kotlin
internal fun TimelineAccent.containerColor(): Color = when (this) {
    TimelineAccent.Body -> MaterialTheme.colorScheme.tertiaryContainer
    TimelineAccent.AlarmAction -> MaterialTheme.colorScheme.secondaryContainer
    TimelineAccent.Schedule -> MaterialTheme.colorScheme.surfaceContainerHigh
}

internal fun TimelineAccent.onContainerColor(): Color = when (this) {
    TimelineAccent.Body -> MaterialTheme.colorScheme.onTertiaryContainer
    TimelineAccent.AlarmAction -> MaterialTheme.colorScheme.onSecondaryContainer
    TimelineAccent.Schedule -> MaterialTheme.colorScheme.onSurfaceVariant
}
```

Call sites always read both functions off the *same* `TimelineAccent` value, so a container and its foreground can never drift apart.

## Nesting a different palette inside another's container

The pairing rule above covers a color touching the neutral page background. It gets harder when one accent-colored element has to sit **inside** a surface that's already tinted by a *different* palette — e.g. a `tertiaryContainer` chip inside a `primaryContainer` card. Three sanctioned strategies, in order of preference for a small nested element:

1. **Buffer via surface level.** Step the *parent* container up the neutral tone ladder (`surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh`) before placing the child, so the child's own container reads as a deliberate accent against a neutral backdrop instead of clashing hue-on-hue.
2. **Harmonize.** Blend a fraction of the parent's hue into the nested accent so their color temperatures agree instead of fighting. (No blend utility is wired into this app yet — reach for strategy 1 or 3 unless a case specifically needs it.)
3. **Go neutral/outlined.** Skip the accent entirely for the nested element — a plain `surfaceContainerHigh` (or an outlined style) sidesteps the clash altogether. This is the right default for small, secondary elements where the accent doesn't carry meaning worth the risk.

Applied example — none currently in this codebase. `AiRunNode`'s content slot (`SessionTimeline.kt`) is a plain `Column` of `Text` sitting directly on the neutral page background, not a card, and carries no nested chips — see "Current audit" below for the actual current state of accent nesting here.

## Current audit

As of this pass, every accent usage in the timeline (`TriggerVisuals.kt`, `TimelineNode.kt`'s `TimelineAnchor.Icon`/`Latest`) only ever touches the neutral page background directly — no accent is nested inside a differently-hued container yet. No changes were needed; this doc exists so the next addition doesn't regress it.

Round 3 added one more direct (non-nested) `primary` usage: the latest timeline run's kicker label (`SessionTimeline.kt`'s `AiRunNode`) is now tinted `colorScheme.primary`, deliberately matching the `LatestAnchor`'s `primary` fill and the countdown card's (`NextAlarmCard.kt`) `primary` fill, so the newest run visually rhymes with the alarm card above it. All three still only touch the neutral page background — none nests inside a differently-hued container — so this is a plain on-role pairing (`primary` on `surface`), not a case requiring one of the three nesting strategies above.
