package fr.bsodium.cron.sensors.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaZoneOffset

/**
 * Writes Cron's own detected sleep window to Health Connect as a stage-less [SleepSessionRecord],
 * so Cron is a sleep-data source in its own right for users without a wearable app already
 * populating Health Connect (see [SleepStageReader], the read-side counterpart).
 */
class SleepSessionWriter(private val context: Context) {

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(SleepSessionRecord::class),
    )

    /** Whether the sleep write permission is currently granted (false if HC is unavailable). */
    suspend fun hasWritePermission(): Boolean {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return false
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    /**
     * Insert one [SleepSessionRecord] for [start]..[end]. [clientRecordId] (Cron's session id) makes
     * repeated writes for the same session idempotent — Health Connect upserts by client record id
     * rather than inserting a duplicate.
     */
    suspend fun write(start: Instant, end: Instant, timezone: TimeZone, clientRecordId: String): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(requiredPermissions)) {
            Log.d(TAG, "Sleep write permission not granted; skipping write")
            return false
        }
        val record = SleepSessionRecord(
            startTime = start.toJavaInstant(),
            startZoneOffset = timezone.offsetAt(start).toJavaZoneOffset(),
            endTime = end.toJavaInstant(),
            endZoneOffset = timezone.offsetAt(end).toJavaZoneOffset(),
            metadata = Metadata.autoRecorded(
                device = Device(type = Device.TYPE_PHONE),
                clientRecordId = clientRecordId,
            ),
        )
        return try {
            client.insertRecords(listOf(record))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write sleep session", t)
            false
        }
    }

    companion object {
        private const val TAG = "SleepSessionWriter"
    }
}
