package fr.bsodium.cron.debug

import android.content.Context

/** DEBUG-ONLY. Persists the timeline verbose-logging toggle across process restarts. */
class TimelineDebugPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When true, [fr.bsodium.cron.ui.screens.home.components.TimelineDebugLog] logs every
     *  per-frame track cap/segment decision (tag: TimelineTrackOverlay). Read fresh on every call,
     *  never cached, so toggling mid-repro takes effect on the very next drawn frame. */
    var isVerboseLoggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_VERBOSE_LOGGING, false)
        set(value) { prefs.edit().putBoolean(KEY_VERBOSE_LOGGING, value).apply() }

    companion object {
        private const val PREFS_NAME = "timeline_debug"
        private const val KEY_VERBOSE_LOGGING = "timeline_verbose_logging"
    }
}
