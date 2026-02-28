package fr.bsodium.cron.engine.travel

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Estimates travel time using the Google Routes API.
 *
 * Makes a **blocking** HTTP call to the `computeRoutes` endpoint.
 * Must be called from a background thread or within `Dispatchers.IO`.
 */
class GoogleRoutesTravelTimeProvider(
    private val apiKey: String
) : TravelTimeProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun estimateTravelTime(originLat: Double, originLng: Double, destination: String): Duration? {
        return estimateWithError(originLat, originLng, destination).first
    }

    /**
     * Same as [estimateTravelTime] but also returns an error message on failure.
     * Used by the orchestrator to populate [fr.bsodium.cron.engine.model.TravelInfo].
     */
    fun estimateWithError(originLat: Double, originLng: Double, destination: String): Pair<Duration?, String?> {
        return try {
            Log.d(TAG, "Requesting route: ($originLat, $originLng) → \"$destination\"")

            val body = JSONObject().apply {
                put("origin", JSONObject().put("location", JSONObject().put("latLng",
                    JSONObject()
                        .put("latitude", originLat)
                        .put("longitude", originLng)
                )))
                put("destination", JSONObject().put("address", destination))
                put("travelMode", "DRIVE")
            }

            val request = Request.Builder()
                .url(ROUTES_URL)
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "routes.duration")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                val msg = "HTTP ${response.code}: ${responseBody?.take(200)}"
                Log.w(TAG, "Routes API error: $msg")
                return Pair(null, msg)
            }

            val json = JSONObject(responseBody ?: return Pair(null, "Empty response body"))
            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                val msg = "No routes returned"
                Log.w(TAG, msg)
                return Pair(null, msg)
            }

            val durationStr = routes.getJSONObject(0).optString("duration")
            if (durationStr.isNullOrBlank()) {
                return Pair(null, "No duration in route response")
            }

            val seconds = durationStr.removeSuffix("s").toLongOrNull()
                ?: return Pair(null, "Unparseable duration: $durationStr")

            val duration = Duration.ofSeconds(seconds)
            Log.d(TAG, "Route OK: ${duration.toMinutes()} min")
            Pair(duration, null)
        } catch (e: Exception) {
            val msg = "${e.javaClass.simpleName}: ${e.message}"
            Log.w(TAG, "Routes API exception: $msg")
            Pair(null, msg)
        }
    }

    companion object {
        private const val TAG = "CronRoutes"
        private const val ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
