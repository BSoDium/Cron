package fr.bsodium.cron.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.settings.PollCheckpointStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * One-shot location acquisition for the evening_plan event.
 *
 * Fallback chain:
 *  1. `lastLocation` if recent (< 2h old) → source: gps
 *  2. `getCurrentLocation(BALANCED)` with 10s timeout → source: gps
 *  3. Stored last-known from [PollCheckpointStore] → source: last_known
 *  4. Registered home address from [SettingsRepository] → source: home_address
 *  5. Nothing → source: unavailable
 *
 * Every successful fix is written to [PollCheckpointStore] so that future
 * cold-start sessions have a fallback even if the backend is unreachable.
 */
class LocationProvider(private val context: Context) {

    suspend fun acquireForEveningPlan(
        checkpoints: PollCheckpointStore = PollCheckpointStore(context),
        settings: SettingsRepository = SettingsRepository(context),
    ): LocationPayload {
        if (!hasAnyLocationPermission()) {
            return fallback(checkpoints, settings, reason = "permission_denied")
        }

        val fused = LocationServices.getFusedLocationProviderClient(context)

        // Step 1: high-accuracy GPS fix — engages GPS chip for a precise fix.
        // Always tried first; if the result is coarser than MAX_ACCURACY_METERS
        // (cell-tower range), persistAndReturn returns null and we fall through.
        val fresh = withTimeoutOrNull(30.seconds.inWholeMilliseconds) {
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(30.seconds.inWholeMilliseconds)
                .build()
            runCatching {
                fused.getCurrentLocation(req, null).awaitNullable()
            }.getOrNull()
        }
        if (fresh != null) {
            persistAndReturn(fresh, LocationSource.Gps, checkpoints)?.let { return it }
        }

        // Step 2: fall back to lastLocation if fresh fix timed out
        val last = runCatching { fused.lastLocation.awaitNullable() }.getOrNull()
        if (last != null && isRecent(last, maxAge = 2.hours)) {
            persistAndReturn(last, LocationSource.Gps, checkpoints)?.let { return it }
        }

        return fallback(checkpoints, settings, reason = "no_fresh_fix")
    }

    private fun persistAndReturn(
        location: Location,
        source: LocationSource,
        checkpoints: PollCheckpointStore,
    ): LocationPayload? {
        // Reject fixes coarser than 500 m — those are cell-tower-only positions and
        // will misplace the user by kilometres (e.g. Ivry → Nation/Bastille).
        if (location.accuracy > MAX_ACCURACY_METERS) return null
        val capturedAt = Instant.fromEpochMilliseconds(location.time)
        checkpoints.setLastLocationFix(location.latitude, location.longitude, capturedAt)
        return LocationPayload(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = location.accuracy.takeIf { it > 0f },
            source = source,
            capturedAt = capturedAt,
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

    companion object {
        private const val MAX_ACCURACY_METERS = 500f
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
