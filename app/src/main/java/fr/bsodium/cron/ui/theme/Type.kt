package fr.bsodium.cron.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive typography scale.
 *
 * Same role names as standard M3, but tuned for the expressive aesthetic:
 *  - Display roles are larger and bolder than the M3 defaults.
 *  - Tight negative tracking on display sizes ties characters together for
 *    that oversized-headline feel.
 *  - Body/label roles stay near M3 defaults; readability is non-negotiable.
 *
 * All roles use [ExpressiveFontFamily] (Google Sans Flex → Roboto Flex).
 */
val Typography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-0.03).em,
    ),
    displayMedium = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.025).em,
    ),
    displaySmall = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.015).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em,
    ),
    labelSmall = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.em,
    ),
)

/**
 * App-specific Typography roles that live outside the Material scale because
 * they bind to brand fonts (`CountdownFontFamily`, `SerifFontFamily`,
 * `CodeFontFamily`) rather than the expressive sans. Use these wherever a
 * `TextStyle.copy(fontFamily = ...)` would otherwise be repeated.
 */
object CronTypography {
    private val tight = TightTextStyle

    /** Page title — the History/Settings screen labels. */
    val pageTitle: TextStyle = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    /** Home greeting, line 1 — the muted time-of-day prefix ("Good evening,"). Small so the name below
     *  carries the row; both stack vertically beside the auto-alarms switch. */
    val greetingPrefix: TextStyle = TextStyle(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /** Home greeting, line 2 — the user's name on its own full-width line, so long names no longer
     *  contend with the switch for horizontal room. */
    val greetingName: TextStyle = TextStyle(
        fontFamily = ExpressiveNarrowFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em,
    )

    /** Sentence-style date label on the alarm card — "Tomorrow, you'll wake up at". */
    val dateSentence: TextStyle = TextStyle(
        fontFamily = ExpressiveCondensedFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    )

    /** AI response prose — the "conclusion" paragraph below the thinking thread. */
    val bodySerif: TextStyle = TextStyle(
        fontFamily = SerifFontFamily,
        fontWeight = FontWeight(450),
        fontSize = 18.sp,
        lineHeight = 26.sp,
    )

    /** Code label — AI tool-call name chips and their result labels (Martian Mono). */
    val labelMono: TextStyle = TextStyle(
        fontFamily = CodeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    /** Smaller code label — tool-call name chips and result labels in the thinking timeline. */
    val labelMonoSmall: TextStyle = labelMono.copy(fontSize = 11.sp, lineHeight = 15.sp)

    /** The trailing clock/relative-time figure on a minor (non-hero) timeline row — sized up from
     *  [labelMonoSmall] specifically for this context rather than resizing that shared role, which
     *  is also used well beyond the timeline (tool-call chips, etc.). Based on [tight] (Round 31),
     *  not [labelMono] directly — passing an explicit `style` to a `Text` bypasses whatever
     *  `LocalTextStyle` a `CompositionLocalProvider` might supply entirely, so a timeline row's own
     *  extra font leading can only be stripped by baking [tight] into the style itself, not by
     *  wrapping the composable in a provider (an earlier attempt at exactly that silently did
     *  nothing, since every `Text` in this timeline passes its own `style`). */
    val timelineRowTime: TextStyle = tight.copy(fontFamily = CodeFontFamily, fontSize = 13.sp, lineHeight = 17.sp)

    /** Bold code label — emphasis pills (e.g. the sleep-duration badge). */
    val labelMonoBold: TextStyle = labelMono.copy(fontWeight = FontWeight.Bold)

    /** Mono tile heading — section titles inside card tiles ("Sleep"). */
    val titleMono: TextStyle = TextStyle(
        fontFamily = CodeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    /** The hero LCD clock face — the big slashed-zero alarm time on the card. */
    val lcdHero: TextStyle = tight.copy(
        fontFamily = LcdFontFamily,
        fontSize = LCD_FONT_SIZE,
        lineHeight = LCD_FONT_SIZE,
    )

    /** Compact two-line LCD stack — the remaining/status block beside the hero clock.
     *  lineHeight < fontSize tightens the leading so the stack reads as one unit. */
    val lcdStack: TextStyle = tight.copy(
        fontFamily = CountdownFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 24.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = "tnum",
    )

    /** Small mono clock figures on tiles (sleep-timeline tick labels). */
    val timeMono: TextStyle = tight.copy(
        fontFamily = CodeFontFamily,
        fontSize = 16.sp,
        lineHeight = 16.sp,
    )

    /** Timeline day-boundary heading's headline — the bare day-of-month figure ("7"), rendered
     *  colossal and heavy, calendar-tear-off style, with [timelineDayMonth] as its caption
     *  underneath rather than a same-size sibling beside it. `Black` (900) is the *inactive*-state
     *  ceiling here; `DayHeaderLabel` itself drives a live variable-font `wght`/`wdth` animation on
     *  top of this base style as the header becomes/stops being the active section, so this fixed
     *  style is really just the animation's resting point. `lineHeight` is deliberately tighter than
     *  `fontSize` — `DayHeaderLabel` also applies a bottom-anchored `graphicsLayer` vertical squash
     *  to the glyph itself, and a matching tighter line height keeps the row's own layout height in
     *  step instead of leaving dead space below the now-shorter-looking digit. */
    val timelineDayNumber: TextStyle = TextStyle(
        fontFamily = ExpressiveUltraCondensedFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    )

    /** The month caption underneath [timelineDayNumber] — written in full ("JUNE", not "JUN"), sized
     *  and weighted to read clearly next to the colossal number. Shares [timelineDayActiveLabel]'s
     *  size exactly — the two sit on the same line, one right-aligned, one left — so only the weight
     *  tells them apart. */
    val timelineDayMonth: TextStyle = TextStyle(
        fontFamily = ExpressiveCondensedFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.01.em,
    )

    /** The relative-day label ("TODAY"/"TOMORROW"/"TWO DAYS AGO"/weekday name) — sits on
     *  [timelineDayMonth]'s own line, left-aligned opposite the month; italic, thinner, and rounder
     *  than the month label. [ExpressiveCondensedThinFontFamily] (85 width, a genuine sub-400 `wght`
     *  face) is *less* condensed than the number's own 30-width family, which is what "rounder" means
     *  here: less condensing leaves round letterforms (O, D) closer to their natural shape instead of
     *  squeezed into narrow ovals. Same `fontSize`/`lineHeight` as [timelineDayMonth] — same line,
     *  same size, only weight/style/family differ. */
    val timelineDayActiveLabel: TextStyle = timelineDayMonth.copy(
        fontFamily = ExpressiveCondensedThinFontFamily,
        fontWeight = FontWeight.Light,
        fontStyle = FontStyle.Italic,
    )

    /** Latest timeline run's headline — the AI's resolved outcome sentence, set at headlineMedium
     *  scale so it reads as the one bold fact on the row (Material Expressive reference: a big
     *  isolated headline paired with a small muted caption, not a gradient of medium sizes). Based
     *  on [tight] (Round 31) — see [timelineRowTime]'s KDoc for why; [timelineHeroTimeNew] and
     *  [timelineHeroTimePrev] inherit it for free via `.copy()`. */
    val timelineHeroTitle: TextStyle = tight.copy(
        fontFamily = ExpressiveCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.01).em,
    )

    /** The bold, emphasized NEW time in a hero PREV › NEW pair — same face/size/weight as
     *  [timelineHeroTitle]; bold alone is enough contrast against [timelineHeroTimePrev]'s thin face,
     *  no italic needed. */
    val timelineHeroTimeNew: TextStyle = timelineHeroTitle

    /** The super-thin "before" value in a [timelineHeroTitle]-scale PREV › NEW time pair — a real
     *  sub-400 `wght` face ([ExpressiveCondensedThinFontFamily]), not just a Normal-vs-Bold contrast,
     *  so the pair reads as thin-vs-bold-italic rather than two similarly-weighted numbers. */
    val timelineHeroTimePrev: TextStyle = timelineHeroTitle.copy(
        fontFamily = ExpressiveCondensedThinFontFamily,
        fontWeight = FontWeight.Light,
    )

    /** Small caption above [timelineHeroTitle] carrying the run's trigger category (e.g. "PLANNED",
     *  "ALARM DISMISSED") now that the headline itself carries the outcome sentence. Based on
     *  [tight] (Round 31) — see [timelineRowTime]'s KDoc for why. */
    val timelineHeroKicker: TextStyle = tight.copy(
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.em,
    )

    /** History timeline row title — condensed and muted so it reads as clearly subordinate to
     *  [timelineHeroTitle] through weight/width, not just size. Sized up from 14sp for legibility.
     *  Based on [tight] (Round 31) — see [timelineRowTime]'s KDoc for why. */
    val timelineRowTitle: TextStyle = tight.copy(
        fontFamily = ExpressiveCondensedFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )
}

/** The hero LCD clock size — the SINGLE source for the 76sp contract shared by the rendered clock
 *  ([CronTypography.lcdHero]), the measured ink metrics (LcdMetrics), and the collapse geometry that
 *  derives from them. Change it here and every consumer follows. */
internal val LCD_FONT_SIZE = 76.sp

/**
 * Shared no-padding text style for tight headline rows. Stripping the default
 * font padding lets adjacent rows of LCD/mono text sit flush against each
 * other (Compose's default `includeFontPadding=true` adds extra leading above
 * tall glyphs).
 */
val TightTextStyle: TextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)
