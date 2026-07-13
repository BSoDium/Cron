package fr.bsodium.cron.ui.screens.home.components

import android.content.Context

/** RELEASE variant — verbose timeline logging is never available. */
internal object TimelineDebugLog {
    @Suppress("UNUSED_PARAMETER")
    fun d(context: Context, message: () -> String) {}
}
