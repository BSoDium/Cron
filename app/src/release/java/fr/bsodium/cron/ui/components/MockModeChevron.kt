package fr.bsodium.cron.ui.components

import androidx.compose.runtime.Composable

/** RELEASE variant — no split FAB in production builds. */
@Composable
fun rememberFabChevron(): FabChevronSlot? = null
