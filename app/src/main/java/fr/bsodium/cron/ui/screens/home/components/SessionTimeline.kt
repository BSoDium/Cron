@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.screens.home.timelineAsleepStates
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun LazyListScope.sessionTimelineItems(
    timeline: List<TimelineItem>,
    hasMore: Boolean,
    registry: TimelineTrackRegistry,
    newlyArrivedIds: Set<String> = emptySet(),
    // Forces every row's animateItem specs to null while the timeline's own composition is still settling after a fresh mount (see HomeContent.kt's rememberTimelineSettled), so cold-start/navigation never replays an entrance animation for unchanged data.
    suppressEntranceAnimation: Boolean = false,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    val asleepStates = timelineAsleepStates(timeline)
    // A DayHeader is purely decorative (never a track anchor) — the true segment/cap boundary is the first/last REAL row, skipping headers, not the raw list ends.
    val firstAnchorIndex = timeline.indexOfFirst { it !is TimelineItem.DayHeader }
    val lastAnchorIndex = timeline.indexOfLast { it !is TimelineItem.DayHeader }

    item(key = "timeline-top-spacer") {
        Spacer(Modifier.height(Spacing.xxxl))
    }

    timeline.forEachIndexed { index, item ->
        when (item) {
            // A plain in-flow row; every header renders identically today (see DayHeaderRow's KDoc) and shares the same animateItem choreography as the AiRun/Event rows below it.
            is TimelineItem.DayHeader -> item(key = item.id, contentType = TimelineItem.DayHeader::class) {
                DayHeaderRow(item = item, modifier = gatedAnimateItem(suppressEntranceAnimation))
            }
            is TimelineItem.AiRun -> item(key = item.id, contentType = TimelineItem.AiRun::class) {
                AiRunNode(
                    item = item,
                    registry = registry,
                    isSegmentTop = index == firstAnchorIndex,
                    isSegmentBottom = index == lastAnchorIndex,
                    isAsleepAbove = asleepStates[index],
                    isAsleepBelow = asleepStates.getOrNull(index + 1) ?: asleepStates[index],
                    isNewlyArrived = item.id in newlyArrivedIds,
                    onClick = { onOpenAiRun(item.iteration.turnIndex, item.sessionId) },
                    // zIndex ahead of animateItem: normal paint order would draw a newly-arrived latest row under the still-demoting previous latest row while its placement slide is in flight; painting the incoming hero on top removes that overlap source.
                    modifier = Modifier
                        .zIndex(if (item.isLatest) 1f else 0f)
                        .then(gatedAnimateItem(suppressEntranceAnimation)),
                )
            }
            is TimelineItem.Event -> item(key = item.id, contentType = TimelineItem.Event::class) {
                EventNode(
                    item = item,
                    registry = registry,
                    isSegmentTop = index == firstAnchorIndex,
                    isSegmentBottom = index == lastAnchorIndex,
                    isAsleepAbove = asleepStates[index],
                    isAsleepBelow = asleepStates.getOrNull(index + 1) ?: asleepStates[index],
                    modifier = gatedAnimateItem(suppressEntranceAnimation),
                )
            }
        }
    }

    if (hasMore) {
        item(key = "view-history") {
            FilledTonalButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                shape = Radius.full,
            ) {
                Text("View full history")
            }
        }
    }
}

/** The shared `animateItem` shape every row in [sessionTimelineItems] uses, including
 *  [TimelineItem.DayHeader] rows — [suppress] forces every spec to null (a fresh mount still
 *  settling, see `HomeContent.kt`'s `rememberTimelineSettled`). `fastSpatialSpec`/`fastEffectsSpec`
 *  (not `default*`): inserting one row at the top of a long timeline reflows every row below it at
 *  once, and a *default* spatial spring's longer settle plus Expressive's bounce, played across a
 *  dozen-plus simultaneously-reflowing rows, reads as the whole timeline "scaling"/rubber-banding
 *  into place rather than a crisp single insertion; `fast*` keeps the same spring family but
 *  shortens the window where that's visible. */
@Composable
private fun LazyItemScope.gatedAnimateItem(suppress: Boolean): Modifier {
    val placementSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    return Modifier.animateItem(
        fadeInSpec = if (suppress) null else fadeSpec,
        placementSpec = if (suppress) null else placementSpec,
        fadeOutSpec = if (suppress) null else fadeSpec,
    )
}

