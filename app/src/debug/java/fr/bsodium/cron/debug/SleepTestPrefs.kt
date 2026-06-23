package fr.bsodium.cron.debug

import android.content.Context

/** DEBUG-ONLY. Persists sleep-detection test toggles across process restarts. */
class SleepTestPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When on, the onset/rearm thresholds collapse to seconds so the chain is testable instantly. */
    var fastOnset: Boolean
        get() = prefs.getBoolean(KEY_FAST_ONSET, false)
        set(value) { prefs.edit().putBoolean(KEY_FAST_ONSET, value).apply() }

    companion object {
        private const val PREFS_NAME = "sleep_test"
        private const val KEY_FAST_ONSET = "fast_onset"
    }
}
