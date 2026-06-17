package fr.bsodium.cron.ai

/** RELEASE variant — always returns the real [AnthropicClient]. */
object AnthropicClientFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(useMock: Boolean, apiKeyProvider: () -> String?): AnthropicMessages =
        AnthropicClient(apiKeyProvider = apiKeyProvider)
}
