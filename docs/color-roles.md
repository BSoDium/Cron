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

Round 4 — timeline track. The two-tone vertical track (`TimelineNode.kt`) reuses `TimelineAccent.Body`'s `tertiaryContainer` for "asleep" stretches and `TimelineAccent.Schedule`'s `surfaceContainerHigh` for "awake" stretches — deliberately the *same* container roles already used for the `SleepOnset`/`OutOfBedConfirmed` event icons and the neutral "nothing happening" icons, rather than a new, undocumented pairing. The track is a plain fill touching only the neutral page background (no nesting), so none of the three nesting strategies above apply; the anchor icons sitting on/inside the tube carry their own correct `on*Container` pairing independently.

Round 5 — anchor halo, the nesting case Round 4 missed. Round 4's "no nesting" conclusion assumed the track was a passive background, but the anchor icons paint *directly on top of it*, and a `Body`-accent icon (`tertiaryContainer`) can land on an asleep track segment (also `tertiaryContainer`) — the exact same role on both sides, so the icon's circular fill has no edge against the track behind it. This *is* a nesting case, and it calls for strategy 1: buffer via surface level. A first pass punched a `CronColors.pageBackground` disc behind each anchor's own circle as a separate layered halo ring — it fixed the color collision, but on-device testing showed it still read as a sticker lifted off the tube (a ring of ground color around a disc is the classic "lifted, not embedded" cue), not the nested transit-stop look intended.

Round 6 — carved socket, replacing the layered halo. `TimelineNode.kt`'s `drawTrackSocket` now carves the anchor's silhouette directly out of the track's own path (`Path.op(trackPath, holePath, PathOperation.Difference)`) instead of layering a separate disc+ring on top — the anchor and the tube share one boundary, which is what actually reads as "set into" rather than "resting in front of." The carved rim is still `CronColors.pageBackground`, still `Spacing.xxs` wide, still strategy 1 in spirit (a neutral buffer below both track colors on the tone ladder) — only its geometric ownership changed, from a disc's backing to the tube's own interior edge. The hole is filled with the same accent each anchor variant always used (`Icon.containerColor`, `Loader`'s `primaryContainer`, `Plain`'s `outlineVariant`), now flush in the tube's plane, so the same-role collision stays structurally impossible as a property of the geometry, not a color patch. Anchors are also now inscribed to exactly fill `TRACK_WIDTH` rather than overflowing it, reinforcing the "bead threaded on the line" read. `TimelineAnchor.Latest`'s morphing `Cookie9Sided` shape (`LatestAnchor.kt`) is exempt from all of this — it's non-circular, so it can't share a carved socket with the tube, and it's meant to break out of the track as the "major interchange" rather than nest in it; it keeps the old two-segment track drawing with no hole and no halo.

Round 7 — grow the track, decouple the track's role from the icon's, and give every anchor a real gap. Round 6's Latest exemption and Round 4's track-color reuse both turned out to be the wrong call once tested overnight on-device: the Latest anchor visibly overflowed a track sized for a plain circle, and reusing `TimelineAccent.Body`'s `tertiaryContainer` for the asleep track segment meant the track inherited that role's M3-spec-intentional light/dark asymmetry (pale in light scheme, bold in dark scheme) as its *only* color, which reads as a genuine contrast bug rather than the icon-pairing feature it originally was. A first pass at fixing the Latest overflow grew `TRACK_WIDTH` and made every anchor's hole exactly fill the tube's cross-section (content + 2×rim == `TRACK_WIDTH`, rim painted the same accent as the content) — but a second on-device screenshot showed this reads as "the tube becomes the icon," not "the icon nests in the tube": a circular hole inscribed to exactly match a straight-sided tube's width only touches the tube's walls at its exact vertical center, and filling the *entire* hole (content and rim alike) with one accent color meant the icon's own glyph sat in a disc as wide as the whole tube, with barely any visible margin around it.

