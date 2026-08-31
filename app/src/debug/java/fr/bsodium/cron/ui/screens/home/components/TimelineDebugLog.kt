package fr.bsodium.cron.ui.screens.home.components

import android.content.Context
import android.util.Log
import fr.bsodium.cron.debug.TimelineDebugPrefs

/** DEBUG variant — logs when the "Verbose timeline logging" toggle (Settings → Developer) is on. */
internal object TimelineDebugLog {
    fun d(context: Context, message: () -> String) {
        if (TimelineDebugPrefs(context).isVerboseLoggingEnabled) Log.d(TAG, message())
    }

    private const val TAG = "TimelineTrackOverlay"
}
