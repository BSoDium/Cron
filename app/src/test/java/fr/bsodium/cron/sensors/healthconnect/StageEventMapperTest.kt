package fr.bsodium.cron.sensors.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class StageEventMapperTest {

    private val t0 = Fixtures.T0

    private fun stage(
        start: Instant,
        end: Instant,
        type: Int = SleepSessionRecord.STAGE_TYPE_LIGHT,
    ) = SleepSessionRecord.Stage(
        startTime = start.toJavaInstant(),
        endTime = end.toJavaInstant(),
        stage = type,
    )

    private fun map(stages: List<SleepSessionRecord.Stage>, seenThrough: Instant) =
        StageEventMapper.newStageEvents(
            stages = stages,
            source = "com.garmin.android.apps.connectmobile",
            ownPackage = "fr.bsodium.cron",
            seenThrough = seenThrough,
        )

    private val firstPollStages = listOf(
        stage(t0, t0 + 30.minutes, SleepSessionRecord.STAGE_TYPE_LIGHT),
        stage(t0 + 30.minutes, t0 + 60.minutes, SleepSessionRecord.STAGE_TYPE_DEEP),
        stage(t0 + 60.minutes, t0 + 90.minutes, SleepSessionRecord.STAGE_TYPE_REM),
    )

    @Test
    fun first_poll_emits_every_segment_past_the_cutoff() {
        val events = map(firstPollStages, seenThrough = t0 - 15.minutes)
        assertEquals(3, events.size)
        assertEquals(
            listOf(SleepStage.Light, SleepStage.Deep, SleepStage.Rem),
            events.map { (it.data as EventData.HcStageUpdate).stage },
        )
    }

    @Test
    fun second_poll_seeing_the_same_stages_emits_nothing() {
        val firstPoll = map(firstPollStages, seenThrough = t0 - 15.minutes)
        val checkpoint = firstPoll.maxOf { it.timestamp }

        val secondPoll = map(firstPollStages, seenThrough = checkpoint)

        assertTrue(secondPoll.isEmpty())
    }

    @Test
    fun poll_with_one_new_stage_emits_only_that_stage() {
        val firstPoll = map(firstPollStages, seenThrough = t0 - 15.minutes)
        val checkpoint = firstPoll.maxOf { it.timestamp }
        val newStage = stage(t0 + 90.minutes, t0 + 120.minutes, SleepSessionRecord.STAGE_TYPE_DEEP)

        val secondPoll = map(firstPollStages + newStage, seenThrough = checkpoint)

        assertEquals(1, secondPoll.size)
        val data = secondPoll.single().data as EventData.HcStageUpdate
        assertEquals(SleepStage.Deep, data.stage)
        assertEquals(t0 + 90.minutes, data.recordStart)
        assertEquals(t0 + 120.minutes, data.recordEnd)
    }

    @Test
    fun segment_ending_exactly_at_the_checkpoint_is_already_seen() {
        val events = map(
            listOf(stage(t0, t0 + 30.minutes)),
            seenThrough = t0 + 30.minutes,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun emitted_event_carries_trigger_timestamp_and_confidence() {
        val event = map(
            listOf(stage(t0, t0 + 30.minutes)),
            seenThrough = t0,
        ).single()

        assertEquals(TriggerType.HcStageUpdate, event.trigger)
        assertEquals(t0 + 30.minutes, event.timestamp)
        val data = event.data as EventData.HcStageUpdate
        assertEquals(SignalConfidence.High, data.confidence)
        assertEquals("com.garmin.android.apps.connectmobile", data.source)
    }

    @Test
    fun unmapped_stage_types_never_emit() {
        val events = map(
            listOf(
                stage(t0, t0 + 30.minutes, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
                stage(t0 + 30.minutes, t0 + 60.minutes, SleepSessionRecord.STAGE_TYPE_UNKNOWN),
            ),
            seenThrough = t0 - 15.minutes,
        )
        assertTrue(events.isEmpty())
    }
}
