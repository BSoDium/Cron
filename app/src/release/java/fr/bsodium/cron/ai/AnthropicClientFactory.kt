package fr.bsodium.cron.ai

import android.content.Context

/** RELEASE variant — always returns the real [AnthropicClient]. */
object AnthropicClientFactory {
    fun create(context: Context, apiKeyProvider: () -> String?): AnthropicMessages =
        AnthropicClient(apiKeyProvider = apiKeyProvider)
}
