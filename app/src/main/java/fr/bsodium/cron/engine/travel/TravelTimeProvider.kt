package fr.bsodium.cron.engine.travel

import java.time.Duration

/**
 * Abstraction for estimating travel time between two locations.
 *
 * Implementations may call a remote API or return a test double.
 * This interface is intentionally not tied to any Android class.
 */
interface TravelTimeProvider {

    /**
     * Estimates travel time from the given coordinates to [destination].
     *
     * @param originLat Latitude of the origin (device location).
     * @param originLng Longitude of the origin (device location).
     * @param destination Street address of the destination (from calendar event).
     * @return The estimated travel duration, or `null` if the estimate
     *         could not be computed (network error, ambiguous address, etc.).
     */
    fun estimateTravelTime(originLat: Double, originLng: Double, destination: String): Duration?
}
