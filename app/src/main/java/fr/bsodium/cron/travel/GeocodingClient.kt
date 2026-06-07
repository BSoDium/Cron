package fr.bsodium.cron.travel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/** A coordinate used to bias geocoding toward the user's area (so ambiguous names resolve nearby). */
data class LatLng(val lat: Double, val lng: Double)

/**
 * Converts a human-readable address to geographic coordinates using
 * the Google Geocoding API.
 *
 * Returns null on any failure so callers can surface a graceful error
 * to the model instead of crashing the AI turn.
 */
class GeocodingClient(
    private val apiKey: String,
    private val http: okhttp3.OkHttpClient = RoutesClient.defaultHttp(),
) {
    data class GeocodedLocation(val lat: Double, val lng: Double, val formattedAddress: String)

    /**
     * Geocodes [address]. When [bias] is given, a viewport box around it is sent so an ambiguous name
     * (e.g. "Hauptbahnhof") resolves to the user's city rather than the country's most prominent match
     * (the capital) — `bounds` biases ranking, it doesn't hard-restrict results.
     */
    suspend fun geocode(address: String, bias: LatLng? = null): Result<GeocodedLocation> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(address, "UTF-8")
            val boundsParam = bias?.let {
                val box = "${it.lat - BIAS_RADIUS_DEG},${it.lng - BIAS_RADIUS_DEG}|${it.lat + BIAS_RADIUS_DEG},${it.lng + BIAS_RADIUS_DEG}"
                "&bounds=" + URLEncoder.encode(box, "UTF-8")
            }.orEmpty()
            val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$encoded$boundsParam&key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful)
                    error("HTTP ${response.code}: ${raw.take(300)}")
                val results = JSONObject(raw).optJSONArray("results")
                    ?: error("no 'results' key in response")
                if (results.length() == 0)
                    error("no geocoding results for this address")
                val first = results.getJSONObject(0)
                val loc = first.getJSONObject("geometry").getJSONObject("location")
                GeocodedLocation(
                    lat = loc.getDouble("lat"),
                    lng = loc.getDouble("lng"),
                    formattedAddress = first.optString("formatted_address", address),
                )
            }
        }.onFailure { Log.w(TAG, "Geocoding: ${it.message}") }
    }

    companion object {
        private const val TAG = "GeocodingClient"
        // ~±0.4° ≈ a ~45 km box around the user — covers a metro area, biasing away from the capital.
        private const val BIAS_RADIUS_DEG = 0.4
    }
}