The fix: split the hole into two concentric layers instead of one. `ANCHOR_GAP` (`Spacing.xs`) is now a genuinely **neutral** buffer — the socket's outer hole (`content diameter + 2×ANCHOR_GAP`) is carved out of the track and filled with `CronColors.pageBackground`, and each anchor's own accent-colored content (the icon's container disc, the Loader's indicator, the Latest morph's fill, all already self-painting except `Icon`, which now gets an explicit `drawCircle` in the same `drawBehind` pass) is drawn centered *inside* that neutral hole at its own smaller diameter. The track's own color is now visible framing every anchor, which is what actually reads as "nested in the line" — a bead sitting inside the tube, not the tube's cross-section replaced outright. `TimelineAnchor.Latest`'s content diameter shrinks to match a regular icon's (`18dp`) rather than the full tube width, since its own morph silhouette, bold `primary` fill, and arrival spring are enough to distinguish it without also being oversized.

Two further changes from the same round:

- **`TRACK_WIDTH` grows from 20dp to 28dp** (`Spacing.xxl + Spacing.xs`) — comfortably larger than any anchor's outer footprint (content + gap), so nothing pokes past the tube's edges the way Latest used to.
- **The asleep track segment moves from `tertiaryContainer` to bare `tertiary`** (`SessionTimeline.kt`'s `trackColorFor`) — a bold accent role that sits at a consistently visible tone in both light and dark schemes, unlike a `*Container` role. This is the same reasoning `LatestAnchor` and `AiRunNode`'s kicker already apply to `primary`. **This deliberately un-reuses Round 4's role-sharing with `TimelineAccent.Body`** — `TriggerVisuals.kt` is untouched, so a sleep-event icon (e.g. "You fell asleep") still carves a `tertiaryContainer`/`onTertiaryContainer` disc, now into a bolder `tertiary` tube, always separated from it by the same neutral `ANCHOR_GAP` ring every anchor gets — so this can't regress icon legibility, only fix the track's own cross-theme contrast.

Round 8 — calmer track color, single-carve nesting with an end-cap-aware radius, importance-tiered icon contrast. Round 7's neutral-gap fix itself read as broken on a fresh on-device pass: bare `tertiary` was too vivid for the asleep track, and the two-layer socket (a neutral ring plus a separately-drawn accent disc) looked like two concentric circles rather than one nested shape.

- **Asleep track color is now a blend, not a flat role**: `lerp(scheme.surfaceContainerHighest, scheme.tertiary, 0.22f)` (`ASLEEP_TRACK_TINT`), the same `lerp` technique `Theme.kt`'s `liftedSurfaces()` already uses. `surfaceContainerHighest` is the most-elevated neutral surface, generated symmetrically on the tone ladder in both themes — blending in a minority of `tertiary` keeps that symmetry (no light/dark asymmetry reintroduced) while calming the full-chroma vividness bare `tertiary` had, and it still reads as one rung more elevated than the awake `surfaceContainerHigh`.
- **The two-layer gap is gone — back to one carved hole per anchor, filled directly with its own accent color.** `ANCHOR_GAP` (a neutral-filled ring) is replaced by `ANCHOR_PADDING` (`Spacing.xxs`), which is no longer painted at all — it's just margin left uncarved, so the track's own (now calmer) color shows through around the anchor instead of a separately-colored ring. `TimelineNode.kt`'s `socketAccentColor()` (replacing `socketFillColor`) is now exhaustive over all four anchor types and fills every hole directly — including `Loader`/`Latest`, which already self-paint the same color on top (no seam), and `Plain`, whose own self-painted dot is now redundant and removed for the socket-carved case (the carved fill *is* the dot).
- **End-cap-aware radius**: at a track's true `isFirst`/`isLast` terminus, the anchor's center coincides exactly with the tube's own rounded-cap center, so the carved hole there uses `max(contentRadius, halfTrack − ANCHOR_PADDING)` instead of a plain content-sized circle — a smaller concentric version of the tube's own rounded end, nesting properly instead of reading as an arbitrary circle dropped onto a curved edge. `TRACK_WIDTH` grows from 28dp to `Spacing.xxxl` (32dp) so this end-cap disc has real room.
- **Icon backgrounds now carry an *importance* tier, orthogonal to `TimelineAccent`'s hue family** (`TriggerVisuals.kt`'s new `TimelineImportance`): `Prominent` events (real alarm/state changes — dismissed, snoozed, the hard-latest safety alarm, a wake-window opportunity, confirmed out-of-bed) keep their hue's real container tone, which sits far enough from either track color to pop; `Muted` events (ambient sensing, routine schedule checks) always drop to a neutral `surfaceContainerLow`/`onSurfaceVariant` pair regardless of hue, so they recede into the track instead of competing for attention. Container and on-container are still chosen together as a pair per tier — the "not optional" rule above still holds, just with importance as an added key.

Round 9 — the track is two shapes, not one; the Latest anchor's footprint finally matches its carve. A fresh on-device pass found the sleep/awake transition still read as a sharp cut (Round 8 only carved a single shared path, colored in two halves with no rounding at the transition), and the Latest anchor still overflowed the track's top cap in some rows.

- **The Latest anchor's ~2dp offset was a real footprint bug, not a geometry-tuning issue.** `TimelineAnchor.diameter()` (which the carve's `anchorCenter` math is built on) assumes every anchor's outer footprint is `contentDiameter() + 2×ANCHOR_PADDING`, matching how `Icon`/`Plain`/`Loader` are wrapped in `AnchorFootprint`. `Latest` alone bypassed `AnchorFootprint`, rendering its own morph fill in a bare box sized to just `contentDiameter()` — so its actual visual center sat 2dp off from where the carve was centered, letting the morph shape poke past the track's rounded cap. Routing `Latest` through `AnchorFootprint` like every other anchor (`AnchorCircle`'s `Latest` branch) fixed it with no signature change.
- **The track is now genuinely two overlapping shapes**: an always-present, round-capped **background** spanning the whole visible track (rounded only at the list's true first/last row), and a separate round-capped **sleep-pill overlay** drawn on top wherever a contiguous asleep stretch occurs — capped at its *own* onset/wake row, not just the list's global boundary. Both are carved independently via the same `Path.op(..., PathOperation.Difference)` technique (no antialiasing seam), so an icon marking a sleep block's own start or end nests concretely into *that pill's* rounded terminus (`atAnyCap` now also checks `sleepOpensHere`/`sleepClosesHere`, not just global `isFirst`/`isLast`) — the actual "background track + independently-capped sleep pills" mental model the user asked for, not a two-tone single shape. `TimelineNode`'s public signature changed from `trackColorAbove/Below: Color` to `isAsleepAbove/Below: Boolean`, since the track's two colors are now fixed constants (`trackColorFor`) resolved inside `TimelineNode`, not arbitrary per-half values a caller supplies.

Round 10 — a rounded cap only nests if its rect is trimmed to the anchor, not just rounded; icon glyph ink wasn't centered in its own box. A fresh on-device pass found the sleep pill overflowing past the track's true edge in some rows and falling short in others, plus every icon still reading a few px off-center inside its circle — a report that had already surfaced once in Round 9 without a real fix (only `Latest`'s own footprint mismatch was addressed that round).

- **A rounded cap's curvature is only concentric with the carved hole if the rect it's rounding is trimmed to stop exactly at the anchor's center — rounding an untrimmed rect just rounds the wrong point.** Before Round 9, the single shared track path trimmed its own rect to the anchor (`top = if (isFirst) center else 0f`) at a cap; Round 9's rewrite of `bgPath` kept the *rounding* flag but dropped the *trim*, always spanning `[0, size.height]` — so an `isFirst` cap's curvature centered at `(cx, 0 + halfTrack)`, generally a different point than the hole's own center (`anchorCenter`). The overlay pill already trimmed correctly for its *own* local cap (`sleepOpensHere`/`sleepClosesHere`), but reused the same untrimmed branch whenever a **global** cap coincided with a *continuing* sleep state (`isFirst && isAsleepAbove`, `isLast && isAsleepBelow`) — exactly the two reported directions (overflow at one end, falling short at the other). The fix keys both `bgPath`'s and the overlay's rect extents off the same `roundTop`/`roundBottom` booleans that decide rounding, so a trimmed edge and a rounded edge are always the same edge — `bgTop = if (isFirst) center else 0f` (background only cares about the list's true ends), `overlayTop = if (roundTop) center else 0f` (the overlay's cap folds in the local sleep boundary too).
- **The icon glyph itself wasn't centered in its drawing canvas — a font-rendering issue, not a Compose layout bug.** `Symbol()` (`MaterialSymbols.kt`) drew every glyph at a fixed `(size/2, size)` origin (`Paint.Align.CENTER` for x, baseline pinned to the canvas's bottom edge for y), assuming that always maps the em-square onto the icon's own box. A Roborazzi render + pixel measurement across 5 different icons/positions/colors found a consistent rightward bias regardless of which icon or where it sat — ruling out per-glyph ink asymmetry (different glyphs would drift differently) and pointing at the shared drawing code instead. The fix measures the glyph's *actual* ink rectangle via `Paint.getTextBounds` for the current size/variation settings and centers the draw origin on that, the same "measure the real thing, don't assume a fixed origin" approach `AlignedFirstGlyph` (`NextAlarmCard.kt`) already uses — confirmed via direct pixel measurement afterward (hole-fill bbox center and glyph-ink bbox center now coincide exactly on both axes). This is a one-line-call-site change in the shared `Symbol()` composable, so every icon in the app is centered more accurately, not just the timeline's.
- **A session's first turn can now carry over a real PREV time.** `AiIterationUi.previousAlarmTime` only ever looked within its own session's turn history, so a fresh session's turn 0 always showed NEW alone. `HomeViewModel.timelineFlow` now looks at the most-recent *older* session's own last resolved alarm time (history is guaranteed most-recent-first by `SessionDao.findPaginated`'s `ORDER BY createdAt DESC`) and patches turn 0's `previousAlarmTime` with it when null — a genuine intra-session PREV is never overwritten.

Round 11 — fold the "Latest · HH:MM" status line into the kicker. The hero row showed a static kicker (`iter.systemMessage`, e.g. "PLANNED MANUALLY") above the PREV › NEW time, and a separate "Latest · HH:MM" line below in the content slot — two places carrying a time-ish fact where one would do. `SessionTimeline.kt`'s `AiRunNode` now computes a `kickerSuffix` off the sealed `RunKind` (exhaustive `when`, no `else`): a base plan (`ScheduledBase`/`ManualBase`) gets a relative "· Xm ago" (reusing `OldPlanFooter.kt`'s `rememberRelativeAgo`, promoted `private` → `internal`), since a base plan can be many hours old and the evening plan vs. a replan minutes ago read very differently; a `Replan` gets an absolute "· at HH:MM" instead, since replans usually cluster close together and an exact clock time is more useful for correlating against the other rows around them. The standalone status-line `Text` is gone; `content` collapses to `null` when there's no `heroHeadline` prose, rather than always building a now-empty `Column`.

Round 12 — the flush-nest illusion only works for a circular hole; a live per-Paint variation override doesn't guarantee measurement and rendering see the same glyph.

- **`TimelineAnchor.Latest`'s scalloped morph shape broke the cap-nesting illusion Round 8 built for circular anchors.** At any cap, the background rect is deliberately trimmed to a single point at the anchor's own center (`bgTop = center` when `isFirst`, with `topLeft`/`topRight` radius equal to the rect's own half-width) — the two rounded corners coincide into one point, so the background has *zero* area above it. The illusion that fills that gap: the anchor's own hole-fill is deliberately inflated to `endCapRadiusPx` at a cap, so its upper half *is* the visible rounded cap, just recolored in the anchor's accent — this only reads correctly when that hole-fill traces a perfect circle (true for `Icon`/`Plain`/`Loader`). `Latest`'s `Cookie9Sided` morph is scalloped, not circular, so inflating it the same way made its petals visibly overshoot the implied arc — exactly the "test plan shape overflowing the track" report. Separately, and only invisible by coincidence: `LatestAnchor.kt`'s own `Canvas` is hardcoded to `LATEST_ANCHOR_SIZE` (18dp) regardless of the inflated hole radius, so the carved hole and the actual painted fill were already two different sizes, papered over only because both use the same `primary` fill. The fix: `TimelineAnchor.Latest` opts out of the cap inflation entirely (`holeRadiusPx` stays at `contentRadiusPx` even `atAnyCap`) and always renders at its true size, accepting the same small `ANCHOR_PADDING` ring every anchor gets at an interior row — this also incidentally re-syncs the carved hole and `LatestAnchor`'s fixed-size fill, since neither depends on the other's independent inflation anymore.
- **A live per-Paint `fontVariationSettings` string isn't guaranteed to resolve identically on `Paint.getTextBounds`'s measurement path and `Canvas.drawText`'s render path.** Two icons (`AlarmOff`, `NotificationImportant`) still read a few px off-center on-device after Round 10's ink-bounds fix, while others (`PlayArrow`, `Bedtime`) didn't — same `Symbol()` code path, different glyphs, different result. The two off-center icons are **compound** glyphs (a bell plus a diagonal slash, a bell plus an exclamation badge) whose parts reshape relative to each other across the `wght`/`GRAD`/`opsz` axes; a simple convex glyph's silhouette barely changes shape across those same axes, so the same measure/render divergence stays invisible there. `Symbol()` now bakes those three axes into a real cached `Typeface` variant (`Typeface.Builder(file).setFontVariationSettings(...)`, keyed per weight/grade/opticalSize) instead of a live per-Paint override, so measurement and rendering are guaranteed to resolve the same physical glyph outline — `fill` stays on the cheap per-Paint path since it's animated per-frame elsewhere (`CronNavigationBar`'s tab-selection morph) and mostly affects interior strokework, not the outer bbox centering depends on. `Typeface.Builder` has no constructor that accepts an already-resolved `Typeface` or a `res/font` id directly, only a raw file/asset path, so the bundled subset is extracted once per process into the app's cache dir to get a real `File` to build from.

Round 13 — the track is a single continuous overlay, not per-row slices; every anchor is flush, not just the true caps. Two changes landed together because the second only became visible once the first shipped.

- **The track used to be drawn per-row**, each `TimelineNode.drawBehind` painting its own `[0..height]` slice with caps decided by that row's own `isFirst`/`isLast`/sleep flags. Under the list's `animateItem` glide+fade this was structurally fragile: on insert/remove, cap ownership flipped instantaneously between rows instead of gliding: `animateItem`'s fade faded each row's track slice along with its content, and adjacent slices could gap or overlap mid-transition. The fix decouples the track from the rows entirely: `TimelineNode` now renders only its glyph/content (transparent background) and reports its own window-space anchor center plus an `AnchorDescriptor` (radius, shape, accent, segment id, cap/sleep flags) into a shared `TimelineTrackRegistry` (`TimelineAnchorRegistry.kt`) via `onGloballyPositioned`/`SideEffect`, cleaning up via `DisposableEffect`. A single new painter, `TimelineTrackOverlay.kt`, sits behind the whole `LazyColumn` and draws the entire track in one pass: anchors are grouped by segment id (incrementing at each `DayHeader`), one round-capped background stadium is drawn per segment from its topmost to bottommost *currently composed* anchor (rounding only where that anchor is a true segment end — otherwise the stadium runs uncapped to the viewport edge, since the segment continues off-screen), continuous sleep pills are drawn the same way, and each anchor's own socket is carved and filled on top. Because it's one continuous path rather than N independent per-row slices, it physically cannot gap or re-cap while rows glide or fade — the caps just track the live position of whichever anchor is currently topmost/bottommost.
- **Once the track was a shared painter, Round 8's "only inflate a circular hole at a true cap" rule (`docs/color-roles.md` above) read as wrong, not just incomplete.** Every anchor not sitting at a true segment/sleep boundary stayed at its small nominal content radius (9dp) inside a 32dp-wide track, leaving a ~7dp ring of bare track color on every side — fine when the eye reads a row at a time, but glaringly disconnected once the whole track is visible as one continuous shape, confirmed against a reference showing every anchor (capped or not) nesting flush with only a thin rim. The fix folds every anchor size (`PLAIN_DOT_SIZE`/`ICON_DOT_SIZE`/`LOADER_DOT_SIZE`/`LATEST_ANCHOR_SIZE`) into one shared `FLUSH_ANCHOR_SIZE = TRACK_WIDTH − 2×ANCHOR_PADDING` (`TimelineNode.kt`), so `contentRadius + ANCHOR_PADDING == halfTrack` holds by construction for every anchor, at every position — the "never overflow the track" invariant is now structural rather than a per-site inflation check, and the cap-only `atCap`/`endCapRadius` branch in `TimelineTrackOverlay`'s socket carve is dead code, removed. `TimelineAnchor.Latest`'s `Cookie9Sided` morph gets the same flush diameter (a deliberate reversal of Round 12's "keep it smaller to avoid overshooting the cap arc," since `buildMorphPath` already scales the morph to fit exactly within whatever diameter it's given — the overshoot Round 12 fixed was specific to inflating *only at a cap*, which no longer happens).
- **A track-center bug surfaced once flush sizing made every anchor's position matter far more:** `TimelineTrackOverlay`'s `trackCenterX` originally read `placed.first().cx` off a `SnapshotStateMap`'s `keys`, whose iteration order is arbitrary hash order, not insertion or visual order. Every anchor centers in the same fixed-width gutter so this happened to work, but it's fragile against a differently-ordered or momentarily-stale entry; it's now `placed.map { it.cx }.average()`, order-independent and self-correcting.

Round 14 — variable anchor sizing, semantic valence shapes, a neutral Latest, and a completeness-gated overlay. Round 13's "every anchor flush" fixed a detachment problem but flattened the Google-Maps transit read the user wanted back; this round restores the interior/cap size distinction and layers several polish items on top.

- **Variable anchor sizing.** A cap anchor (segment top/bottom, or a sleep sub-track's own onset/wake) keeps `FLUSH_ANCHOR_SIZE` (28dp, 2dp rim); every interior anchor shrinks to a new `INTERIOR_ANCHOR_SIZE` (20dp, 6dp rim) — clearly nested, not a rounding difference. The `atCap` predicate (`isSegmentTop || isSegmentBottom || sleepOpensHere || sleepClosesHere`) is computed in `TimelineNode` (it already receives all four flags) and picks between the two sizes for all four anchor types with no per-type exception. The glyph itself (`ICON_GLYPH_SIZE`) doesn't shrink — it just fills more of its smaller disc. The Latest run is always the segment top, so it evaluates `atCap = true` and reads big with no special rule. The reported `contentRadiusPx` is wrapped in `animateFloatAsState(defaultSpatialSpec())` so a row that stops being a cap (superseded by an insertion) visibly shrinks instead of snapping.
- **Latest anchor drops its exclusive color, keeps its morph.** Its socket accent moves from `primary` to `primaryContainer` (and `LatestAnchor`'s glyph from `onPrimary` to `onPrimaryContainer`) — the same `primaryContainer`/`onPrimaryContainer` pair a *non-latest* AI run's icon already uses, so Latest no longer steps up to a bolder, exclusive fill. The `Circle → Cookie9Sided` morph is what distinguishes it now, not the color. The hero *text* styling (the `primary` kicker, the bold/italic PREV › NEW) is deliberately untouched — only the anchor icon's own color changed.
- **Semantic silhouette by valence.** A new `TimelineValence` (`TriggerVisuals.kt`), orthogonal to `TimelineAccent` (hue) and `TimelineImportance` (contrast): positive triggers (`OutOfBedConfirmed`, `WakeWindowOpportunity`) carve a `MaterialShapes.Flower`, negative ones (`HardLatestFired`, `AlarmSnoozed`) a `MaterialShapes.Triangle`, everything else stays a plain circle. A `Replan` inherits its trigger's valence; base plans are neutral. Rendered as a *static* `AnchorShape.Polygon` via a new `buildPolygonPath` (sibling to `buildMorphPath`, same measure-and-scale, no per-frame progress). `Plain`/`Loader` anchors stay circles (no category to derive a shape from); `Latest` keeps its dedicated morph regardless of valence.
- **Uniform track-to-text spacing, filled glyphs.** Every row's gutter-to-title gap is now `Spacing.md` (previously Latest-only got the wider gap), and every timeline anchor glyph renders `fill = 1f` (filled, not thin-outlined) — scoped to the two anchor call sites, not the app-wide `Symbol()`.
- **Smoother supersession.** `AiRunNode`'s hero-vs-plain `title`/`status` swap is wrapped in a `Crossfade(defaultEffectsSpec())` (`ai-run-hero-demote`) so a demote fades instead of cutting; combined with the animated anchor-radius shrink above, the whole demotion is one coordinated soft transition (content fades, anchor shrinks, color already neutral).
- **Never draw an incomplete registration set.** Two symptoms — an insertion briefly flashing the track to fill the whole screen, and the track visibly "catching up" frame-by-frame on nav-return — share one cause: `TimelineTrackOverlay` drew whatever partial set had registered *this frame*, even when the LazyColumn's own `visibleItemsInfo` said more anchor rows should be present. The fix computes the placed set (and whether it's *complete* — every anchor-eligible visible id, filtered by `contentType` so DayHeaders/spacers don't hold the gate, has both a descriptor and a position) in the *composition* phase via `derivedStateOf`, holds the last complete snapshot in a `remember`-ed state, and has `drawBehind` read only that stable snapshot. When a frame's set is incomplete, the overlay redraws the last complete one instead of a half-built track — so an insertion's one-frame gap (new top row not yet positioned) and a nav-return's empty-then-filling registry never render as a mismatched track.

Round 15 — the track/anchors visibly lagged a frame behind the row content during ordinary scrolling; a much thicker track; a new center-line spine.

- **The Round 14 completeness gate fixed the insertion/nav-return bugs but introduced a permanent one-frame lag on every ordinary scroll.** Gating the placed-set computation behind a composition-phase `derivedStateOf` + a `remember`-ed `lastComplete` write meant a position change (which happens every scroll frame, via `TimelineNode`'s `onGloballyPositioned`) had to wait for `TimelineTrackOverlay` to actually *recompose* before `lastComplete` updated — recomposition is scheduled for the next frame, not synchronous with the layout pass that triggered it. Row content has no such indirection (LazyColumn scrolling is pure layout, no recomposition needed), so it moved instantly while the track visibly trailed behind it. The fix moves the same completeness check and the "hold the last complete snapshot" cache directly into `drawBehind`, using a plain (non-`State`) `mutableListOf` mutated only from inside the draw lambda — the same pattern already used for the `scratch` `Path`. Reading `registry`/`listState`/`overlayOrigin` straight in the draw phase means a position change redraws the same frame it happens, no recomposition round-trip.
- **Track thickness.** `TRACK_WIDTH` grows from `Spacing.xxxl` (32dp) to a new `Spacing.xxxxl` (40dp) token, with every dependent size scaled to match: `INTERIOR_ANCHOR_SIZE` 20→24dp, `NODE_GUTTER` 40→48dp, both anchor glyph sizes 14→18dp. Icon glyphs also reverted from filled back to outlined (Round 14 tried filled at the smaller size; once the track and glyphs are bigger, outlined reads cleanly again).
- **A thin center-line "spine"** (`TimelineTrackOverlay.kt`) now runs down the track's vertical center, reinforcing the "this is a timeline" read the user asked for. It's drawn per-segment across the same `[bgTop, bgBottom]` extent as the background stadium, with a small gap carved around every anchor (`anchor.contentRadiusPx + SPINE_GAP`, so the gap scales with that anchor's own size) so the line never runs through a socket. Layered exactly like the background/sleep-pill fills: the awake-color spine spans the whole segment first, then an asleep-color spine overpaints each sleep pill's own sub-range on top — same whole-then-overlay technique, just for a line instead of a fill.

Round 16 — the sleep track becomes deliberately dark, and the spine gets quieter/roomier now that it's proven out.

- **The sleep track now reuses `colorScheme.primary` outright**, replacing Round 8's `lerp(surfaceContainerHighest, tertiary, …)` blend — the user wanted it "really dark, the same color as the next alarm card background," and `NextAlarmCard`'s own hero fill is exactly `colorScheme.primary`. This app's flat-design rule rules out a literal elevation for "the sleep track should feel raised"; borrowing the app's single boldest surface role is the color-only stand-in.
- **Spine colors follow the sleep track's new fill.** The awake spine moves from `colorScheme.outline` to the quieter `colorScheme.outlineVariant` — a deliberately less assertive divider role, so the line reads as a subtle detail rather than competing with the sockets it threads between. The asleep spine moves from `colorScheme.onTertiaryContainer` to `colorScheme.onPrimary` — the exact M3 pairing for the new `primary` sleep-track fill, and the "inverted" (light-on-dark) counterpart the neutral awake spine doesn't need.
- **`SPINE_GAP` doubles from 4dp to 8dp** for more visible breathing room between the line and each anchor's own shape, per direct feedback that 4dp read too tight once seen on-device.
- **Open question carried forward, not resolved this round:** `TimelineImportance.Muted`'s `surfaceContainerLow` container (a light neutral, meant to "recede into the track") was tuned against the old blended-tertiary asleep track; against the new bold `primary` fill it may now read with much *more* contrast than intended. Not changed here since it wasn't part of what was asked — worth a dedicated look if a muted icon on the sleep track reads oddly on-device.

Round 17 — outline tried and rejected; elevation-based interior anchors instead; a subtler spine.

The bold `primary` sleep track from Round 16 resurfaced the original contrast bug from a different
angle: a per-trigger/valence `*Container` accent (an MD3 role whose identity is in hue, not
lightness) could render nearly indistinguishable from whichever track sat behind it. A same-round
fix drew a thin `colorScheme.outline` stroke around every `Icon` anchor's socket — a real, working
fix, but the user tried it on-device and rejected it as heavy-handed once seen on every single icon
across a real timeline, asking for the *other* sanctioned nesting strategy this doc already lists
instead: tone/elevation separation, not a drawn boundary.

- **Outline removed entirely** — `AnchorDescriptor.hasOutline`, `TimelineTrackOverlay`'s
  `SOCKET_OUTLINE_WIDTH`/`socketOutlineColor` and the `Stroke(...)` draw calls in `drawSocket` are
  gone, back to a plain fill.
- **Interior anchors drop their semantic shape and accent entirely, not just gain a boundary.**
  Confirmed with the user: non-circular valence silhouettes (`Flower`/`Diamond`) and per-trigger
  `*Container` accents are now reserved for **cap anchors only** (segment top/bottom, a sleep sub-
  track's own onset/wake) — the "important, big" anchors. Every interior anchor is a plain circle
  filled with a neutral pulled from the **opposite extreme of the surface-container tone ladder**
  from whichever track it sits on: `colorScheme.surfaceContainerHighest` (lightest neutral) on the
  awake track, `colorScheme.surfaceContainerLowest` on the bold dark `primary` sleep track — using
  the ladder's two *named extremes*, not one step up/down, maximizes the guaranteed tone gap
  without needing to hand-verify every accent/track combination across every dynamic palette. The
  glyph tint also reverts to neutral (`onSurfaceVariant`) for interior anchors, since a colored
  icon on a now-neutral disc would read as an arbitrary mismatch. Implemented by normalizing the
  `Icon` anchor value once (`tint`/`containerColor` nulled, `valence` forced `Neutral`) at the top
  of `TimelineNode` when `!atCap`, so the existing fallbacks in `AnchorContent` and
  `socketAccentColor` do the rest without a separate `atCap` branch duplicated three times.
- **Spine made deliberately subtler in both directions**, via `lerp` toward each track's own color
  instead of a full-strength divider role: the awake spine is now `outlineVariant` blended 35% into
  the track color (was a flat `outlineVariant`); the asleep spine is `onPrimary` blended 40% into
  `primary` (was flat `onPrimary`, which read as a bold white stroke against the new dark track).
  `SPINE_GAP` (already 8dp as of Round 16) is unchanged this round.
- **Minor-row text grows and gains relative phrasing.** `timelineRowTitle` 14→16sp; a new
  `timelineRowTime` role (13sp, derived from `labelMono` rather than resizing the widely-shared
  `labelMonoSmall`) replaces it at both `EventNode`'s and non-latest `AiRunNode`'s trailing time.
  A new `timelineTimeLabel(epochMs, absoluteLabel)` helper shows "12m ago" (via the existing
  `rememberRelativeAgo`, already used for the hero kicker) for anything under a 60-minute cutoff,
  falling back to the plain clock label beyond it — the day header already disambiguates the date
  for anything older, so a multi-hour-old "3h 40m ago" wouldn't read better than "23:14" anyway.

Round 18 — interior sockets go fully invisible, a cap anchor's `*Container` fill gets a track-aware
escape hatch, and cap shapes get real breathing room. Also the round that finally removed the
per-day header block that this doc's Round 13/14 entries assumed was a legitimate track-segment
boundary — it wasn't; see the summary at the end of this entry.

- **Interior sockets now match the track exactly, not a contrasting neutral.** Round 17's
  opposite-tone-ladder-extreme fix (`surfaceContainerHighest`/`surfaceContainerLowest`) was itself a
  visible dot, just a higher-contrast one — the user wanted no visible disc at all, only the glyph
  floating on bare track. `TimelineNode.kt`'s `interiorNeutralColor(isAsleep)` now returns
  `trackColorFor(isAsleep)` directly (the same `primary`/`surfaceContainerHigh` values the track
  itself is painted with), so the carved socket disappears into its background. Because the disc no
  longer carries any contrast on its own, the glyph tint can no longer fall back to a flat
  `onSurfaceVariant` — that reads fine against the light awake track but disappears against the dark
  `primary` sleep track. The `effectiveAnchor` normalization now sets `tint` explicitly per track
  (`onPrimary` when asleep, `onSurfaceVariant` when awake) instead of nulling it.
- **Cap anchors get a track-aware escape hatch from the `*Container`-on-track clash.** The original
  Round 17 contrast bug (a `*Container` role is tonally close to a `surfaceContainer*` role by M3
  construction — that's the entire point of the "container" tier) turned out not to be fully fixed:
  it only ever applied to *interior* anchors. A cap anchor's raw `containerColor`
  (`TriggerVisuals.kt`'s `containerColor(Prominent)`, or `AiRunNode`'s flat `primaryContainer`) still
  painted straight onto the track, confirmed on-device with a `primaryContainer`-filled
  "good moment to wake" cap barely separating from the awake `surfaceContainerHigh` track. New
  `TimelineAccent.capContainerColor`/`capOnContainerColor` (`TriggerVisuals.kt`) resolve a Prominent
  cap's fill to the accent's *solid* role (`primary`/`secondary`/`tertiary` + matching `on*`) on the
  awake track — the opposite tonal extreme from any surface role by construction, a guarantee that
  holds across dynamic color rather than one palette's specific hex values — while leaving the dark
  `primary` sleep track on the plain `*Container` pairing, since a light Container tone already has
  ample gap there and a solid `primary` fill for the Schedule accent would collide outright with the
  sleep track's own `primary` fill. Applied uniformly across all three `TimelineAccent` values (not
  just the one the user happened to screenshot) and mirrored in `AiRunNode`'s own flat
  `primaryContainer` icon fill, which carries the identical structural risk. A detail chip
  (`MonoPill`, e.g. the snooze-duration badge) is explicitly *not* routed through this — it sits on
  the neutral page background, not the track, so the plain Container pairing is already correct
  there and swapping it in would have been an unrelated regression.
- **Cap shapes get more rim.** `ANCHOR_PADDING` (2dp) is renamed `CAP_ANCHOR_PADDING` and doubled to
  4dp (`Spacing.xs`), so `FLUSH_ANCHOR_SIZE` drops from 36dp to 32dp inside the same 40dp track — a
  non-circular cap silhouette (Flower/Diamond) reported as cramped/jagged at the old 2dp flush fit
  now has real breathing room. The stadium's own cap radius is unchanged (still `halfTrack`); only
  the anchor's content shrinks within it, so the concentric-cap math in `TimelineTrackOverlay.kt`
  needed no changes beyond the renamed constant.
- **The inline per-day header block is gone — a day boundary is no longer a track/segment break at
  all.** This turned out to be a real, confirmed bug, not just a visual seam: `TimelineMapper.kt`'s
  `timelineAsleepStates()` force-reset the asleep flag to `false` at every `DayHeader`, and
  `SessionTimeline.kt` incremented `segmentId`/flanked `isSegmentTop`/`isSegmentBottom` at every day
  boundary — meaning a sleep session spanning midnight (the overwhelming common case) got its asleep
  state reset and its track literally cut into two independently-capped segments exactly at
  midnight, and a genuinely mid-sleep event landing next to the boundary got wrongly forced into
  big-cap-shape treatment. `TimelineItem.DayHeader` is removed entirely (not just skipped) — the
  list is now one continuous segment end-to-end (`segmentId = 0`, cap only at the true list
  ends), and `timelineAsleepStates` has nothing to reset on. A new floating pill
  (`TimelineDayIndicator.kt`) replaces the removed header purely as a scroll-position reminder
  ("Today"/"Yesterday"/a weekday name) — it's a genuine overlay, never a list item, so it can't
  reintroduce the seam it replaces. This is why Round 13/14's "a DayHeader already marks a fresh
  track segment" framing is now wrong, not just superseded — that framing was the bug.

Round 19 — the floating day pill was badly positioned, a track-aware Container fallback still
collided on one shipped palette, and "invisible" interior sockets read as boring. Three corrections
after live-usage feedback on Round 18's build.

- **The floating day-indicator pill is gone, replaced by a sticky *inline* row.** Round 18's overlay
  pill landed pinned in the top-right corner regardless of scroll position — not the "floats above
  the current section" the design intended, just a fixed badge. `TimelineItem.DayHeader` is
  reinstated as a real (if purely decorative) list item — `TimelineMapper.kt`'s `insertDayHeaders`
  and `capTimeline`'s header-skip logic are both back — but rendered via `LazyListScope.stickyHeader`
  instead of a plain `item`, so it pins to the top of the viewport while its section scrolls beneath
  it and yields to the next header, exactly the native "sticky section header" behavior. Restored the
  original big/heavy typography (`timelineDayHeader`/`timelineDayDate`, `ExpressiveUltraCondensedFontFamily`)
  Round 18 had deleted as part of the (now-reverted) compact-pill design, but right-aligned and with no
  track anchor of its own (unlike an `Event`/`AiRun` row) — it's a reminder floating over the content,
  not another node threaded onto the timeline. Round 18's core fix survives intact: `DayHeader` stays
  fully transparent to `timelineAsleepStates`, segment id, and cap derivation (now via
  `timeline.indexOfFirst/indexOfLast { it !is DayHeader }` rather than raw list-index math), so a
  sleep session spanning midnight still renders as one continuous, uncapped pill with the header
  merely floating over part of it — confirmed via a new screenshot regression test with a real
  `DayHeader` sitting inside the sleep stretch.
- **A Prominent cap's `*Container` fallback on the sleep track wasn't actually safe.** Round 18's
  `capContainerColor` kept the plain `*Container` pairing on the sleep track on the theory that a
  light Container tone has "ample gap" against the dark `primary` fill — an assumption that doesn't
  hold universally: M3's dark-scheme tonal spec can place a Container role *darker* than its base
  role (the inverse of light scheme), and this app's own non-dynamic dark fallback
  (`Color.kt`'s `FallbackDarkColors`) sets `primaryContainer` to the exact same value as `primary` —
  zero contrast, confirmed by reading the source, not just inferred. The fix now applies the solid
  role (`primary`/`secondary`/`tertiary`) uniformly on **both** tracks for every accent — Container is
  no safer a bet on the sleep track than the awake one — except the one collision no track-side branch
  can dodge: [TimelineAccent.Schedule]'s solid role *is* `primary`, and the sleep track *is* a flat
  `primary` fill. That single case now falls back to `colorScheme.inverseSurface`/`inverseOnSurface` —
  an M3 role built specifically to read clearly against whatever surrounds it, independent of the
  primary/secondary/tertiary palette entirely. `AiRunNode`'s own flat `primaryContainer` icon fill
  (always Schedule-hued) gets the identical `inverseSurface` fallback on the sleep track.
- **Interior anchors trade "invisible" for a high-contrast pill.** Round 18 made an interior anchor's
  socket disc match its track exactly, so only the glyph read — the user found this "lame," not
  boring-in-a-good-way, and asked for a visibly-present pill-shaped badge instead. Every interior
  (non-cap) anchor now carves a new `AnchorShape.Pill` — a horizontal capsule the same width as a cap
  anchor's own footprint (`FLUSH_ANCHOR_SIZE`) but only as tall as the interior content diameter
  (`INTERIOR_ANCHOR_SIZE`), rounded left/right and flat top/bottom (a wide `RoundRect` with corner
  radius equal to half its height — the standard capsule recipe). Its fill/glyph pair is the same
  `inverseSurface`/`inverseOnSurface` role used for the one cap-collision case above, applied
  *uniformly* regardless of track — deliberately sidestepping the per-track ladder-rung tuning that
  Rounds 17 and 18 each got wrong in one direction or the other, since `inverseSurface` is guaranteed
  by M3 spec to invert lightness against whatever's active, not tuned per-surface by hand.

Round 20 — interior pills swap to track-crossed roles, the sticky header gets real geometry fixes,
and a fade-in animation stopped replaying on every scroll.

- **Interior pill fill swaps to a track-crossed pair, not a fixed `inverseSurface`.** Round 19's
  uniform `inverseSurface`/`inverseOnSurface` fixed the contrast problem but read as flat/uninteresting
  once seen live. `TimelineNode.kt`'s `interiorPillColor(isAsleep)` now deliberately *crosses* the two
  tracks instead of using one color for both: on the sleep track the pill matches
  `CronColors.pageBackground` (a low-elevation neutral — the pill reads as a hole punched through to
  the page), and on the awake track it borrows the sleep track's own bold fill
  (`trackColorFor(isAsleep = true)`, i.e. `primary`) — the boldest color in the app, popping clearly
  off the lighter awake track. `interiorPillOnColor` pairs `onSurface` with the page-background case
  and `onPrimary` (the M3-guaranteed pairing) with the borrowed-sleep-track case.
- **The sticky day header was colliding with `HomeContent.kt`'s hand-rolled `StickyAlarm` overlay —
  a real, confirmed-live bug a static Roborazzi screenshot can't catch on its own.** `stickyHeader`
  pins at the LazyColumn's own content-padding top, which sits *behind* the separately-pinned
  collapsed alarm bar (`StickyAlarm`, positioned via `graphicsLayer.translationY`, independent of the
  LazyColumn's scroll state) — so a header trying to stick renders directly underneath the alarm and
  is fully covered by it. Verified two ways this round: (1) a new scroll-driven Roborazzi test
  (`day_header_pins_to_the_top_while_its_section_scrolls_beneath_it`) that drives a real `LazyColumn`
  + `LazyListState.scrollToItem`, confirming `stickyHeader` itself *does* pin correctly — the bug was
  purely the alarm-overlay collision, not broken pinning; (2) live on-device feedback, which caught
  an over-correction this round's first pass made (see below). Fix: `sessionTimelineItems` now takes
  the owning `LazyListState`, and each `DayHeader` derives `isPinned` (whether it's the first entry
  in `listState.layoutInfo.visibleItemsInfo`) to conditionally add `ALARM_BAR_HEIGHT + Spacing.xxxl`
  of extra top clearance — enough to clear the collapsed bar and its fade-out gradient tail — but
  *only* while genuinely pinned. An unconditional version of this same clearance (applied to every
  header regardless of position) shipped briefly within this round and was rejected on-device for
  making every ordinary, non-pinned header needlessly oversized.
- **The header's opaque backdrop was blocking the track underneath it entirely, in both pinned and
  unpinned states** — a full-row-width `Modifier.background` covered the vertical track/spine on the
  left, which should always stay visible since the header carries no anchor of its own. Fixed by
  reserving `NODE_GUTTER` (the same gutter width every anchor row uses) as fully transparent space
  before the opaque backdrop begins, so the track always shows through on the left, pinned or not.
- **A newly-arrived latest row's delayed fade-in (Round 18) was replaying every time the row
  scrolled off-screen and back into view**, not just on genuine insertion. The `Animatable`/delay
  driving it lived in a per-row `remember(item.id)`, which resets whenever `TimelineNode`'s
  `DisposableEffect` disposes the row (LazyColumn recycles rows scrolled far enough away) and it
  later recomposes fresh. Fixed by tracking "has this id's entrance already played" in
  `TimelineTrackRegistry.markEnteredOnce` — a plain `mutableSetOf` that, unlike a per-row `remember`,
  survives the row's own dispose/recompose cycle for the registry's (i.e. the whole screen's)
  lifetime, so the animation is gated to play at most once per row identity.

Round 21 — a non-cap `Latest` anchor stopped borrowing the cap's morph shape, the sleep track moved
off `primary` entirely, the awake track became an outline instead of a fill, and the sticky header
was rebuilt as a `StickyAlarm`-style overlay.

- **`TimelineAnchor.Latest` rendered its Cookie9Sided morph even when it wasn't a segment cap.**
  `item.isLatest` (marks the chronologically-newest `AiRun`) doesn't imply `isSegmentTop`: an `Event`
  timestamped more recently than the latest AI run (an alarm dismissed the next morning, after the
  AI's plan ran the night before) legitimately sorts above it in the reverse-chronological list,
  making the "latest" run a structurally interior row despite the name. `TimelineNode.kt`'s
  `effectiveAnchor` normalization now converts a non-cap `Latest` into a plain interior `Icon`,
  reusing the exact same Pill-shape/track-crossed-color path every other interior anchor already
  takes — "only cap anchors get custom shapes" now holds for the morph too, not just the valence
  polygons.
- **The sleep track moved from `colorScheme.primary` to `colorScheme.secondary`**, freeing the
  primary palette for the AI-run family (which already used `primary`/`primaryContainer` throughout)
  to stop colliding with the track it sits on. `SessionTimeline.kt`'s `trackColorFor` and
  `TimelineTrackOverlay.kt`'s spine blend both moved together; `AiRunNode`'s cap-color branch could
  drop its `useInverseCapColor`/`useSolidCapColor` split entirely, since the solid `primary` role no
  longer collides with anything on either track.
- **The awake track switched from a filled `surfaceContainerHigh` stadium to a thin outline stroke**
  — a flat fill at that tone read as barely visible against the page background, and the fix reaches
  for the same treatment M3 Expressive's outlined buttons use (a 1dp `colorScheme.outline` stroke)
  rather than raising the fill's own contrast, which would have fought the "quiet unless something
  happened" read the awake track is meant to have.
- **The sticky day header was rebuilt as a `StickyAlarm`-style overlay** to fix `LazyListScope.stickyHeader`'s
  layout-shift/late-transition problems (superseded by Round 22 below — kept here for the record of
  what was tried and why it still wasn't right).

Round 22 — the outline/spine got quieter still, the sticky-header overlay was reverted for being
its own source of glitches, a second Prominent accent was found colliding with the sleep track, and
the timeline's left edge was aligned to the alarm card's.

- **The awake track's outline and spine still read as too contrasty even after Round 21.**
  `colorScheme.outline` is M3's role for a boundary that needs to read *clearly* — exactly the
  opposite of what was wanted here, a track that sits quietly behind the content. `trackColorFor`
  now uses `colorScheme.outlineVariant` instead, M3's own "quiet divider" role. The spine (the
  center-line detail) needed to sit at an even lower visual weight than the outline it runs inside
  of, so `TimelineTrackOverlay.kt`'s `awakeSpineColor` now blends the outline color *into*
  `CronColors.pageBackground` (mostly background, a hint of the outline's own hue) rather than
  blending the outline color toward a bolder neutral — the direction that increases rather than
  decreases contrast.
- **A second Prominent accent was confirmed colliding with the sleep track on-device**: an
  `OutOfBedConfirmed` cap (`TimelineAccent.Body`, solid `tertiary`, rendered as a `Flower` for its
  Positive valence) landed at a near-identical tone to the sleep track's own `secondary` fill —
  the same "different palette, same elevation" failure Round 21 had only fixed for
  `TimelineAccent.AlarmAction` (whose solid role is the literal same color, `secondary`, as the
  sleep track). The root cause generalizes: once the sleep track itself became a *solid* role
  instead of a neutral surface tone, no other solid role is guaranteed distinct from it by M3
  construction — solid-vs-surface is a structural guarantee, solid-vs-solid isn't.

  The first fix generalized every Prominent cap on the sleep track to
  `colorScheme.inverseSurface`/`inverseOnSurface` — which turned out to be the wrong escape hatch,
  caught by another live report showing the exact same collision one level deeper.
  `inverseSurface` only guarantees it differs from *this theme's own* `surface` by mirroring the
  opposite theme's `surface`; it has no guaranteed relationship to `secondary`. This app's `Color.kt`
  fallback palette makes the collision concrete: `LightSecondary` (`0xFF3D6655`) is deliberately dark
  (the sleep track is meant to read bold/dark in *both* themes, Round 16), and light theme's
  `inverseSurface` mirrors dark theme's `surface` — also dark. Two independently-dark colors in
  different hue families, the same bug, one abstraction layer removed. The real fix uses
  `colorScheme.surfaceContainerHighest`/`onSurface` instead — a genuine surface-*family* role, the
  one the solid-vs-surface guarantee actually covers, verified against this app's own fallback
  palette to sit at the opposite tonal extreme from `secondary` in both themes
  (`TriggerVisuals.kt`'s `capContainerColor`/`capOnContainerColor`, and the mirrored
  `atCap`/`isAsleepAbove` branch in `SessionTimeline.kt`'s `AiRunNode`). Verified via two new
  isolated Roborazzi captures (`cap_gallery_sleep_track_light`/`_dark`) — the combined
  `EventCapGalleryPreview`'s sleep-track section renders below the fold in a fixed-height
  Robolectric window, so the existing `event_cap_gallery_*` tests never actually exercised it.
- **The Round 21 sticky-header overlay was reverted entirely.** It fixed `stickyHeader`'s layout-shift
  problem but introduced its own: on-device testing showed the pin threshold triggering visibly late
  and the handoff between the in-flow row and the overlay copy reading as glitchy rather than smooth
  — more moving parts (a second `DayHeaderRow` copy, a live pin-target computation off `StickyAlarm`'s
  collapse state, an alpha-hiding threshold on the in-flow row) than the payoff justified. `DayHeader.kt`
  and `SessionTimeline.kt` are back to a single plain in-flow row with no pin behavior at all.
  In its place: `DayHeaderLabel` gained an `isActive` flag that de-emphasizes every header except the
  one governing whichever section is currently scrolled into view — computed inline off
  `listState.layoutInfo` (the last day header whose live offset has crossed above the viewport top,
  or the first one visible if none has yet). Since `CronTypography.timelineDayHeader` is already at
  `FontWeight.Black` — the top of the weight scale — there's no heavier weight to step the *active*
  header up to; instead the *inactive* state steps down to `FontWeight.SemiBold` plus
  `onSurfaceVariant`, leaving the active header relatively bolder by contrast, with the color
  transition (not the discrete weight swap) carrying the actual animation via
  `animateColorAsState(label = "day-header-active-emphasis")`.
- **The timeline's content padding moved from `Spacing.xl` to `Spacing.md`** so its left edge
  (day headers, anchor gutter) lines up with `StickyAlarm`'s own horizontal inset — previously the
  timeline sat visibly offset to the right of the alarm card above it.

Round 23 — the alarm-card fade overlay no longer fully hides content, the awake-track outline was
reverted back to a fill (again — see Round 21/22), a plan row's press feedback moved from a whole-row
scale into a genuine shape morph, and the day header's active/inactive transition became a real
continuous variable-font animation instead of a discrete weight swap.

- **`StickyAlarm`'s fade overlay (`HomeContent.kt`) used a fully-opaque `pageBackground` before
  fading to transparent**, completely hiding any timeline content scrolling up underneath the alarm
  card until it cleared the fade band — including the exact moment Round 22's gutter-alignment fix
  becomes visible. In light theme this read as content abruptly vanishing rather than fading. Fix:
  cap the overlay's own max opacity at `MAX_LIGHT_FADE_OVERLAY_ALPHA = 0.5f` (light theme only —
  dark theme's fade already read fine at full opacity), so content stays faintly visible through it
  at every point in the scroll.
- **The awake track's outline-stroke treatment (Round 21, retuned in Round 22) was reverted
  entirely back to a fill** — not a further tuning pass, a full reversal: the user didn't want an
  outline at all, regardless of how subtle. `trackColorFor`'s awake branch is back to
  `colorScheme.surfaceContainerHigh` (a plain fill, same role Round 20 tried and found "barely
  visible" — but this time the fill isn't expected to carry all the legibility alone). The spine
  blend flipped direction to compensate: instead of blending the (now-gone) outline color toward the
  page background, `TimelineTrackOverlay.kt`'s `awakeSpineColor` now blends the fill color toward
  `colorScheme.onSurfaceVariant` — the M3 role calibrated to read on top of surface-family tones —
  so the center line carries the visibility burden the fill isn't expected to.
- **A plan row's press feedback moved from a whole-row `graphicsLayer { scaleX; scaleY }` shrink to
  a shape morph on the anchor itself.** The scale transform only ever touched the foreground
  `Surface` — never the anchor socket, which `TimelineTrackOverlay` paints independently at the
  row's registered position — so a press visually read as the row content sliding sideways out from
  under a static anchor. Fix: `AnchorShape.Pill` gained a `pressProgress: () -> Float` that
  interpolates its corner radius from a full capsule (unpressed) down to `35%` of that radius
  (pressed) — a `ToggleButton`-style rounder↔squarer morph (this repo's own Expressive guidance
  already names `ToggleButton`'s shape morph as the pattern to reach for). A clickable cap anchor's
  plain `Circle` gets the equivalent treatment via the existing `AnchorShape.MorphShape` mechanism —
  a new `Morph(Circle, Cookie6Sided)` (a different point count from `Latest`'s own `Cookie9Sided`
  arrival morph, so the two "selected" cues stay visually distinguishable) that renders
  pixel-identical to a plain circle at `pressProgress == 0`, so it's used unconditionally for every
  clickable Neutral cap rather than swapping shape types on press. `pressProgress`/`pressMorphProgress`
  are hoisted above `TimelineNode`'s shape derivation and computed unconditionally (harmless when
  `onClick` is null — `pressed` just never turns true without a `Surface.onClick` feeding events).
- **`SessionTimeline.kt` crossed the 500-line file cap** once the press-morph plumbing landed —
  `EventNode` (a fully self-contained "render one Event row" responsibility, ~80 lines) moved to its
  own `EventNode.kt`, taking `SessionTimeline.kt` back to 427 lines. `timelineTimeLabel` (shared by
  both `EventNode` and `AiRunNode`) stayed in `SessionTimeline.kt`, promoted from `private` to
  `internal` so the split file can still call it.
- **`DayHeaderLabel`'s active/inactive transition became a genuine continuous variable-font
  animation.** Round 22's binary `FontWeight.Black`/`SemiBold` swap read as "brutal" — a discrete
  jump, not a transition. Roboto Flex (the same variable font file
  `fr.bsodium.cron.ui.theme.CountdownFontFamily` already draws on) exposes both `wght` and `wdth`
  axes, so both can now animate in real time via `MaterialTheme.motionScheme.defaultSpatialSpec()`
  driving two `animateFloatAsState` calls (weight 600→900, width 30→42), with a new
  `rememberVariableWeekdayFont(weight, width)` building a fresh `FontFamily`/`FontVariation.Settings`
  pinned to the (rounded, for cache-key stability) current axis values every frame the animation is
  in flight — a real "resize," not a color/alpha fade, hence spatial specs rather than effects specs
  per this repo's motion rules. This is the first live/per-frame `FontVariation` animation in the
  codebase; existing uses (`CountdownFontFamily`, `expressiveWidth`) all pin fixed values at
  composition time.
- **`DayHeaderRow`'s top padding trimmed from `Spacing.xxl` to `Spacing.xl`** — a modest reduction,
  not a redesign.

Round 24 — the alarm-card fade and the app-wide status-bar scrim were fighting each other, and the
Round 23 press-morph target changed from a squarer pill to a full circle.

- **Two independent top-of-screen gradients were both active at once on Home, producing a visible
  "fade, un-fade, fade again" as content scrolled through the gap between them.** `EdgeFades`
  (`MainActivity.kt`) draws a generic status-bar scrim on every route without its own `PageAppBar`,
  entirely independent of `StickyAlarm`'s own collapse-driven fade (`HomeContent.kt`) — the two were
  never coordinated, each fading/un-fading content on its own schedule as it scrolled through their
  respective (different-height) bands. Fix: `EdgeFades`' top scrim is now suppressed specifically for
  `ROUTE_HOME` — `StickyAlarm`'s own gradient already covers the same y-range (it starts at `y=0`,
  same as `EdgeFades`'), so Home owns 100% of its own top-of-screen occlusion with nothing else
  layered on top.
- **`StickyAlarm`'s Round 23 fade cap (50% max in light theme) now ramps to fully opaque as the card
  settles into its collapsed state**, rather than staying capped at 50% indefinitely. The 50% cap
  was specifically meant to keep the *collapse transition* watchable (seeing the timeline slide into
  alignment with the card's edge); once the card is genuinely done collapsing there's no more
  transition to see through, and a permanently-partial occlusion just reads as visual noise —
  especially now that it's the *only* top-of-screen gradient (previous bullet). The overlay's alpha
  cap is `lerp(MAX_LIGHT_FADE_OVERLAY_ALPHA, 1f, collapse.value.fraction)` — soft during the active
  collapse, solid once settled.
- **The Round 23 press-morph's `AnchorShape.Pill` target shape changed from a squarer corner radius
  to a full circle**, per direct feedback that a circle "would make more sense" as the selected
  state. Rather than reintroducing a separate corner-radius-fraction constant, the pill's *height*
  now grows to meet its own fixed width as `pressProgress` goes 0→1, with the corner radius pinned
  at `height / 2` throughout — at full press, width == height with a fully-round corner, which is
  just a circle, and specifically one at the same flush diameter a cap anchor's own `Circle` uses.

Round 25 — the press morph got bouncier and a real Cookie4Sided target, the two-gradient timing/
opacity got tuned rather than just toggled, the sleep track's spine went wavy, and the day header
was redesigned around a big date number.

- **The press-morph animation switched from `fastSpatialSpec()` to `defaultSpatialSpec()`** — "fast"
  read as an ease with no real spring, and a press cue is exactly the kind of moment M3 Expressive
  wants to feel bouncy.
- **An interior `Pill`'s press target changed from a plain circle to `MaterialShapes.Cookie4Sided`.**
  Since `Pill` wasn't previously backed by a `RoundedPolygon` at all (it draws via a raw
  `drawRoundRect`), this needed a real capsule endpoint shape —
  `RoundedPolygon.pill(width = PILL_ASPECT, height = 1f)` at a normalized aspect ratio, morphed
  toward `Cookie4Sided`. Scaling a non-square `Morph` output into a non-square target needed a new
  sibling to `buildMorphPath`: `buildMorphPathFitRect` scales width/height independently instead of
  uniformly into a single diameter, so the target shape only reads undistorted once `pressProgress`
  has grown the pill's height to match its width (i.e. right at full press, exactly when it matters).
  `AnchorShape.Pill` gained a `morph: Morph?` field (`null` — the default — keeps every
  non-interactive `EventNode` pill on the original crisp `drawRoundRect`, since only a clickable
  `AiRunNode` row needs the morph machinery at all).
- **Two follow-ups to Round 24's single-gradient fix, both from live testing on a real device.**
  First: `EdgeFades`' top scrim was suppressed on Home, but `StickyAlarm`'s own `gradientAlpha` was
  still keyed to the *alarm card's* collapse distance — meaning the greeting had already scrolled
  fully under the status bar, unoccluded, well before that distance turned positive. Fix:
  `gradientAlpha` is now keyed to the *greeting row's own* scroll offset instead, so occlusion
  engages as soon as scrolling starts, independent of how far the card itself is from collapsing.
  Second: Round 24's fraction-based ramp to a fully-opaque (1f) settled state made the
  `belowFadePx` tail's transition into visible timeline content too harsh — content that used to
  visibly "transpire through" near the card's bottom edge now hit a hard cliff. Fix: the settled cap
  is `MAX_LIGHT_FADE_OVERLAY_SETTLED_ALPHA = 0.85f`, not `1f` — solid enough to read as occluding,
  short enough that the tail's transparent tail still stays gentle.
- **The sleep track's center-line spine is now a hand-rolled sine wave; the awake track's stays
  straight.** No off-the-shelf "wavy line" primitive was reused (M3 Expressive's wavy progress
  indicators are full composables, not an exposed path utility) — `drawWavyVerticalLine` walks
  short `lineTo` segments along a sine curve (`SPINE_WAVE_AMPLITUDE = 3dp`,
  `SPINE_WAVE_LENGTH = 16dp`, phase anchored to each gap-free segment's own start so it reads as one
  continuous wave across anchor gaps within the same pill, not a reset-per-anchor zigzag). `drawSpine`
  gained a `wavy: Boolean` switch so the identical gap-skipping logic can emit either primitive.
- **The day header was redesigned from a relative-day wordmark to a calendar-tear-off pairing** — a
  colossal bold day-of-month digit (`timelineDayNumber`, 56sp, up from the wordmark's 28sp) with the
  month abbreviation as a small thin caption underneath (`timelineDayMonth`, 14sp), both
  right-aligned, closed off by a thin `HorizontalDivider` sized to the block's own width (not the
  full row — "an underline under the month text," deliberately not touching the timeline gutter on
  the left). Replaces `timelineDayHeader`/`timelineDayDate` entirely, which drops the "TODAY"/
  "YESTERDAY"/weekday-name relative label along with them — `relativeDayLabel` (`TimelineMapper.kt`)
  is now dead code and was removed. The existing bouncy variable-font `wght`/`wdth` animation
  (Round 23) carries over unchanged, now driving the big number instead of the wordmark.
- **`TimelineTrackOverlay.kt` crossed the 500-line file cap** once the wavy-spine and
  `buildMorphPathFitRect` code landed — `buildMorphPath`/`buildMorphPathFitRect`/`buildPolygonPath`
  (a fully self-contained "build a Compose Path from a graphics-shapes object, scaled to fit"
  responsibility, not used outside this file) moved to a new `ShapePathBuilders.kt`.

Round 26 — two Round 25 items got redone rather than tuned after live feedback ("the wavy line
looks terrible" / "doesn't sell the animation"), plus four smaller day-header adjustments.

- **The wavy sleep-track spine was rebuilt on the correct technique, and a real overlap bug got
  fixed.** Two separate problems, confirmed together on-device: (1) the wave itself was built from
  short straight `lineTo` segments, which reads as faceted/jagged rather than a smooth curve — M3
  Expressive's own wavy progress indicators (`LinearProgressDrawingCache` in
  `androidx.compose.material3.internal`, a `private class` and not directly reusable) build their
  wave from one **quadratic Bézier curve per half-wavelength** with an alternating control-point
  sign instead, which is what actually produces the smooth signature curve — `drawWavyVerticalLine`
  now replicates that exact technique. (2) A genuine layering bug: the straight awake spine was
  drawn across the *entire* segment extent first, on the assumption the wavy asleep spine would
  fully overpaint its own sub-range the same way the solid background/pill *fills* do — but a wavy
  line oscillates around `trackCenterX` while the straight line sits fixed on it, so the straight
  line visibly peeked out from behind the wave at every peak and trough ("I see BOTH the wavy line
  and a straight line"). Fixed with a new `awakeSpineRanges` helper that computes the actual
  complement of the pill ranges and only draws the straight spine there — no more reliance on
  overpaint for the spine specifically.
- **The interior Pill's press-morph target changed again, from `Cookie4Sided` to a sideways arrow**
  — Round 25's cookie shape looked fine as a static shape but "didn't sell the animation" as a
  press/selection cue. `MaterialShapes.Arrow` points up by default (confirmed by rendering it, not
  by assumption — an initial attempt to verify this with a bounding-box aspect-ratio check gave a
  false reading, since the shape's wingspan is wider than its tip length even before rotation; a
  dedicated `PillPressMorphScreenshotTest` renders the real production shape at fixed press values
  instead and settles it visually). Rotated 90° via `RoundedPolygon.transformed { x, y ->
  TransformResult(-y, x) }` — the same *public* mechanism M3's own `MaterialShapes` library uses
  internally to build shapes like `Oval` by rotating a scaled circle, not the icon-direction-faking
  CLAUDE.md's rule targets (that rule is specifically about `MaterialSymbol` glyphs, where flipping
  can break an asymmetric icon's own design — this is a background shape morph). The press-morph
  spec also stepped up from `defaultSpatialSpec` to `slowSpatialSpec`, the same tier the Latest
  anchor's own arrival morph uses, to sell it as a real flourish rather than a quick snap.
- **The day-header number gained a vertical squash** (`graphicsLayer { scaleY = 0.8f }`, the same
  paint-time-only technique `CollapsibleAlarmCard`'s clock digits already use) paired with a
  tightened `lineHeight` so the row's own layout height keeps pace instead of leaving dead space
  under the now visually-shorter glyph.
- **The month caption grew and thickened, and now writes the full name** ("JUNE", not "JUN") —
  `timelineDayMonth` moved off the thin/italic pairing entirely (14sp Light → 18sp SemiBold, and off
  `ExpressiveCondensedThinFontFamily` — which only ever registers one fixed Light face regardless of
  requested weight — onto `ExpressiveCondensedFontFamily`, which actually has a SemiBold/Bold face
  to request).
- **The relative-day label ("TODAY"/"TOMORROW"/"TWO DAYS AGO"/weekday name), dropped in the Round 25
  redesign, came back** — left-aligned on the same line as the month, opposite it, "a bit bolder"
  (`timelineDayActiveLabel` = `timelineDayMonth.copy(fontWeight = Bold)`, sharing its exact size).
- **`DayHeaderRow` now explicitly reserves `NODE_GUTTER`** the way every anchor row already does.
  Without it, the header's own content — and specifically its divider, sized to the block's own
  width — had nothing stopping it from extending far enough left to visually overlap the timeline
  track itself for a wide (2-digit, colossal-font) day number ("the divider... currently does"
  overlap, confirmed). The label now sits in a `Modifier.weight(1f)` slot after that gutter Spacer,
  so it's confined to the same content column every other row already respects.

Round 27 — the wavy spine is fully abandoned after live testing ("looked better on paper, but it
turned out catastrophic"), every anchor's accent color converges on one track-matched rule, and
three smaller day-header/press-morph items.

- **The wavy sleep-track spine is gone, permanently.** Round 26's quadratic-Bézier rebuild fixed the
  *technique* but the result still read badly on-device, and the user explicitly asked to stop
  iterating on it rather than try a third approach — `drawWavyVerticalLine`, `awakeSpineRanges`,
  `drawSpineSegment`, and the wave-amplitude/wavelength constants are deleted outright. Both tracks
  draw a single straight `drawLine` per gap-free sub-range again — the full awake spine across the
  segment's extent, then each sleep pill's own line overpainting its sub-range, the design from
  before Round 25 ever touched this file. This also resolved a reported "hugging" artifact at
  sleep-track boundaries as a side effect: `awakeSpineRanges` had hard-clipped the straight spine's
  draw range exactly at each pill's edge (needed only for the wave), and clipping there rather than
  overpainting is what was pulling the line in tight.
- **Every anchor's fill color now matches whichever track it sits on — one rule, not six rounds of
  patches.** New `trackAccentColor`/`trackOnAccentColor` functions (`SessionTimeline.kt`) return
  `secondary`/`onSecondary` on the sleep track and `primary`/`onPrimary` on the awake track, and
  every socket fill in the timeline — cap icons (`TriggerVisuals.kt`'s `capContainerColor`/
  `capOnContainerColor`), interior pills (`TimelineNode.kt`, replacing the Round 19/20
  `interiorPillColor`/`interiorPillOnColor` "track-crossed swap"), the Loader spinner, and the
  Latest hero anchor (`LatestAnchor.kt`, previously a flat `primaryContainer` regardless of track)
  — now derive from it instead of each carrying its own per-accent-hue or escape-hatch logic
  (`inverseSurface`, `surfaceContainerHighest`, per-`TimelineAccent` branching). Confirmed with the
  user this uses the *literal* solid track color, not a lighter Container shade — on the sleep
  track specifically this means a Prominent cap's fill is now identical to the track's own fill, so
  the shape itself blends away and only its glyph reads (the same "hole" tradeoff Round 20 already
  accepted for interior pills, now consistent everywhere rather than swapped per anchor type). The
  `MonoPill` detail chip (e.g. "9 min" snooze duration) is unaffected — it sits on the page
  background, not the track, so it was never part of this collision class.
- **Today's day header is skipped entirely** (`TimelineMapper.kt`'s `insertDayHeaders`) — the user
  already knows it's today, so the header was pure redundancy at the top of the list.
- **Day-header spacing tightened**: top padding down to `Spacing.md`, the big number's
  `graphicsLayer` squash now anchors at `TransformOrigin(0.5f, 1f)` (bottom, not center) so tightening
  `timelineDayNumber`'s `lineHeight` (46sp → 40sp) directly shortens the gap to the row below instead
  of fighting a center-anchored transform, and the block's left inset grew from `NODE_GUTTER` alone
  to `NODE_GUTTER + Spacing.md` so the relative-day label lines up with every other row's own text
  indent (`titleSpacer` in `TimelineNode.kt`).
- **The relative-day label reverses Round 26's "a bit bolder" call** — `timelineDayActiveLabel` now
  reuses `ExpressiveCondensedThinFontFamily` with `FontWeight.Light` and `FontStyle.Italic` instead
  of a bold `timelineDayMonth` copy, on the same request as the spacing/indent fixes.
- **The interior Pill's press-morph target changed again, from a sideways arrow back to
  `MaterialShapes.Square`** — Round 26's arrow "didn't sell the animation" any better than Round
  25's `Cookie4Sided` did on live device testing. `Square` is a symmetric rounded-square silhouette
  with no rotation to get wrong, sidestepping the whole class of "does this shape actually point
  which way I think" verification problem Round 26 ran into. The press spec stays
  `slowSpatialSpec` (unchanged) — its `dampingRatio = 0.8` is already genuinely underdamped, i.e.
  it already overshoots before settling, which is the "bouncy" behavior asked for; no bespoke spring
  was added per this repo's motion rule.

Round 27.9 — a same-day live-device follow-up: the sleep-track color unification above used the
*literal* `secondary` role for both the track's own fill and every anchor sitting on it, which
[trackColorFor]'s own KDoc had already flagged as a deliberate choice — but on a real device every
sleep-track anchor (`OutOfBedConfirmed`, `AlarmDismissed`, etc.) genuinely disappeared into the pill,
leaving only a bare glyph with no shape around it ("same colour as the sleep track itself... needs
tweaking"). `trackAccentColor`/`trackOnAccentColor`'s asleep branch moved to `secondaryContainer`/
`onSecondaryContainer` — still the same secondary family (so the original ask, "same palette as the
sleep track," still holds), but a genuinely different token, so it contrasts against the track's own
`secondary` fill instead of matching it pixel-for-pixel. The awake branch is untouched: `primary`
already contrasts fine against the awake track's neutral `surfaceContainerHigh` fill, since those two
were never the same role to begin with — the collision was specific to the asleep side literally
reusing the track's own fill token for its anchors.

Round 27.11 — five smaller items from a live-device pass, none touching the color-unification logic
above except one direct follow-up to 27.9:

- **The interior Pill's press-morph "butterfly" glitch is fixed by dropping `Morph` entirely for
  this case.** Rounds 24–27 each tried a different `Morph` press target (circle → `Cookie4Sided` →
  sideways arrow → `MaterialShapes.Square`), scaling the *mid-interpolation* outline non-uniformly to
  fit the growing rect (`buildMorphPathFitRect`) — morphing between two shapes with mismatched vertex
  topology (a capsule vs. a 4-cornered square) produces a genuinely bowtie-shaped in-between outline,
  and stretching that non-uniformly made it worse, reading as "some kind of butterfly shape." The
  fix: `AnchorShape.Pill` no longer carries a `Morph` at all — `TimelineTrackOverlay.kt`'s
  `drawSocket` just interpolates a plain `drawRoundRect`'s corner radius from a full capsule
  (`pillHeight / 2`) down to `SQUARE_CORNER_FRACTION` (`0.3`, matching `MaterialShapes.Square`'s own
  `CornerRounding(radius = 0.3f)` read from the M3 source) as press progress goes 0→1, the same
  lerp already driving the height grow. `buildMorphPathFitRect` (now unused) and the
  `pillPolygon`/`pressSquare` remembered values in `TimelineNode.kt` are deleted.
- **A demoted `AiRunNode` title (a row that was latest and got superseded) was floating at the top of
  its reserved height with dead space below.** `Crossfade` has no `contentAlignment` param in this
  project's compose-animation version, and its own internal `Box` defaults to `TopStart` — once the
  row demotes to its single-line state, that line rendered at the top of the still-`heightIn`-reserved
  hero-sized box instead of centered in it. Fixed by moving the `heightIn(min = heroMinHeight)`
  modifier onto a wrapping `Box(contentAlignment = Alignment.CenterStart)` around the `Crossfade`,
  rather than trying to pass it to `Crossfade` directly. Covered by a new
  `TimelineNodeScreenshotTest` that actually flips `isLatest` true→false within one composition (a
  row that starts and stays demoted never reproduces this — `everLatest` only matters once a genuine
  transition has happened).
- **A cap anchor's fill is now identical across `TimelineImportance` tiers, not just across
  accents.** Follow-up to 27.9: even after that fix, `TimelineImportance.Muted` cap anchors
  (`SleepOnset`/"You fell asleep", etc.) still used a fixed, track-agnostic neutral
  (`containerColor(Muted)` = `surfaceContainerLow`), which — since M3's own surface tones are
  themselves derived from the primary seed — visibly read as an unrelated "primary" cast sitting
  next to the obviously-secondary Prominent caps on the same sleep track. A first attempt blended a
  minority of `trackAccentColor` into that neutral as a compromise (keeping Muted's own recede
  quality); live testing showed that still wasn't enough — the ask was for every anchor on a track to
  be *literally* the same color, full stop. `capContainerColor`/`capOnContainerColor` dropped their
  `TimelineImportance` parameter entirely and now just return `trackAccentColor`/`trackOnAccentColor`
  unconditionally; the Muted/Prominent distinction on a cap now lives entirely in icon and shape
  (still separately valence-driven), not color. `containerColor`/`onContainerColor` (the detail chip,
  off-track) keep their full per-accent/per-importance branching — unaffected.
- **The day-header divider is now a real Material 3 Expressive component, not a hand-rolled wave.**
  `LinearWavyProgressIndicator(progress = { 1f }, ...)` — a genuine public composable — replaces the
  plain `HorizontalDivider`. `amplitude` is forced to a constant `{ 1f }` (the default fades it to 0
  past ~95% progress, meant for an actually-completing bar, which would flatten this to a straight
  line at our permanent `progress = 1f`), and `waveSpeed = 0.dp` keeps the wave static — a second
  perpetually-running animation here would undercut the very next fix. Top padding also dropped
  `Spacing.md` → `Spacing.xs` (still read as too far from the row above on-device), and
  `relativeDayLabel` (`DayHeader.kt`) dropped its trailing `.uppercase(...)` — every branch was
  already naturally capitalized, just not shouty.
- **The day header's "you are here" active-day emphasis is removed entirely, not optimized.** Round
  22 added a `derivedStateOf` over `listState.layoutInfo`, re-evaluated on every scroll frame for
  every visible `DayHeader`, driving three separate `animate*AsState` calls (color, variable-font
  weight, variable-font width) per header. Reported as a genuine on-device performance cost with no
  cheaper way to preserve the same live "which section am I scrolled into" tracking, so it's gone:
  every header now renders at one fixed style, no per-frame scroll-position computation at all.
  `sessionTimelineItems` dropped its now-unused `listState` parameter as a result.

Round 28 — a live-device pass covering press-motion tuning, a layout-inset bug, a chip → subtext
redesign (with a cascading dead-code cleanup), and the Latest hero's own spacing/typography.

- **The interior pill's press-morph moved from `slowSpatialSpec` to `fastSpatialSpec`** ("faster and
  bouncier"). Checked against the actual token values
  (`androidx.compose.material3.tokens.ExpressiveMotionTokens`) rather than guessing: fast is
  `dampingRatio=0.6`/`stiffness=800` against slow's `0.8`/`200` — genuinely *more* underdamped (more
  overshoot) as well as quicker to settle, i.e. legitimately both faster and bouncier, not a tradeoff
  between the two. A stale comment claiming fast "read as an ease, not a spring" (the reason Round 25
  moved off it) didn't match these numbers and was removed rather than carried forward unverified.
- **`DayHeaderRow` was missing the `end = Spacing.md` inset every anchor row's own trailing content
  applies** before the screen edge — the day number/month sat closer to the edge than every row's own
  time label. Added to match.
- **The `AlarmSnoozed`/`CalendarChange` detail chip (the *only* two triggers `TimelineMapper.kt`'s
  `eventDetail` ever populated) is gone, replaced by a subtext line.** "Those chips are actually not
  very useful... we could just add them below as extra content" — a `MonoPill` reading "9 min" or
  "Added" had no room to say what happened; a subtext line can ("9 minutes added" / "Added event"),
  with the one fact that matters bolded via a plain `AnnotatedString` (`EventNode.kt`'s `emphasized`)
  rather than a markup language — `TimelineItem.Event` gained a `detailEmphasis: String?` naming the
  substring of `detail` to bold, computed once alongside `detail` itself in `TimelineMapper.kt`'s new
  `EventDetail(text, emphasis)` return type. This left `TriggerVisuals.kt`'s per-accent/per-importance
  `containerColor`/`onContainerColor` — the detail chip's *only* remaining caller — fully dead, which
  cascaded to `TimelineImportance`/`timelineImportance()` (no callers left once those two functions
  were gone either); all four are deleted rather than left as an orphaned, uncalled color system.
  `TimelineAccent`/`timelineAccent()` themselves are untouched — still called from `EventNode.kt` to
  reach `capContainerColor`/`capOnContainerColor`, even though (per Round 27) those two now ignore
  their `TimelineAccent` receiver entirely; that's pre-existing Round 27 residue, not something this
  round's edit newly orphaned, so it's left for a future pass rather than scope-creeping this one.
- **The Latest hero's kicker-to-headline and headline-to-subtext gaps are swapped.** "The big text...
  was closer to the subtext than to the text above" — previously the reverse (`Spacing.xxs` kicker
  gap vs. `Spacing.sm` subtext gap). A new shared `HERO_KICKER_GAP` (`Spacing.sm`) constant now drives
  the kicker gap, `heroMinHeight`'s reservation term, *and* the new `heroAnchorOffset` below, so the
  three can't drift out of sync; `TimelineNode.kt`'s generic subtext gap (used by every row's
  `content` slot, not just the hero's) tightened from `Spacing.sm` to `Spacing.xxs`.
- **The anchor glyph and trailing arrow now align with the headline, not the whole row.** `TimelineNode`
  gained a `heroAnchorOffset: Dp` param — zero for every ordinary row, and (while `anchor is
  TimelineAnchor.Latest`) the kicker's line height plus `HERO_KICKER_GAP` for the hero row, applied as
  a top-alignment `padding` on both the anchor gutter `Box` and the trailing `status` `Box`. It
  automatically turns back off the moment a row demotes, since `anchor` stops being
  `TimelineAnchor.Latest` at that exact point (see `AiRunNode`'s own `when` deriving `anchor`) — no
  separate flag needed to track "are we still in the hero state."
- **"Crank the rounding axis to the max" on the hero headline's numerals.** In this app's own
  vocabulary a *wider* Roboto Flex `wdth` setting is the "rounder" one (less condensing leaves round
  letterforms like O/D closer to their natural shape — see `ExpressiveCondensedThinFontFamily`'s
  KDoc) — `timelineHeroTimeNew`/`timelineHeroTimePrev` move off the hero title's own condensed
  85-width face onto a new `ExpressiveMaxWidthFontFamily`/`ExpressiveMaxWidthThinFontFamily` pinned at
  151, Roboto Flex's registered `wdth` ceiling per its own public `fvar` spec.
- **The trailing arrow is thicker/bolder** via `Symbol`'s own Material Symbols `weight` axis (bumped
  to 700) rather than just sizing the glyph up, which wouldn't itself thicken the stroke.

Round 29 — a same-day live-device follow-up on three Round 28 items that didn't hold up once actually
seen on a phone, plus one real bug the "too spaced out" report led to.

- **The hero headline's max-width font is reverted.** Live testing read the whole block as more
  spaced out with it than without, and the width axis was the one thing that had visibly changed —
  `timelineHeroTimeNew`/`timelineHeroTimePrev` go back to `timelineHeroTitle`'s own condensed 85-width
  face; `ExpressiveMaxWidthFontFamily`/`ExpressiveMaxWidthThinFontFamily` are deleted rather than left
  defined-but-unused.
- **The real bug behind "I need the title closer to the subtext": the subtext was never inside the
  `TightTextStyle` `CompositionLocalProvider`.** `TimelineNode`'s title/status Row strips Android's
  default `includeFontPadding` leading; the `content` (subtext) Box rendered as a **sibling** below
  that provider's closing brace, not inside it — so the subtext's own invisible font padding stacked
  on top of the real `Spacing.xxs` gap, and no amount of shrinking that literal padding value would
  have closed the visual gap. Moving `content`'s `Box` inside the same `CompositionLocalProvider`
  fixed it directly, rather than continuing to tune a padding constant that was never the actual cause.
- **The anchor-glyph/arrow alignment hack is replaced with real Compose alignment lines.** Round 28's
  `heroAnchorOffset` — a padding value hand-computed from the kicker's own `lineHeight` — "didn't work
  at all" live. Replaced with a proper `HorizontalAlignmentLine` (`HeroHeadlineCenter`,
  `TimelineNode.kt`): the headline itself declares its own vertical center via a small
  `Modifier.declareCenterAs(line)` helper (a `Modifier.layout {}` reporting `placeable.height / 2` as
  the named line's value), and the anchor gutter `Box`/trailing arrow `Box` read it back via
  `RowScope.alignBy`, either the named-line overload (reads the headline's declared value, bubbled up
  through the title's Column automatically) or the lambda overload (`{ measured -> measured.measuredHeight
  / 2 }`, each box's own center) — Compose measures the real position instead of a hand-computed guess.
  A custom line (not the built-in `FirstBaseline`/`LastBaseline`) avoids ambiguity from the kicker
  caption *also* being a `Text` a couple of layers up in the same Column — only the headline ever
  declares `HeroHeadlineCenter`, so there's never a second candidate value to merge.
- **`EventNode`'s subtext (the Round 28 chip replacement) moves off `contentColor`
  (`onSurfaceVariant`, the same color as the row's own title/time) onto `colorScheme.outline`** — "the
  subtext should probably be less preeminent," and sharing a color with the title/time competed with
  them for the same attention rather than reading as secondary detail underneath.
- **The `CalendarChange` subtext no longer interpolates the raw backend `changeType` string.**
  `changeType` is an internal identifier (`CalendarChangeWorker.kt` only ever emits
  `"first_event_changed"`), not human-readable text — Round 28's `"$changeType event"` template
  produced garbled snake_case ("First_event_changed event," reported as "event moved event" not
  making sense). `EventData.CalendarChange` already carries a proper typed `affectsFirstEvent:
  Boolean` alongside the free-form string; the subtext is built from that instead — "Your first event
  changed" (or "Your calendar changed" as a fallback for a future `changeType` this flag doesn't
  cover), with the affected subject bolded.

Round 30 — theme-consistent occlusion, an eased (not linear) fade shared across every scrim in the
app, the timeline track finally participating in that fade instead of relying on incidental z-order
coverage, and a follow-up pass on hero/subtext spacing and content.

- **The collapsed alarm card's occlusion scrim now behaves the same in both themes.** Round 23
  found light theme's fully-opaque "solid" zone read as content abruptly vanishing rather than
  fading, and capped light theme's own alpha to stay dimmed-but-visible instead — but left dark
  theme hardcoded to a fully-opaque `1f`, on the (at-the-time reasonable, now-outdated) assumption
  dark theme's fade "read fine" fully opaque. Reported as an inconsistency ("only in light theme"):
  fully opaque genuinely hides content rather than dimming it, so light theme's ramp
  (`MAX_FADE_OVERLAY_ALPHA`/`MAX_FADE_OVERLAY_SETTLED_ALPHA`, renamed off their `_LIGHT_` prefixes)
  now applies unconditionally in `HomeContent.kt`'s `StickyAlarm`.
- **Every fade-to-transparent scrim in the app now eases rather than linearly ramps.** A straight
  `Brush.verticalGradient` alpha lerp crosses a high-contrast element (the timeline's solid-filled
  sleep track, in particular) at a constant rate, which reads as an artificial hard edge rather than
  a soft dissolve. New shared `ui/components/EasedFade.kt`: `easedFadeInAlpha`/`easedFadeOutAlpha`
  (scalar, `FastOutSlowInEasing` by default) and `easedVerticalGradient` (samples that same curve at
  16 stops, since `Brush.verticalGradient` only linearly interpolates *between* the stops it's given
  — a genuinely eased curve means supplying many stops sampled from an `Easing`, not two or three
  flat ones). Applied to `StickyAlarm`'s own tail gradient and both of `EdgeFades.kt`'s top/bottom
  scrims, for one consistent fade language across the app.
- **The timeline track now fades in lockstep with the scrim, instead of relying on incidental
  z-order coverage.** Root cause of the reported "track cuts off, looks like a mistake": every draw
  call in `TimelineTrackOverlay.kt` used a flat, constant-alpha `Color` with zero scroll-position
  awareness — the track's only "occlusion" was happening to sit z-order-under `StickyAlarm`'s scrim
  Box. Combine that with `TimelineNode`'s `DisposableEffect` yanking a scrolled-off row's anchor out
  of the registry the instant its composable is disposed (no interpolation), and the track's cap
  could visibly snap to a new position in one frame. Fix: a new `FadeZone` class (same file) shares
  the exact fade bounds `StickyAlarm` uses (`cardBottomPx`/`totalPx`, hoisted up to `HomePlanContent`
  so both composables derive them from the same measured card height rather than each guessing
  independently) and fades the track's own colors in as they move away from the card — by the time
  an anchor's row actually gets disposed, it's already faded to near-nothing, so the snap becomes
  imperceptible. Applied three ways depending on how much Y-span each draw call has: background/pill
  fills (can span a large range) get a real `easedVerticalGradient` brush; the spine (already broken
  into short pieces at anchor gaps) gets a flat per-piece alpha from that piece's own start-Y; sockets
  (a single point, no span) get an exact flat alpha from their own center. Fills skip building a brush
  entirely when they're nowhere near the zone (`FadeZone.mayOverlap`) — the common case away from the
  top of the screen — so the extra cost is paid only by whatever's actually near the card.
- **The hero kicker now hugs its headline as tightly as the headline hugs its own subtext.** Round 28
  deliberately widened `HERO_KICKER_GAP` past the headline→subtext gap so the headline read as
  belonging with the subtext, not the caption above it; live feedback asked for the opposite —
  `HERO_KICKER_GAP` now matches `Spacing.xxs` exactly, the same value the subtext gap already used.
- **`AlarmSnoozed`'s subtext rephrased as the upside, not the bare mechanic**: "9 minutes added" →
  "You get to sleep for 9 extra minutes" — still built from the one fact this event actually carries
  (`snoozeDurationMinutes`), just friendlier and longer, per explicit direction to skip correlating it
  with a later AI replan's resolved time (fragile: no replan exists past 3 snoozes, other events can
  interleave and get matched instead, throttling can skip it outright).
- **`CalendarChange`'s subtext surfaces the actual calendar event title.** The title was already
  being read, just discarded one call too early: `CalendarChangeAnalyzer.computeFirstEventSig` read a
  full `CalendarReader.Event` (with a real `.title`) but only folded `id`/`start`/`location`/
  `selfAttendeeStatus` into its signature string. `CalendarChangeAnalyzer.Result` gained a
  `firstEventTitle: String?`, threaded through `CalendarChangeWorker.kt` into a new
  `EventData.CalendarChange.firstEventTitle` field (defaulted, so already-persisted events missing it
  still deserialize fine) and into `TimelineMapper.kt`'s subtext: "{title} on your calendar changed,"
  falling back to the prior generic "Your first event changed" whenever a title isn't available.

Round 31 — a same-day live-device pass on Round 30 corrects a real bug the subtext-hugging fix never
actually applied, a real color bug in the eased-gradient work, and reverts the whole track-fade
approach and half the gradient scope after live testing rejected them.

- **The subtext still wasn't hugging its title, because Round 29's fix never actually did anything.**
  `CompositionLocalProvider(LocalTextStyle provides ...TightTextStyle)` only affects a `Text` that
  relies on its *default* `style = LocalTextStyle.current` — every `Text` in this timeline (title,
  status, content, the hero's kicker/headline) passes its own explicit `style`, which bypasses
  `LocalTextStyle` entirely regardless of what a provider higher up supplies. The wrapper was dead
  code from the moment it was written. Real fix: bake `tight`'s platform/line-height properties
  directly into the `CronTypography` roles that only ever apply within this timeline context
  (`timelineRowTitle`, `timelineRowTime`, `timelineHeroKicker`, `timelineHeroTitle` — `Type.kt`, via
  `tight.copy(...)` instead of a bare `TextStyle(...)`, the same pattern `lcdHero`/`lcdStack`/
  `timeMono` already use), and `.merge(TightTextStyle)` locally at the two ad hoc
  `MaterialTheme.typography.bodyMedium` subtext call sites (`EventNode.kt`, `SessionTimeline.kt`) —
  `bodyMedium` itself stays untouched since it's a shared, app-wide role. The now-fully-inert
  `CompositionLocalProvider` wrapper in `TimelineNode.kt` is removed.
- **The eased-gradient work from Round 30 had a real color-blend bug, was applied somewhere it
  shouldn't have been, and is reverted.** `easedVerticalGradient(from, to, ...)` used
  `androidx.compose.ui.graphics.lerp(from, to, t)` to interpolate between an opaque color and
  `Color.Transparent` — but `Color.Transparent` is *black* at alpha 0, and a plain per-channel lerp
  blends the RGB channels toward black as alpha drops, producing a muddy gray "shadow" band instead
  of a clean alpha-only fade (reported as "a really ugly [effect] reminiscent of 2010's designs," the
  colors "completely wrong" in light theme). Also in scope was `EdgeFades.kt`'s top *and* bottom
  scrim, when only the collapsed alarm card's own scrim should have been touched ("I really expected
  you to only change the one under the next alarm card"). Given the bug, the narrower-than-intended
  scope, and a live report that the eased curve read as "way too short" and "even less natural than
  before" regardless, both `EdgeFades.kt` and `StickyAlarm`'s own gradient (`HomeContent.kt`) are
  reverted to their exact pre-Round-30 plain linear `Brush.verticalGradient` — the Round 30.1 theme-
  consistency fix (dropping the dark-theme-only fully-opaque cap) is the one Round 30 change to this
  area that stands, since it was never part of this complaint. `EasedFade.kt` is deleted — reverting
  both its only two call sites leaves it with zero callers.
- **The track-fade-through approach is fully reverted — every track element draws at flat, constant
  opacity again, with no exceptions.** Explicit, unambiguous instruction: elements must stay fully
  visible as long as even one pixel of them is still on screen; a scroll-position-derived dissolve is
  rejected outright, not just tuned. `FadeZone`, `fillFaded`, and the per-anchor/per-spine-piece alpha
  multiplies are removed from `TimelineTrackOverlay.kt`; `HomeContent.kt`'s hoisted
  `cardVisiblePx`/`fadeZone` plumbing between `HomePlanContent`/`StickyAlarm`/`TimelineTrackOverlay`
  is removed along with it.
- **The actual track-cutoff bug — still open after Round 30's non-fix — is a geometric discontinuity,
  not a disposal/fade timing issue.** `drawSegment`'s non-cap boundary
  (`if (roundTop || top.cy >= 0f) top.cy - halfTrack else 0f`) was written to guard a *rare*
  mid-insertion stale-frame case (a new true-first row whose `isSegmentTop` hasn't propagated yet),
  but its `top.cy >= 0f` branch fires on *every* ordinary scroll: `computePlacedIfComplete` filters to
  `LazyListState.layoutInfo.visibleItemsInfo`, and the instant the previous topmost tracked anchor
  drops out of that set, the new topmost tracked anchor is very much on-screen (`cy >= 0`) — so
  `bgTop` snaps downward from the flush screen edge (`0f`) to that anchor's own position every single
  time a row scrolls fully past, which is exactly "the track brutally cuts off/despawns." Fixed by
  always extending to the screen edge for a non-cap boundary (`if (roundTop) top.cy - halfTrack else
  0f`, and the mirrored change for the bottom edge) — the rare stale-frame glitch this reintroduces is
  one frame long during a new-item insertion at the very top of the whole timeline, far less
  disruptive than a cutoff on every scroll.

Round 32 — fixes a broken entrance animation (a genuine new-item arrival visibly overlapped the row it
demoted) and a spurious slide-in on plain navigation (Home→Settings→back, or cold start, animated the
whole timeline even though nothing in the data had changed).

- **Root cause of the overlap: a hand-rolled 220ms reveal delay raced the demoted row's own placement
  spring on two independent, unsynchronized clocks.** `AiRunNode`'s old `heroFadeAlpha`/
  `NEW_ENTRY_FADE_DELAY_MS`/`registry.markEnteredOnce` mechanism waited a fixed 220ms before fading the
  new latest row in, meant to give the previous latest row's own reflow (`MaterialTheme.motionScheme
  .defaultSpatialSpec()`, a spring with no fixed settle duration, more so with Expressive's bouncy
  overshoot) time to finish first — but a spring's real settle time isn't bounded to 220ms, so
  whichever animation finished second painted fully opaque over the other for as long as the race
  lasted. Fix: delete the hand-rolled delay entirely and trust `LazyListScope`'s own `animateItem
  (fadeInSpec, placementSpec, fadeOutSpec)` uniformly for every row — verified against the actual
  Compose Foundation source (`LazyLayoutItemAnimator.kt`) that a genuinely new key's appearance
  (`previousIndex == -1 && previousKeyToIndexMap != null`) and an existing key's reposition are both
  driven by the *same* synchronized per-frame system, not two independently-clocked ones. A
  `Modifier.zIndex(if (item.isLatest) 1f else 0f)` on the `AiRun` branch (`SessionTimeline.kt`)
  additionally guarantees the incoming hero paints on top of the demoting row for whatever momentary
  crossing still happens during a normal reorder reflow — a residual overlap source regardless of how
  well the timing itself is fixed.
- **`DayHeaderRow` never had an `animateItem` modifier at all**, so a header snapped to its new
  position instantly while sibling rows were still mid-flight through their own animated reflow.
  Fixed by giving it the same gated `animateItem` shape as the `AiRun`/`Event` branches
  (`sessionTimelineItems`'s new private `gatedAnimateItem` helper).
- **The spurious slide-in on plain navigation had two contributing causes, both rooted in confusing
  "freshly mounted" with "genuinely new data."** `HomePlanContent`'s `cardFullHeightPx` starts at 0 and
  only receives its real measured value a frame or two after `CollapsibleAlarmCard`'s own first layout
  pass — since this lands on a *later* frame than the LazyColumn's very first measure pass, `
  animateItem` sees it as a legitimate offset delta and slides the entire visible timeline, even though
  `uiState.timeline` itself never changed. Separately, the old `registry.markEnteredOnce`-based
  "have I seen this id" bookkeeping lived in `remember`-scoped Compose state, which resets every time
  `HomePlanContent`'s composition is torn down and rebuilt — which happens on every Home↔Settings round
  trip (`HomeViewModel` itself survives via `MainActivity.kt`'s `popUpTo(ROUTE_HOME){ inclusive = false
  }`, but the composition holding `LazyListState`/`TimelineTrackRegistry` does not) — so a row already
  seen before Home was backgrounded could still misreport itself as new on return.
  - Fix part one: `TimelineMapper.kt`'s `diffNewlyArrivedIds(currentIds, previousIds)` is a pure
    function — `previousIds == null` only on the true first check of the caller's lifetime, always
    returning `emptySet()`. `HomeViewModel`'s new `NewlyArrivedIdTracker` holds the one piece of
    mutable bookkeeping this needs *at the ViewModel layer*, which survives the Home↔Settings round
    trip that resets Compose's own `remember` state — diffed on the *uncapped* timeline id set (before
    `capTimeline` truncates), so a genuinely new item (always sorted near the front) is never
    misreported as truncated-away, and an old item scrolling back into the cap window purely because
    the cap boundary shifted isn't misreported as new. Threaded through as `HomeUiState
    .newlyArrivedIds`, into `sessionTimelineItems(newlyArrivedIds = ...)`, into `AiRunNode
    (isNewlyArrived = ...)`, into `TimelineNode` — which now gates its own bespoke Circle→Cookie9Sided
    arrival morph (`latestMorph`/`latestProgress`) on `isNewlyArrived && registry.markEnteredOnce(id)`
    together, rather than on `markEnteredOnce` alone — the one thing that mechanism still needs to
    guard, since that morph isn't driven by `animateItem` at all.
  - Fix part two: `HomeContent.kt`'s new `rememberTimelineSettled(framesToWait = 2)` counts actual
    rendered frames (`withFrameNanos`) since the composable was first composed, independent of device
    speed. `HomePlanContent` threads `!timelineSettled` into `sessionTimelineItems
    (suppressEntranceAnimation = ...)`, which forces every row's `animateItem` specs to `null` — this
    unconditionally wins over `newlyArrivedIds` during the settle window, so a genuine arrival that
    happened while the user was away (e.g. behind Settings) still renders statically on return, per
    the explicit requirement that plain navigation must never animate.
- **Tests**: `TimelineMapperTest` covers `diffNewlyArrivedIds`'s four cases (cold start, one added id,
  an identical re-check, a removed id) and a `buildTimeline` idempotency check (same input twice →
  structurally-equal output, since nothing upstream of the diff is memoized). `HomeViewModelTest`
  drives a real session + a second AI turn insert through `CronDatabase` and Room's own invalidation,
  asserting `newlyArrivedIds` is empty on the first real emission and contains only the new turn's id
  on the next. `SessionTimelineScreenshotTest` gained a settled two-`AiRun` (demoted + latest) capture
  via `suppressEntranceAnimation = true` — the resting frame the old racing-clocks bug never reliably
  reached — confirming no residual double-content. A from-scratch attempt at a dynamic, mid-transition
  `LazyColumn` test (mutating `timeline` state after `setContent` and sampling `animateItem`'s live
  offsets) was abandoned: under this project's `createComposeRule()` + Robolectric harness, pausing
  `mainClock.autoAdvance` before a state-driven `LazyColumn` content change prevents even the very
  first non-animated remeasure from ever running, regardless of `runOnIdle`/`advanceTimeByFrame`
  sequencing — a test-infrastructure gap, not a production bug, and not worth working around given the
  data-layer tests already pin down the actual root cause precisely.

Round 32.1 — a live-device pass on Round 32 found both reported bugs still present; this corrects the
actual mechanisms rather than the ones Round 32 assumed.

- **The settle gate's own signal was wrong, not just its timing.** Round 32's `rememberTimelineSettled`
  gated purely on `cardFullHeightPx > 0` — but `HomeViewModel.uiState` is `stateIn(..., SharingStarted
  .WhileSubscribed(5_000), HomeUiState())`: any time Home is backgrounded (behind Settings) for more
  than 5 seconds — the common case — the whole flow cold-restarts to that all-empty default
  (`initialized = false`, `sessionDisplay = null`) the moment Home is mounted again. `
  CollapsibleAlarmCard` renders that default's "no alarm" empty state, which has its own nonzero
  height and fires `onFullHeight` almost immediately — satisfying `cardFullHeightPx > 0` and flipping
  `settled` true *before* the real `uiState` (and the real card height under it) had even arrived,
  leaving the actual data-arrival jump completely unsuppressed. Fixed by also requiring `uiState
  .initialized`, which only flips true on the first REAL combine emission — so the card height the
  gate settles on is the one driven by genuine data, not the cold-start placeholder.
- **This is not specific to a committed back-navigation pop.** Navigation Compose 2.8.5's `composable
  (popEnterTransition = tabEnter, ...)` is live-seeked by the system predictive-back gesture itself
  (confirmed against the actual library version and `AndroidManifest.xml`'s `android
  :enableOnBackInvokedCallback="true"`) — Home's composition restarts progressively *during the drag*,
  before the user's finger lifts, not just at a discrete "pop committed" moment. `docs/navigation.md`'s
  own summary table calls the tab-level back gesture "instant, no card" — true only in the sense that
  it has no *spatial* motion of its own to half-finish; the settle-gate bug above still fully applies
  frame-by-frame throughout the live-seeked drag, which is what produced the reported "track and
  content slide in from the top, misaligned" during a predictive-back preview specifically.
- **The "whole timeline scales upward" report on a new arrival is a reflow-fanout problem, not an
  overlap regression.** Inserting one row at the top of a long timeline shifts every row below it down
  by that row's height, and since every row has `animateItem`'s `placementSpec` active, all of them
  reposition at once — a `defaultSpatialSpec` spring's longer settle, played across a dozen-plus
  simultaneously-reflowing rows with Expressive's bounce, reads as the whole timeline rubber-banding
  into place rather than a crisp single insertion. `gatedAnimateItem` (`SessionTimeline.kt`) now uses
  `fastSpatialSpec`/`fastEffectsSpec` instead of `default*` — same spring family, shorter window for
  the mass-reflow to be visible. Not independently re-verified live as of this writing (device
  disconnected mid-session); flagged here rather than silently assumed fixed.

Round 33 — a code-reading pass (no live device available) found a second, independent cause of the
Round 32.1 "track and content slide in misaligned" report that neither prior round's fix touched.

- **`TimelineTrackOverlay` and the `LazyColumn` are separate sibling composables that only agree on
  position via `TimelineTrackRegistry`, and that registry starts empty on the exact same fresh-mount
  window Round 32.1 already found.** `rememberTimelineTrackRegistry()` is `remember`-scoped to
  `HomePlanContent`, so a cold start or a Home remount after a Settings round trip gives it a brand
  new, empty registry. Each row re-reports its own position (`TimelineNode`'s `Modifier
  .onGloballyPositioned`) and descriptor (a `SideEffect`) independently as it recomposes, and
  `TimelineTrackOverlay`'s `computePlacedIfComplete` only accepts a fresh snapshot once *every*
  currently-visible anchor has done so — until then it keeps painting `lastComplete`, which on a fresh
  registry starts as an empty list. Meanwhile the `LazyColumn`'s own row content has no dependency on
  the registry at all and renders fully opaque on the very first frame. The two subtrees have no shared
  layout/draw pass, so nothing keeps them in step during this window — the track is blank or stale for
  however many frames it takes every visible row to catch up, while the content beside it is already
  final. This is the literal mechanism behind "track and content... misaligned," and it's untouched by
  Round 32/32.1's fix, which only threads into `sessionTimelineItems`' `animateItem` specs and never
  reaches `TimelineTrackOverlay` at all.
- **Fix: gate the overlay's paint on the same settle signal, not a second one.** `TimelineTrackOverlay`
  gained a `visible: Boolean = true` param; its `drawBehind` block still updates `lastComplete`/
  `computePlacedIfComplete` unconditionally (so the first frame after settling paints immediately, with
  no extra catch-up delay), but only calls `drawTrack(...)` when `visible` is true. `HomeContent.kt`
  passes `visible = timelineSettled` — the identical boolean already driving `suppressEntranceAnimation`
  — rather than deriving a second settle signal, since both consumers are answering the same question
  ("has this fresh mount's first real layout with real data landed yet?"). Steady-state scrolling is
  unaffected: `timelineSettled` is a one-time latch that never reverts, so every frame after the
  initial settle calls `drawTrack` exactly as before, and the overlay's intentionally zero-lag
  draw-phase registry read (see `TimelineTrackOverlay`'s own KDoc on why it avoids `derivedStateOf`) is
  untouched.
- **Not independently re-verified live as of this writing** (device disconnected for the whole
  session) — build, lint, and the existing `SessionTimelineScreenshotTest` suite (which exercises the
  default `visible = true` path and shows no regression in the settled resting frame) all pass, but per
  this file's own standing note, none of that substitutes for a real predictive-back gesture and a real
  new-arrival check on-device. Do that before trusting this fix; if the symptom still reproduces, the
  next step is the more invasive structural alternative noted in earlier rounds (moving the alarm
  card's reserved space from an in-list `item("alarm-spacer")` into the `LazyColumn`'s own
  `contentPadding.top`), not another patch on the registry/overlay split.

Round 35 — a live-device pass (both navbar tabs, both nav directions, real predictive-back drags)
found two independent correctness bugs behind symptoms earlier rounds had only chased with timing
tweaks, plus two follow-up refinements once the fixes were tested more broadly.

- **Track/content misalignment during any Home nav transition.** `registry.positions` used to cache
  a plain `Offset` computed once inside `TimelineNode`'s `onGloballyPositioned`. But `MainActivity.kt`'s
  navbar tab transitions are a `graphicsLayer` scale/alpha transform on the whole entering/exiting
  subtree — a draw-phase-only transform that doesn't retrigger layout — so each row's cached position
  ended up baked in at different, mutually-inconsistent points of the animation. Fixed by not caching a
  value at all: `registry.positions` now holds the live `LayoutCoordinates` handle, queried fresh via
  `LayoutCoordinates.localPositionOf` at draw time in `TimelineTrackOverlay.computePlacedAnchors` —
  mapping the anchor's own untransformed local center directly into the overlay's local space in one
  step, so Compose applies the real transform matrix (translation and scale) between the two exactly
  once. An initial attempt used a window-space delta instead, which double-counted the transition's
  scale since the overlay's own draw content sits inside the same scaled subtree; `localPositionOf`
  avoids that round trip entirely.
- **Track flush-to-screen-edge flash on a new arrival.** `drawSegment`'s `isSegmentTop`/`isSegmentBottom`
  reasoning could read stale right after a new anchor is prepended and hasn't registered yet, flushing
  the track's background to the literal screen edge under the alarm card for a frame. A same-round
  attempt cross-checked against `listState.layoutInfo.visibleItemsInfo` membership to catch this, but
  further live testing found `visibleItemsInfo` recomputes on essentially every scroll frame while the
  registry's own flags update on composition/effect timing — two independently-clocked systems that
  briefly disagree constantly during ordinary scrolling, producing a worse, more frequent flicker than
  the narrow bug it targeted. Reverted back to pure `descriptor.isSegmentTop`/`isSegmentBottom`, computed
  fresh from `uiState.timeline` every recomposition, which can't race with scrolling since scrolling
  never changes `uiState.timeline`. The narrow new-arrival case is real but rare and not re-addressed
  here; a future fix should be scoped to "did the timeline's head genuinely change" as a data event, not
  anything derived from `layoutInfo` or scroll position.
- **A disposed row's stale-position ghost, tried and reverted.** A same-round attempt cached a disposed
  row's last-known placement and kept drawing it until its position proved genuinely off-screen, to
  paper over a row's registry entry disappearing the instant `LazyColumn` disposes it (which doesn't
  necessarily wait until a row is fully past the edge). Reverted after live testing found it traded one
  bug for a worse one: a disposed row's cached position never updates again, so "still on screen" could
  evaluate true forever — a permanently stuck, non-scrolling ghost element. Back to drawing only what's
  currently registered; a row disposing while still visually on-screen can still flicker, and this
  remains an open issue — the right fix controls `LazyColumn`'s own beyond-viewport composition margin
  directly, rather than caching or second-guessing its disposal decisions from outside.
- Also fixed the screenshot tests silently rendering with no track at all, since they never advanced
  the test clock past the settle gate an earlier round introduced — all of `TimelineNodeScreenshotTest`'s
  cases plus `PillPressMorphScreenshotTest`, confirmed by tracing every `captureRoboImage()` call site
  in the touched test files, not just the handful first noticed.

Round 36 — addresses Round 35's "track flush-to-screen-edge flash on a new arrival," left open there.

- **A first attempt — `roundTop = top.descriptor.isSegmentTop || anchors.none { it.descriptor
  .isSegmentTop }` — was tried and rejected.** `registry.remove(id)` clears both `descriptors` and
  `positions` together on disposal, including ordinary scroll-driven disposal (the same "row disposes
  while still visually on-screen" behavior Round 35's ghost-caching attempt above already found). So
  once the *true* top row is disposed by plain scrolling, no placed anchor has `isSegmentTop == true`
  either — the exact same observable signal as "the new top row registered a descriptor but not yet a
  position." An `anchors`-only check can't tell these apart, and rounds the current top cap on every
  ordinary scroll past the original first row — reintroducing the same class of flicker the
  `visibleItemsInfo` cross-check was reverted for in Round 35, just reached through registry state
  instead of layout state.
- **Fix: disambiguate using `registry.descriptors` directly**, which still distinguishes the two cases
  — a descriptor claiming `isSegmentTop`/`isSegmentBottom` exists somewhere in the registry (new row,
  descriptor written but position not yet registered) vs. no descriptor anywhere claims it (true top/
  bottom genuinely disposed by scrolling, both maps cleared together). `drawTrack` now computes
  `topUnplaced = registry.descriptors.values.any { it.isSegmentTop } && placed.none { it.descriptor
  .isSegmentTop }` (and the bottom equivalent), passed into `drawSegment` as `roundTop = top.descriptor
  .isSegmentTop || topUnplaced`. Still purely data-derived (registry descriptor state, not scroll/
  layoutInfo) — self-corrects the next frame once the new anchor's position registers, and is `false`
  in the ordinary-disposal case since no descriptor anywhere claims the flag anymore.
