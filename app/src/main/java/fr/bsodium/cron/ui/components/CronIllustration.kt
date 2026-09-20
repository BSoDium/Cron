package fr.bsodium.cron.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import fr.bsodium.cron.R

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
        source.recolored { original ->
            when (original) {
                SENTINEL_PRIMARY -> scheme.primary
                SENTINEL_PRIMARY_VARIANT -> scheme.primaryContainer
                SENTINEL_SECONDARY -> scheme.secondary
                SENTINEL_SECONDARY_VARIANT -> scheme.secondaryContainer
                SENTINEL_TERTIARY -> scheme.tertiary
                SENTINEL_TERTIARY_VARIANT -> scheme.tertiaryContainer
                SENTINEL_PAPER -> scheme.surfaceVariant
                SENTINEL_INK -> scheme.onSurface
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

enum class CronIllustrationType(@DrawableRes internal val resourceId: Int) {
    Landscape(R.drawable.illus_landscape),
    Flowers1(R.drawable.illus_flowers_1),
    Flowers2(R.drawable.illus_flowers_2),
    Flower1(R.drawable.illus_flower_1),
    Flower2(R.drawable.illus_flower_2),
    Face(R.drawable.illus_face),
}

private val SENTINEL_PRIMARY = Color(0xFFFF0001)
private val SENTINEL_SECONDARY = Color(0xFFFF0002)
private val SENTINEL_SECONDARY_VARIANT = Color(0xFFFF0003)
private val SENTINEL_TERTIARY = Color(0xFFFF0004)
private val SENTINEL_TERTIARY_VARIANT = Color(0xFFFF0005)
private val SENTINEL_PAPER = Color(0xFFFF0006)
private val SENTINEL_INK = Color(0xFFFF0007)
private val SENTINEL_PRIMARY_VARIANT = Color(0xFFFF0008)
