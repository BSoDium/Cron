package fr.bsodium.cron.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.settings.PollCheckpointStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * One-shot location acquisition for the evening_plan event.
 *
 * Acquisition order (a CURRENT fix always beats a stale one — even if coarser — because the user
 * may have just travelled, so a precise but stale fix can be in the wrong city):
 *  1. `getCurrentLocation(HIGH_ACCURACY)` (precise) → source: gps
 *  2. `getCurrentLocation(BALANCED_POWER)` (current, wifi/cell, coarser cap) → source: gps
 *  3. `lastLocation` if recent (< 2h) → source: last_known
 *  4. Stored last-known from [PollCheckpointStore] → source: last_known
 *  5. Registered home address from [SettingsRepository] → source: home_address
 *  6. Nothing → source: unavailable
 *
 * Every successful fix is written to [PollCheckpointStore] so that future
 * cold-start sessions have a fallback even if the backend is unreachable.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission") // permission checked via hasAnyLocationPermission() at entry
    suspend fun acquireForEveningPlan(
        checkpoints: PollCheckpointStore = PollCheckpointStore(context),
        settings: SettingsRepository = SettingsRepository(context),
    ): LocationPayload {
        if (!hasAnyLocationPermission()) {
            return fallback(checkpoints, settings, reason = "permission_denied")
        }

        val fused = LocationServices.getFusedLocationProviderClient(context)

        // Step 1: high-accuracy GPS fix — engages the GPS chip for a precise fix. If it's coarser
        // than MAX_ACCURACY_METERS, persistAndReturn returns null and we fall through.
        val fresh = withTimeoutOrNull(20.seconds.inWholeMilliseconds) {
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(20.seconds.inWholeMilliseconds)
                .build()
            runCatching {
                fused.getCurrentLocation(req, null).awaitNullable()
            }.onFailure { Log.w(TAG, "getCurrentLocation failed", it) }.getOrNull()
        }
        if (fresh != null) {
            persistAndReturn(fresh, LocationSource.Gps, checkpoints)?.let { return it }
        }

        // Step 2: balanced-power CURRENT fix (wifi/cell — works indoors without a GPS lock). A
        // current city-level fix beats a stale precise one elsewhere, so accept a coarser cap.
        val coarse = withTimeoutOrNull(10.seconds.inWholeMilliseconds) {
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(10.seconds.inWholeMilliseconds)
                .build()
            runCatching {
                fused.getCurrentLocation(req, null).awaitNullable()
            }.onFailure { Log.w(TAG, "getCurrentLocation failed", it) }.getOrNull()
        }
        if (coarse != null) {
            persistAndReturn(coarse, LocationSource.Gps, checkpoints, COARSE_MAX_ACCURACY_METERS)?.let { return it }
        }

        // Step 3: a recent last-known fix — labelled LastKnown (it is NOT a fresh fix), so the
        // prompt treats it with appropriate caution rather than as a confident current position.
        val last = runCatching { fused.lastLocation.awaitNullable() }
            .onFailure { Log.w(TAG, "lastLocation failed", it) }.getOrNull()
        if (last != null && isRecent(last, maxAge = 2.hours)) {
            persistAndReturn(last, LocationSource.LastKnown, checkpoints)?.let { return it }
        }

        return fallback(checkpoints, settings, reason = "no_fresh_fix")
    }

    private suspend fun persistAndReturn(
        location: Location,
        source: LocationSource,
        checkpoints: PollCheckpointStore,
        maxAccuracyMeters: Float = MAX_ACCURACY_METERS,
    ): LocationPayload? {
        // Reject fixes coarser than the cap — a precise fix is gated at 500 m (cell-tower-only
        // positions misplace by kilometres); the balanced current fix uses a looser cap since a
        // current city-level position is still far better than a stale precise one elsewhere.
        if (location.accuracy > maxAccuracyMeters) return null
        val capturedAt = Instant.fromEpochMilliseconds(location.time)
        checkpoints.setLastLocationFix(location.latitude, location.longitude, capturedAt)
        return LocationPayload(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = location.accuracy.takeIf { it > 0f },
            source = source,
            capturedAt = capturedAt,
            address = reverseGeocode(location.latitude, location.longitude),
        )
    }

    private suspend fun fallback(
        checkpoints: PollCheckpointStore,
        settings: SettingsRepository,
        reason: String,
    ): LocationPayload {
        // Try stored last-known
        val stored = checkpoints.lastLocationLatLng()
        val storedAt = checkpoints.lastLocationFixAt()
        if (stored != null && storedAt != null) {
            return LocationPayload(
                lat = stored.first,
                lng = stored.second,
                accuracyMeters = null,
                source = LocationSource.LastKnown,
                capturedAt = storedAt,
                address = reverseGeocode(stored.first, stored.second),
            )
        }

        // Try home address
        val homeLat = settings.homeAddressLat.first()
        val homeLng = settings.homeAddressLng.first()
        if (homeLat != null && homeLng != null) {
            return LocationPayload(
                lat = homeLat,
                lng = homeLng,
                accuracyMeters = null,
                source = LocationSource.HomeAddress,
                capturedAt = Clock.System.now(),
                address = reverseGeocode(homeLat, homeLng),
            )
        }

        return LocationPayload(
            lat = 0.0,
            lng = 0.0,
            accuracyMeters = null,
            source = LocationSource.Unavailable,
            capturedAt = Clock.System.now(),
        )
    }

    /**
     * Resolves coordinates to a human-readable address so the planner is handed a real place name
     * instead of being left to (unreliably) reverse-geocode raw lat/lng itself. Best-effort: returns
     * null when the platform geocoder is absent, errors, or times out.
     */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        // Locale.getDefault(): the address is human-language for the model, not a numeric readout —
        // the documented exception to the Locale.US formatting rule.
        val geocoder = Geocoder(context, Locale.getDefault())
        return withTimeoutOrNull(REVERSE_GEOCODE_TIMEOUT.inWholeMilliseconds) {
            runCatching {
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                            override fun onError(errorMessage: String?) = cont.resume(null)
                        })
                    }
                } else {
                    @Suppress("DEPRECATION") // synchronous getFromLocation is the only option below API 33
                    withContext(Dispatchers.IO) { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }
                }
                address?.let(::formatAddress)
            }.onFailure { Log.w(TAG, "reverseGeocode failed", it) }.getOrNull()
        }
    }

    private fun formatAddress(address: Address): String? =
        address.getAddressLine(0)
            ?: listOfNotNull(
                address.locality ?: address.subAdminArea,
                address.adminArea,
                address.countryName,
            ).joinToString(", ").takeIf { it.isNotBlank() }

    companion object {
        private const val TAG = "LocationProvider"
        private const val MAX_ACCURACY_METERS = 500f
        // Looser cap for a balanced-power CURRENT fix: city-level accuracy is acceptable when the
        // alternative is a stale fix in the wrong city (commute origin only needs the right area).
        private const val COARSE_MAX_ACCURACY_METERS = 5000f
        private val REVERSE_GEOCODE_TIMEOUT = 5.seconds
    }

    private fun isRecent(location: Location, maxAge: kotlin.time.Duration): Boolean =
        (System.currentTimeMillis() - location.time) <= maxAge.inWholeMilliseconds

    private fun hasAnyLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitNullable(): T? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resume(null) }
            addOnCanceledListener { cont.cancel(CancellationException("Task cancelled")) }
        }
}
