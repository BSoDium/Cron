package fr.bsodium.cron.debug

import android.content.Context

/** DEBUG-ONLY. Persists the mock-API toggle and active scenario across process restarts. */
class MockApiPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    var scenario: MockScenario
        get() {
            val name = prefs.getString(KEY_SCENARIO, null) ?: return MockScenario.PLAN_SUCCESS
            return runCatching { MockScenario.valueOf(name) }.getOrDefault(MockScenario.PLAN_SUCCESS)
        }
        set(value) { prefs.edit().putString(KEY_SCENARIO, value.name).apply() }

    companion object {
        private const val PREFS_NAME = "mock_api"
        private const val KEY_ENABLED = "mock_api_enabled"
        private const val KEY_SCENARIO = "mock_api_scenario"
    }
}
