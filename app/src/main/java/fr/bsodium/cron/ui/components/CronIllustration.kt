package fr.bsodium.cron.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/**
 * A themed illustration whose palette tracks the active Material 3 color scheme.
 * The underlying vector assets use sentinel hues (FF0001, FF0002, etc.) which
 * this component remaps onto the live theme.
 */
@Composable
fun CronIllustration(
    type: CronIllustrationType,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val source = ImageVector.vectorResource(type.resourceId)
    
    val illustration = remember(source, scheme) {
        source.rethemed { name, original ->
            when {
                // High Emphasis / Bold Roles
                name == "primary" || name == "flower-center" || name == "flower-pistil" ||
                name == "sun" || name == "eye" -> scheme.primary

                // Mid-Tone / Softer Containers
                name == "primary_soft" || name == "flower-petal" || name == "water-dark" ||
                name == "eyelid" || name == "upper-lip" || name == "lower-lip" -> scheme.primaryContainer

                // Secondary Palette (Structure & Features)
                name == "secondary" || name == "jar" || name == "nose" || name == "eyebrow" ||
                name == "flower-outline" || name.endsWith("-stem") -> scheme.secondary

                // Light Accents
                name == "secondary_soft" || name == "water-light" -> scheme.secondaryContainer

                // Tertiary Palette (Natural elements / Accents)
                name == "tertiary" || name == "flower-leaf" || name == "flower-stem" -> scheme.tertiary

                // Outlines & Contrast
                name == "ink" || name == "face-line"  -> scheme.onSurface

                // Background-ish / Subtle
                name == "surface" || name == "cloud" || name == "jar-opening" ||
                name == "flower-highlight" -> scheme.surfaceVariant

                else -> original
            }
        }
    }

    Image(
        imageVector = illustration,
        contentDescription = null,
        modifier = modifier
    )
}

enum class CronIllustrationType(@param:DrawableRes internal val resourceId: Int) {
    Landscape(R.drawable.illus_landscape),
    Flowers1(R.drawable.illus_flowers_1),
    Flowers2(R.drawable.illus_flowers_2),
    Flower1(R.drawable.illus_flower_1),
    Flower2(R.drawable.illus_flower_2),
    Face(R.drawable.illus_face),
}

@Preview(name = "Illustrations Library — Light", showBackground = true)
@Preview(name = "Illustrations Library — Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CronIllustrationLibraryPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                items(CronIllustrationType.entries) { type ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        CronIllustration(
                            type = type,
                            modifier = Modifier.size(140.dp)
                        )
                        Text(
                            text = type.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
