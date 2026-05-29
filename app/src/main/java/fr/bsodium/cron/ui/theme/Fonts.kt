package fr.bsodium.cron.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
private val Vt323 = GoogleFont("VT323")
private val MartianMono = GoogleFont("Martian Mono")
private val Newsreader = GoogleFont("Newsreader")
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
 * Monospace family for small UI text (date label, "Sleep" tag, duration
 * pill, sleep-timeline timestamps and stage labels). Iceland is listed
 * first because it has both lower- and uppercase letterforms (Major Mono
 * Display only carries uppercase, which breaks "Tuesday 17" / "Sleep"
 * rendering). VT323 falls in behind as a chunkier terminal-style backup.
 */
val MonoFontFamily: FontFamily = FontFamily(
    Font(R.font.iceland),
    Font(R.font.vt323),
    Font(googleFont = Iceland, fontProvider = Provider),
    Font(googleFont = Vt323, fontProvider = Provider),
)

/**
 * Real code face for the AI thinking thread — tool-call name chips, result
 * labels, and markdown code spans. Martian Mono is a clean monospace (variable,
 * so it can sit light at small sizes) that replaces the retro [MonoFontFamily],
 * which stays on the LCD clock card and sleep timeline. Bundled .ttf resolves
 * first; downloadable Google Font is the network fallback.
 */
val CodeFontFamily: FontFamily = FontFamily(
    Font(R.font.martian_mono),
    Font(googleFont = MartianMono, fontProvider = Provider),
)

/**
 * Serif face for the AI assistant's final response prose. Sets the
 * "conclusion" apart from the sans-serif thinking content.
 */
val SerifFontFamily: FontFamily = FontFamily(
    Font(R.font.newsreader),
    Font(googleFont = Newsreader, fontProvider = Provider),
)

/**
 * Display face for the alarm card's date label — geometric grotesque
 * with a slightly technical feel that pairs with the LCD digits below.
 */
val DisplayFontFamily: FontFamily = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Medium),
    Font(googleFont = SpaceGrotesk, fontProvider = Provider, weight = FontWeight.Medium),
)