/** The color that identifies each track — a *fill* for both tracks, not an outline. The awake track
 *  uses `colorScheme.surfaceContainerHigh`, a modest step up from the page background —
 *  `TimelineTrackOverlay.kt`'s spine is separately tuned to stay visible against it.
 *
 *  The asleep track uses `colorScheme.secondary` — a bold, deliberately prominent solid fill so the
 *  sleep stretch still reads as dark/prominent rather than a blended tint (this app's flat-design
 *  rule rules out a literal shadow/elevation for "the sleep track should feel raised"). Kept off
 *  `primary` specifically to free up the primary palette for other timeline elements (the AI-run
 *  family) without a same-hue collision against the sleep track itself — see `docs/color-roles.md`. */
@Composable
internal fun trackColorFor(isAsleep: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isAsleep) {
        scheme.secondary
    } else {
        scheme.surfaceContainerHigh
    }
}

/** The single fill/tint pair every anchor's socket uses, regardless of accent, trigger, or item
 *  kind — secondary-family on the sleep track, primary-family on the awake track: everything on
 *  that track consistently uses that one palette, rather than a scattered mix of per-accent hues,
 *  cross-track "borrowed" colors, and one-off escape hatches.
 *
 *  Deliberately *not* the same literal role as [trackColorFor] on the sleep side: [trackColorFor]'s
 *  own asleep fill is already the bold `secondary` solid, so an anchor using that identical role
 *  would be visually indistinguishable from the track itself, reading as "just a glyph floating on
 *  the pill, no shape." `secondaryContainer` stays in the same secondary family while actually
 *  contrasting against a `secondary`-filled track, the same relationship the awake side already has
 *  for free (`primary` accent against the awake track's neutral `surfaceContainerHigh` fill). */
@Composable
internal fun trackAccentColor(isAsleep: Boolean): Color =
    if (isAsleep) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary

/** [trackAccentColor]'s paired foreground — always the M3-guaranteed `on*` role for whichever fill
 *  [trackAccentColor] resolved to. */
@Composable
internal fun trackOnAccentColor(isAsleep: Boolean): Color =
    if (isAsleep) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary

