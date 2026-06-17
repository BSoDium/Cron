package fr.bsodium.cron.ai

import android.util.Log
import fr.bsodium.cron.debug.FakeAnthropicClient

/** DEBUG variant — returns [FakeAnthropicClient] when [useMock] is true; real client otherwise. */
object AnthropicClientFactory {

    private const val TAG = "AnthropicClientFactory"

    fun create(useMock: Boolean, apiKeyProvider: () -> String?): AnthropicMessages =
        if (useMock) {
            Log.i(TAG, "Using FakeAnthropicClient")
            FakeAnthropicClient()
        } else {
            AnthropicClient(apiKeyProvider = apiKeyProvider)
        }
}
