package fr.bsodium.cron.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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
 * they bind to brand fonts (`DisplayFontFamily`, `SerifFontFamily`,
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
        fontFamily = ExpressiveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em,
    )

    /** Sentence-style date label on the alarm card — "Tomorrow, you'll wake up at". */
    val dateSentence: TextStyle = TextStyle(
        fontFamily = ExpressiveFontFamily,
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
        fontFamily = DisplayFontFamily,
        fontSize = 24.sp,
        lineHeight = 21.sp,
    )

    /** Small mono clock figures on tiles (sleep-timeline tick labels). */
    val timeMono: TextStyle = tight.copy(
        fontFamily = CodeFontFamily,
        fontSize = 16.sp,
        lineHeight = 16.sp,
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
