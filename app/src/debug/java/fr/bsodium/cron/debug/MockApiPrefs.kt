package fr.bsodium.cron.debug

import android.content.Context

/** DEBUG-ONLY. Persists mock-API preferences across process restarts. */
class MockApiPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Capability gate — enables the mock infrastructure and shows the FAB chevron. */
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    /** Active mode — when true the next run uses canned responses instead of the real API. */
    var isMockActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, true)
        set(value) { prefs.edit().putBoolean(KEY_ACTIVE, value).apply() }

    companion object {
        private const val PREFS_NAME = "mock_api"
        private const val KEY_ENABLED = "mock_api_enabled"
        private const val KEY_ACTIVE = "mock_api_active"
    }
}