@Composable
internal fun AiRunNode(
    item: TimelineItem.AiRun,
    registry: TimelineTrackRegistry,
    isSegmentTop: Boolean,
    isSegmentBottom: Boolean,
    isAsleepAbove: Boolean,
    isAsleepBelow: Boolean,
    onClick: () -> Unit,
    // Whether this row's own id is genuinely new since HomeViewModel's last emission (see TimelineMapper.kt's diffNewlyArrivedIds) — only meaningful for the Latest row, gating its Circle→Cookie9Sided arrival morph, since a plain isLatest check alone is also true on a fresh mount with no new data.
    isNewlyArrived: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val iter = item.iteration
    val symbol = if (iter.thread.isMocked) MaterialSymbol.Code else runSymbol(iter.kind)
    val atCap = isSegmentTop || isSegmentBottom || (!isAsleepAbove && isAsleepBelow) || (isAsleepAbove && !isAsleepBelow)
    /** `isAsleepAbove || isAsleepBelow`, not `isAsleepAbove` alone: a transition cap gets nested
     *  inside the sleep pill's rounded end on EITHER edge ([TimelineTrackOverlay]'s
     *  `buildSleepPills`), but `isAsleepAbove` is only true for the sleep-onset direction — a
     *  wake-up-direction cap needs the same treatment, see [EventNode]'s own
     *  `isCapNestedInSleepPill`. */
    val isCapNestedInSleepPill = isAsleepAbove || isAsleepBelow
    /** A cap anchor's socket always matches whichever track it sits on ([trackAccentColor]/
     *  [trackOnAccentColor]). The `!atCap` case's `primaryContainer`/`onPrimaryContainer` is never
     *  actually seen — [TimelineNode]'s `effectiveAnchor` normalization discards and overrides any
     *  non-cap `Icon`'s tint/container with its own track-matched interior treatment — but stays
     *  here as a placeholder rather than `null`/a TODO. */
    val anchor = when {
        item.isStreaming -> TimelineAnchor.Loader
        item.isLatest -> TimelineAnchor.Latest(symbol)
        else -> TimelineAnchor.Icon(
            symbol = symbol,
            tint = if (atCap) trackOnAccentColor(isCapNestedInSleepPill) else scheme.onPrimaryContainer,
            containerColor = if (atCap) trackAccentColor(isCapNestedInSleepPill) else scheme.primaryContainer,
            valence = iter.kind.timelineValence(),
        )
    }
    val contentColor = scheme.onSurfaceVariant
    // The alarm time this run resolved to is the "key fact" headline; a cancel/do_nothing turn has no new time, so it falls back to the standing prevTime, then a static label — never the AI's own prose (see the title `when` below), which stays confined to the content slot instead.
    val newTime = iter.thread.newAlarmTime.takeIf { item.isLatest }
    val prevTime = iter.previousAlarmTime.takeIf { item.isLatest }
    /** While streaming, `thread.summary` is still-growing live token text (fine for the "thinking"
     *  pill elsewhere) — never bound into this `maxLines=2` ellipsis headline, or it visibly jitters
     *  and truncates mid-sentence as tokens arrive. Same gate `AiThreadMapper` applies to `answer`;
     *  while streaming this falls back to the stable, non-moving systemMessage kicker below. */
    val heroHeadline = iter.thread.summary?.takeIf { item.isLatest && !item.isStreaming && it.isNotBlank() }
    /** Base plans can be many hours old (the evening plan vs. a replan minutes ago read very
     *  differently) so they get a relative "X ago"; replans are usually close together, where an
     *  exact clock time is more useful for correlating with nearby rows. */
    val kickerSuffix = if (item.isLatest) {
        when (iter.kind) {
            RunKind.ScheduledBase, RunKind.ManualBase -> iter.ranAtEpochMs?.let { "· ${rememberRelativeAgo(it)}" }
            is RunKind.Replan -> "· at ${iter.timeLabel}"
        }
    } else {
        null
    }
    val kickerText = listOfNotNull(iter.systemMessage, kickerSuffix).joinToString(" ")
    val density = LocalDensity.current

    TimelineNode(
        id = item.id,
        registry = registry,
        anchor = anchor,
        isSegmentTop = isSegmentTop,
        isSegmentBottom = isSegmentBottom,
        isAsleepAbove = isAsleepAbove,
        isAsleepBelow = isAsleepBelow,
        isNewlyArrived = isNewlyArrived,
        onClick = onClick,
        modifier = modifier,
        verticalPadding = if (item.isLatest) Spacing.lg else Spacing.md,
        title = {
            /** A row that has NEVER been latest only ever shows the single-line demoted branch
             *  below — reserving hero-sized height on it too (not just a row actually transitioning
             *  away from latest) forced every historical row into a taller box than its one line
             *  needed, offsetting it from the anchor. `remember(item.id)` captures whether THIS row
             *  was latest at its first composition and never un-sets, since `isLatest` only ever
             *  transitions true→false, never the reverse. */
            val everLatest = remember(item.id) { item.isLatest }
            /** The demoted (single-line) state is shorter than the hero (kicker + time) state;
             *  without reserving the hero's height here, the Crossfade instantly resizes the Row
             *  and — since the Row centers its children vertically — the whole anchor/title visibly
             *  re-centers the moment a run gets superseded. Only applies to a row that could ever
             *  actually be in the hero state. */
            val heroMinHeight = remember(density) {
                with(density) {
                    CronTypography.timelineHeroKicker.lineHeight.toDp() + HERO_KICKER_GAP +
                        CronTypography.timelineHeroTimeNew.lineHeight.toDp()
                }
            }
            // Fades the hero headline ↔ plain system-message swap instead of cutting instantly, pairing with TimelineNode's animated anchor-radius shrink; heightIn lives on this wrapping Box (not Crossfade, which has no contentAlignment and top-aligns internally) so centering the demoted text belongs here.
            Box(
                modifier = if (everLatest) Modifier.heightIn(min = heroMinHeight) else Modifier,
                contentAlignment = Alignment.CenterStart,
            ) {
            Crossfade(
                targetState = item.isLatest,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "ai-run-hero-demote",
            ) { isLatest ->
            if (isLatest) {
                Column(verticalArrangement = Arrangement.spacedBy(HERO_KICKER_GAP)) {
                    // Shares the countdown card's `primary` fill so the newest run visually rhymes with it — touches only the neutral page background, so this is a plain on-role pairing, not a nested-container case (docs/color-roles.md).
                    Text(
                        text = kickerText.uppercase(Locale.US),
                        style = CronTypography.timelineHeroKicker,
                        color = scheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // TimelineNode's anchor and trailing-arrow Boxes align to this exact line via RowScope.alignBy, rather than a hand-computed padding offset that requires guessing the kicker's rendered height.
                    Box(modifier = Modifier.declareCenterAs(HeroHeadlineCenter)) {
                    when {
                        // A streaming turn is seeded before its set_alarm result arrives, so both times are transiently null — showing NO_ALARM_LABEL would flash then immediately replace, reading as a glitch; reserve the line's height with an invisible placeholder instead.
                        newTime == null && prevTime == null && item.isStreaming ->
                            Text(text = " ", style = CronTypography.timelineHeroTimeNew, color = Color.Transparent)
                        // A real change: show it as a PREV › NEW pair, NEW bold italic — the key fact at a glance, not a prose sentence.
                        newTime != null && prevTime != null && prevTime != newTime -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(text = prevTime.asClock(), style = CronTypography.timelineHeroTimePrev)
                            Text(text = "›", style = CronTypography.timelineHeroTimePrev, color = contentColor)
                            Text(text = newTime.asClock(), style = CronTypography.timelineHeroTimeNew)
                        }
                        // No prior time to compare against, or it didn't actually change — the pair would just read as a redundant "7:30 › 7:30".
                        newTime != null -> Text(text = newTime.asClock(), style = CronTypography.timelineHeroTimeNew)
                        // No new time at all (cancel/do_nothing) but a prior one exists — the alarm stands as-is, still a time fact, never the AI's prose.
                        prevTime != null -> Text(text = prevTime.asClock(), style = CronTypography.timelineHeroTimeNew)
                        // Nothing resolved at all (rare first-run do-nothing) — a static literal, never AI-generated text, so the big slot can never overflow with long prose.
                        else -> Text(text = NO_ALARM_LABEL, style = CronTypography.timelineHeroTimeNew)
                    }
                    }
                }
            } else {
                Text(
                    text = iter.systemMessage,
                    style = CronTypography.timelineRowTitle,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            }
        },
        status = {
            Crossfade(
                targetState = item.isLatest,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "ai-run-status-demote",
            ) { isLatest ->
            if (isLatest) {
                // status is the row's trailing, vertically-centered slot by construction, so the tap-through arrow belongs here; weight=700 uses Symbol's own wght axis to actually thicken the stroke, not just size the glyph up.
                Symbol(symbol = MaterialSymbol.ArrowForward, contentDescription = null, tint = contentColor, size = 18.dp, weight = 700)
            } else {
                Text(
                    text = iter.ranAtEpochMs?.let { timelineTimeLabel(it, iter.timeLabel) } ?: iter.timeLabel,
                    style = CronTypography.timelineRowTime,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            }
        },
        // The headline above is now exclusively a time fact or NO_ALARM_LABEL, never heroHeadline, so this can't duplicate it; "Latest · HH:MM" moved into the kicker's kickerSuffix. `.merge(TightTextStyle)` — see EventNode.kt's `content` KDoc for why an explicit style needs this directly rather than an ambient provider.
        content = if (item.isLatest && heroHeadline != null) {
            { Text(text = heroHeadline, style = MaterialTheme.typography.bodyMedium.merge(TightTextStyle), color = contentColor) }
        } else {
            null
        },
    )
}

private fun LocalTime.asClock(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

private const val NO_ALARM_LABEL = "No alarm set"

/** Gap between the hero kicker caption and its own headline — matches [TimelineNode]'s `content`
 *  slot gap (`Spacing.xxs`) so the kicker hugs the headline as tightly as the subtext does. Also the
 *  basis for [heroMinHeight] below, which references this constant rather than a hardcoded number so
 *  the two can't drift out of sync. */
private val HERO_KICKER_GAP = Spacing.xxs

/** A recent (within [RECENT_THRESHOLD_MS]) row shows "12m ago" instead of its clock time — the day
 *  header already gives date context for anything older, so absolute [absoluteLabel] stays legible
 *  past the cutoff. [rememberRelativeAgo] (a state-holding, self-re-ticking @Composable) is always
 *  called, never skipped based on the threshold — conditionally calling a remember/produceState-
 *  backed composable across recompositions is the kind of thing that can desync its internal state
 *  from the slot table; picking the *string* to display, not the composable to call, sidesteps it. */
@Composable
internal fun timelineTimeLabel(epochMs: Long, absoluteLabel: String): String {
    val relative = rememberRelativeAgo(epochMs)
    val elapsedMs = Clock.System.now().toEpochMilliseconds() - epochMs
    return if (elapsedMs < RECENT_THRESHOLD_MS) relative else absoluteLabel
}

private const val RECENT_THRESHOLD_MS = 60 * 60 * 1000L

