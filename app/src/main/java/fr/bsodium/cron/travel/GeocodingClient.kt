package fr.bsodium.cron.travel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

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

    suspend fun geocode(address: String): GeocodedLocation? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(address, "UTF-8")
            val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Geocoding ${response.code}: ${raw.take(200)}")
                    return@use null
                }
                val json = JSONObject(raw)
                val results = json.optJSONArray("results") ?: return@use null
                if (results.length() == 0) return@use null
                val first = results.getJSONObject(0)
                val loc = first.getJSONObject("geometry").getJSONObject("location")
                GeocodedLocation(
                    lat = loc.getDouble("lat"),
                    lng = loc.getDouble("lng"),
                    formattedAddress = first.optString("formatted_address", address),
                )
            }
        }.getOrElse { e ->
            Log.w(TAG, "Geocoding exception: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GeocodingClient"
    }
}
