package fr.bsodium.cron.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CronColors {
    val pageBackground: Color
        @Composable get() = if (isSystemInDarkTheme())
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surfaceContainer

    val elementSurface: Color
        @Composable get() = if (isSystemInDarkTheme())
            MaterialTheme.colorScheme.surfaceContainer
        else
            MaterialTheme.colorScheme.surface
}
