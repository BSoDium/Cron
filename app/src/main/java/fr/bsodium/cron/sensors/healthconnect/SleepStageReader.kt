package fr.bsodium.cron.sensors.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import fr.bsodium.cron.session.model.SessionEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

/**
 * Reads sleep stage data from Health Connect.
 *
 * Health Connect has no push API for sleep records, so this reader is
 * driven by [fr.bsodium.cron.worker.HealthConnectPollWorker] on a 15-min
 * periodic schedule. Each poll fetches new stage segments since the last
 * one and emits one `HcStageUpdate` event per segment.
 */
class SleepStageReader(private val context: Context) {

    enum class Availability { Available, ProviderUpdateRequired, NotInstalled }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.ProviderUpdateRequired
        else -> Availability.NotInstalled // covers SDK_UNAVAILABLE and any future status the SDK adds
    }

    /** Whether the sleep read permission is currently granted (false if HC is unavailable). */
    suspend fun hasSleepPermission(): Boolean {
        if (availability() != Availability.Available) return false
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    /**
     * Emit a [SessionEvent] per sleep-stage segment that ended after [start].
     * The record query fetches everything overlapping [start..now] (a session
     * record spans the whole night), so segments already seen by a previous
     * poll are dropped by [StageEventMapper] before they reach [emit].
     * Returns the latest emitted segment end so the caller can checkpoint
     * progress, or null when nothing new was found.
     */
    suspend fun readSince(
        start: Instant,
        emit: suspend (SessionEvent) -> Unit,
    ): Instant? {
        if (availability() != Availability.Available) {
            Log.d(TAG, "Health Connect not available; skipping read")
            return null
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(requiredPermissions)) {
            Log.d(TAG, "Sleep permission not granted; skipping read")
            return null
        }

        val response = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start.toJavaInstant(), Clock.System.now().toJavaInstant()),
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read sleep sessions", t)
            return null
        }

        var latestEnd: Instant? = null
        val ownPackage = context.packageName

        for (record in response.records) {
            val events = StageEventMapper.newStageEvents(
                stages = record.stages,
                source = record.metadata.dataOrigin.packageName,
                ownPackage = ownPackage,
                seenThrough = start,
            )
            for (event in events) {
                if (latestEnd == null || event.timestamp > latestEnd) latestEnd = event.timestamp
                emit(event)
            }
        }
        return latestEnd
    }

    companion object {
        private const val TAG = "SleepStageReader"
    }
}
