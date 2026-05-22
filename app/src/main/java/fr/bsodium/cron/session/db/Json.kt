package fr.bsodium.cron.session.db

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for persisting session data in Room columns
 * and for the Anthropic API payloads. Configured to ignore unknown keys
 * so that the data layer survives schema drift across releases.
 */
val SessionJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    // No global classDiscriminator — kotlinx default is "type", which matches the
    // Anthropic wire format for ContentBlock. EventData overrides via
    // @JsonClassDiscriminator("kind").
}
