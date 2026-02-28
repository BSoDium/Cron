package fr.bsodium.cron.engine.model

import java.time.Duration

/**
 * Diagnostic information about the travel time estimation attempt.
 *
 * Allows the UI to show exactly what happened during routing:
 * whether it succeeded, why it was skipped, or what error occurred.
 */
data class TravelInfo(
    /** Whether a travel time provider (API key) was configured. */
    val hasApiKey: Boolean,

    /** Whether device location was available. */
    val hasDeviceLocation: Boolean,

    /** Whether the target event has a non-blank location string. */
    val hasEventLocation: Boolean,

    /** The event's location string, if any. */
    val eventLocation: String? = null,

    /** The estimated travel duration, or null if estimation failed or was skipped. */
    val travelTime: Duration? = null,

    /** Error message from the API call, if any. */
    val error: String? = null
) {
    /** Whether all preconditions were met to attempt an API call. */
    val wasAttempted: Boolean
        get() = hasApiKey && hasDeviceLocation && hasEventLocation

    /** Whether the estimation succeeded. */
    val isSuccess: Boolean
        get() = travelTime != null
}
