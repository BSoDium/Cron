package fr.bsodium.cron.ui.theme

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
