package fr.bsodium.cron.travel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Suspend-based wrapper around the Google Routes API v2 (`computeRoutes`).
 *
 * Supports all four travel modes. Returns null on any failure (network,
 * no-route, API error) so callers can fall back gracefully.
 */
class RoutesClient(
    private val apiKey: String,
    private val http: OkHttpClient = defaultHttp(),
) {
    enum class TravelMode { DRIVE, TRANSIT, WALK, BICYCLE }

    data class RouteResult(val durationSeconds: Long, val distanceMeters: Int)

    suspend fun estimate(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        mode: TravelMode = TravelMode.TRANSIT,
        arrivalTimeEpochMs: Long? = null,
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = JSONObject().apply {
                put("origin", JSONObject().put("location", JSONObject().put("latLng",
                    JSONObject().put("latitude", originLat).put("longitude", originLng)
                )))
                // Destination as coordinates (geocoded upstream with a location bias) so it can't snap
                // to a same-named place in another city.
                put("destination", JSONObject().put("location", JSONObject().put("latLng",
                    JSONObject().put("latitude", destLat).put("longitude", destLng)
                )))
                put("travelMode", mode.name)
                if (mode == TravelMode.TRANSIT && arrivalTimeEpochMs != null) {
                    put("arrivalTime", kotlinx.datetime.Instant.fromEpochMilliseconds(arrivalTimeEpochMs).toString())
                }
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON))
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful)
                    error("HTTP ${response.code} ($mode): ${raw.take(300)}")
                val routes = JSONObject(raw).optJSONArray("routes")
                    ?: error("no 'routes' key in response")
                if (routes.length() == 0) error("no routes found for mode $mode")
                val route = routes.getJSONObject(0)
                val durSec = route.optString("duration").removeSuffix("s").toLongOrNull()
                    ?: error("could not parse duration: ${route.optString("duration")}")
                RouteResult(durSec, route.optInt("distanceMeters", 0))
            }
        }.onFailure { Log.w(TAG, "Routes ($mode): ${it.message}") }
    }

    companion object {
        private const val TAG = "RoutesClient"
        private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private val JSON = "application/json".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
