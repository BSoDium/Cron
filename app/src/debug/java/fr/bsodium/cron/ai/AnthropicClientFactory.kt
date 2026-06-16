package fr.bsodium.cron.ai

import android.content.Context
import android.util.Log
import fr.bsodium.cron.debug.FakeAnthropicClient
import fr.bsodium.cron.debug.MockApiPrefs

/** DEBUG variant — returns [FakeAnthropicClient] when the mock toggle is on; real client otherwise. */
object AnthropicClientFactory {

    private const val TAG = "AnthropicClientFactory"

    fun create(context: Context, apiKeyProvider: () -> String?): AnthropicMessages {
        val prefs = MockApiPrefs(context)
        return if (prefs.isEnabled) {
            Log.i(TAG, "Using FakeAnthropicClient")
            FakeAnthropicClient()
        } else {
            AnthropicClient(apiKeyProvider = apiKeyProvider)
        }
    }
}
