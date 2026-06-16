package fr.bsodium.cron.debug

import android.content.Context

/** DEBUG-ONLY. Persists the mock-API toggle across process restarts. */
class MockApiPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    companion object {
        private const val PREFS_NAME = "mock_api"
        private const val KEY_ENABLED = "mock_api_enabled"
    }
}
