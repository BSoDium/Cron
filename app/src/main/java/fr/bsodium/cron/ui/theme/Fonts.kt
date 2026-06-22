@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package fr.bsodium.cron.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import fr.bsodium.cron.R

/**
 * Material 3 Expressive uses **Google Sans Flex** as its recommended variable
 * font. It isn't guaranteed to be available on the public Google Fonts
 * downloadable-fonts provider yet, so we list it first and fall back to
 * **Roboto Flex** — the long-standing M3 variable font, definitely available
 * on the provider, with similar grotesque-sans letter forms.
 *
 * Compose's font resolver iterates entries in the [FontFamily] until one
 * resolves for the requested weight/style, so Roboto Flex steps in
 * automatically if the Google Sans Flex request comes back empty.
 */
private val Provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val GoogleSansFlex = GoogleFont("Google Sans Flex")
private val RobotoFlex = GoogleFont("Roboto Flex")
private val MajorMonoDisplay = GoogleFont("Major Mono Display")
private val Iceland = GoogleFont("Iceland")
private val MartianMono = GoogleFont("Martian Mono")
private val NotoSerif = GoogleFont("Noto Serif")
private val SpaceGrotesk = GoogleFont("Space Grotesk")

private val WEIGHTS = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

val ExpressiveFontFamily: FontFamily = FontFamily(
    buildList {
        WEIGHTS.forEach { w -> add(Font(googleFont = GoogleSansFlex, fontProvider = Provider, weight = w)) }
        WEIGHTS.forEach { w -> add(Font(googleFont = RobotoFlex, fontProvider = Provider, weight = w)) }
        WEIGHTS.forEach { w ->
            add(Font(R.font.roboto_flex, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))))
        }
    },
)

val ExpressiveCondensedFontFamily: FontFamily = FontFamily(
    buildList {
        WEIGHTS.forEach { w ->
            add(Font(R.font.roboto_flex, weight = w, variationSettings = FontVariation.Settings(
                FontVariation.weight(w.weight),
                FontVariation.Setting("wdth", 85f),
            )))
        }
    },
)

/**
 * Hero LCD face for the next-alarm time. Bundled .ttf files resolve first,
 * with the downloadable Google Fonts entry as a network fallback. The
 * bundled assets are OFL-licensed copies from the Google Fonts repo.
 */
val LcdFontFamily: FontFamily = FontFamily(
    Font(R.font.major_mono_display),
    Font(R.font.iceland),
    Font(googleFont = MajorMonoDisplay, fontProvider = Provider),
    Font(googleFont = Iceland, fontProvider = Provider),
)

/**
 * Clean monospace face — the AI thinking thread (tool-call name chips, result
 * labels, markdown code spans) and the alarm card's sleep block ("Sleep" tag,
 * duration pill, timeline timestamps and stage labels). Martian Mono is variable,
 * so it can sit light at small sizes. Bundled .ttf resolves first; downloadable
 * Google Font is the network fallback. The hero LCD clock keeps its own
 * [LcdFontFamily] face — this is the one mono used for everything else.
 */
val CodeFontFamily: FontFamily = FontFamily(
    Font(R.font.martian_mono),
    Font(googleFont = MartianMono, fontProvider = Provider),
)

/**
 * Serif face for the AI assistant's final response prose — Noto Serif. Sets the
 * "conclusion" apart from the sans-serif thinking content with a grounded, legible
 * voice that hints cleanly across densities (EB Garamond's high-contrast strokes
 * read whimsical and rendered unevenly at small sizes). Noto Serif is variable, so the
 * bundled file is registered at each weight via its `wght` axis (no synthesis / no waiting on
 * the network for bold), with the downloadable entry as a fallback.
 */
val SerifFontFamily: FontFamily = FontFamily(
    Font(R.font.noto_serif, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_serif, weight = FontWeight(450), variationSettings = FontVariation.Settings(FontVariation.weight(450))),
    Font(R.font.noto_serif, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_serif, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_serif, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(googleFont = NotoSerif, fontProvider = Provider),
)

/**
 * Display face for the alarm card's date label — geometric grotesque
 * with a slightly technical feel that pairs with the LCD digits below.
 */
val DisplayFontFamily: FontFamily = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Medium),
    Font(googleFont = SpaceGrotesk, fontProvider = Provider, weight = FontWeight.Medium),
)
