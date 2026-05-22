package fr.bsodium.cron.settings

import android.content.Context
import kotlinx.datetime.Instant

/**
 * Tracks per-source poll checkpoints (last-seen timestamps).
 *
 * Backed by SharedPreferences instead of DataStore because the
 * Health Connect worker needs synchronous reads on a background
 * thread and the values are tiny.
 */
class PollCheckpointStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastHealthConnectPoll(): Instant? = prefs.getLong(KEY_HC, -1L)
        .takeIf { it > 0 }
        ?.let { Instant.fromEpochMilliseconds(it) }

    fun setLastHealthConnectPoll(instant: Instant) {
        prefs.edit().putLong(KEY_HC, instant.toEpochMilliseconds()).apply()
    }

    fun lastLocationFixAt(): Instant? = prefs.getLong(KEY_LOC, -1L)
        .takeIf { it > 0 }
        ?.let { Instant.fromEpochMilliseconds(it) }

    fun setLastLocationFix(lat: Double, lng: Double, at: Instant) {
        prefs.edit()
            .putLong(KEY_LOC, at.toEpochMilliseconds())
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LNG, lng.toFloat())
            .apply()
    }

    fun lastLocationLatLng(): Pair<Double, Double>? {
        val lat = prefs.getFloat(KEY_LAT, Float.NaN)
        val lng = prefs.getFloat(KEY_LNG, Float.NaN)
        return if (lat.isNaN() || lng.isNaN()) null else lat.toDouble() to lng.toDouble()
    }

    private companion object {
        const val FILE = "cron_poll_checkpoints"
        const val KEY_HC = "hc_last_poll_ms"
        const val KEY_LOC = "location_last_fix_ms"
        const val KEY_LAT = "location_last_lat"
        const val KEY_LNG = "location_last_lng"
    }
}
