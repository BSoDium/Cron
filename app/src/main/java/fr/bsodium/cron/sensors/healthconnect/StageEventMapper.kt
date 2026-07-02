package fr.bsodium.cron.sensors.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_AWAKE
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_AWAKE_IN_BED
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_DEEP
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_LIGHT
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_OUT_OF_BED
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_REM
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_SLEEPING
import androidx.health.connect.client.records.SleepSessionRecord.Companion.STAGE_TYPE_UNKNOWN
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant

/**
 * Pure mapping from Health Connect sleep-stage segments to [SessionEvent]s,
 * dropping segments a previous poll already emitted.
 *
 * Health Connect's `TimeRangeFilter` matches whole `SleepSessionRecord`s, and
 * one record spans the entire night — so every poll re-fetches every stage
 * seen so far. Filtering on `endTime > seenThrough` is what keeps a 15-minute
 * poll from re-appending the whole night to the session event log and
 * re-triggering paid AI turns on stale data.
 */
internal object StageEventMapper {

    fun newStageEvents(
        stages: List<SleepSessionRecord.Stage>,
        source: String,
        ownPackage: String,
        seenThrough: Instant,
    ): List<SessionEvent> {
        val confidence = DataOriginClassifier.classify(packageName = source, ownPackage = ownPackage)
        return stages.mapNotNull { stage ->
            val mapped = mapStage(stage.stage) ?: return@mapNotNull null
            val end = stage.endTime.toKotlinInstant()
            if (end <= seenThrough) return@mapNotNull null
            SessionEvent(
                trigger = TriggerType.HcStageUpdate,
                timestamp = end,
                data = EventData.HcStageUpdate(
                    stage = mapped,
                    source = source,
                    confidence = confidence,
                    recordStart = stage.startTime.toKotlinInstant(),
                    recordEnd = end,
                ),
            )
        }
    }

    private fun mapStage(hcStage: Int): SleepStage? = when (hcStage) {
        STAGE_TYPE_AWAKE, STAGE_TYPE_AWAKE_IN_BED -> SleepStage.Awake
        STAGE_TYPE_LIGHT, STAGE_TYPE_SLEEPING -> SleepStage.Light
        STAGE_TYPE_DEEP -> SleepStage.Deep
        STAGE_TYPE_REM -> SleepStage.Rem
        STAGE_TYPE_OUT_OF_BED, STAGE_TYPE_UNKNOWN -> null
        else -> null // HC stage constants are an open int set; unknown types carry no signal
    }
}
