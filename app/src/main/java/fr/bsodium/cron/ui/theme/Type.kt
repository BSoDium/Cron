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

    /** Home greeting — pageTitle on the condensed Roboto Flex face, sized so its caps roughly match the
     *  auto-alarms switch height for a balanced row; the tight width keeps it from overflowing. */
    val greeting: TextStyle = pageTitle.copy(
        fontFamily = CondensedDisplayFontFamily,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    )

    /** Date label on the alarm card — "Thursday 28". */
    val dateLabel: TextStyle = tight.copy(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 24.sp,
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
}

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
