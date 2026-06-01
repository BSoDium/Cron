package fr.bsodium.cron.sensors.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_AWAKE
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_AWAKE_IN_BED
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_DEEP
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_LIGHT
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_OUT_OF_BED
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_REM
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_SLEEPING
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_UNKNOWN
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant

/**
 * Reads sleep stage data from Health Connect.
 *
 * Health Connect has no push API for sleep records, so this reader is
 * driven by [fr.bsodium.cron.worker.HealthConnectPollWorker] on a 15-min
 * periodic schedule. Each poll fetches new stage segments since the last
 * one and emits one [TriggerType.HcStageUpdate] event per segment.
 */
class SleepStageReader(private val context: Context) {

    enum class Availability { Available, ProviderUpdateRequired, NotInstalled }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.ProviderUpdateRequired
        else -> Availability.NotInstalled
    }

    /** Whether the sleep read permission is currently granted (false if HC is unavailable). */
    suspend fun hasSleepPermission(): Boolean {
        if (availability() != Availability.Available) return false
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    /**
     * Read all sleep-stage segments overlapping [start..now], emitting a
     * [SessionEvent] per stage segment to [emit]. Returns the latest
     * segment end so the caller can checkpoint progress.
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
            val confidence = DataOriginClassifier.classify(
                packageName = record.metadata.dataOrigin.packageName,
                ownPackage = ownPackage,
            )
            for (stage in record.stages) {
                val mapped = mapStage(stage.stage) ?: continue
                val startK = stage.startTime.toKotlinInstant()
                val endK = stage.endTime.toKotlinInstant()
                if (latestEnd == null || endK > latestEnd) latestEnd = endK
                emit(
                    SessionEvent(
                        trigger = TriggerType.HcStageUpdate,
                        timestamp = endK,
                        data = EventData.HcStageUpdate(
                            stage = mapped,
                            source = record.metadata.dataOrigin.packageName,
                            confidence = confidence,
                            recordStart = startK,
                            recordEnd = endK,
                        ),
                    )
                )
            }
        }
        return latestEnd
    }

    private fun mapStage(hcStage: Int): SleepStage? = when (hcStage) {
        STAGE_TYPE_AWAKE, STAGE_TYPE_AWAKE_IN_BED -> SleepStage.Awake
        STAGE_TYPE_LIGHT, STAGE_TYPE_SLEEPING -> SleepStage.Light
        STAGE_TYPE_DEEP -> SleepStage.Deep
        STAGE_TYPE_REM -> SleepStage.Rem
        STAGE_TYPE_OUT_OF_BED, STAGE_TYPE_UNKNOWN -> null
        else -> null
    }

    companion object {
        private const val TAG = "SleepStageReader"
    }
}
