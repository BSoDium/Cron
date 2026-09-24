package fr.bsodium.cron.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A classification of the movement seen during one [MotionProbe.sample] window. */
enum class MotionClassification { Unknown, Still, Handled, Walking }

/** The result of one [MotionProbe.sample] window — pure data, easy to log and to feed into a wake
 *  decision later (see docs/sleep-detection-architecture.md §1, Tier 1). */
data class MotionSummary(
    val sampleCount: Int,
    val peakDeltaG: Float,
    val variance: Float,
    val classification: MotionClassification,
)

/**
 * Opens a short, batched accelerometer window and summarizes the movement seen in it — the
 * evidence that tells "unlocked for 10s then walked away" apart from "unlocked for 10s then set
 * back down," without needing the unlock to be held open at all.
 *
 * Registered with a large `maxReportLatencyUs` so the hardware FIFO batches samples and wakes the
 * AP in bursts rather than continuously — this is a short, triggered window (opened by a Tier-0
 * signal like significant motion or an unlock), never an all-night listener.
 */
class MotionProbe(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Linear acceleration has gravity already removed; fall back to the raw accelerometer (and
    // subtract gravity ourselves) on devices without the software-composed sensor.
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val fallbackSensor: Sensor? =
        if (sensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null

    /** Samples for [window] (default 90s) and returns a summary. Empty/[MotionClassification.Unknown]
     *  if this device has no motion sensor at all. */
    suspend fun sample(window: Duration = DEFAULT_WINDOW): MotionSummary {
        val activeSensor = sensor ?: fallbackSensor ?: return MotionSummary(0, 0f, 0f, MotionClassification.Unknown)
        val isRaw = sensor == null
        val deltas = mutableListOf<Float>()
        withTimeoutOrNull(window) {
            suspendCancellableCoroutine<Nothing> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        deltas += deltaG(event.values, isRaw)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                sensorManager.registerListener(
                    listener,
                    activeSensor,
                    SAMPLING_PERIOD_US,
                    BATCH_LATENCY_US,
                )
            }
        }
        return summarize(deltas)
    }

    private fun deltaG(values: FloatArray, isRaw: Boolean): Float {
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        return if (isRaw) abs(magnitude - SensorManager.GRAVITY_EARTH) else magnitude
    }

    private fun summarize(deltas: List<Float>): MotionSummary {
        if (deltas.isEmpty()) return MotionSummary(0, 0f, 0f, MotionClassification.Unknown)
        val peak = deltas.max()
        val mean = deltas.average()
        val variance = deltas.sumOf { (it - mean) * (it - mean) }.toFloat() / deltas.size
        return MotionSummary(deltas.size, peak, variance, classify(deltas.size, peak, variance))
    }

    companion object {
        private val DEFAULT_WINDOW = 90.seconds
        /** 25Hz — enough to catch a footstep impact without an unreasonable sample count per window. */
        private const val SAMPLING_PERIOD_US = 40_000
        /** Batches samples in the hardware FIFO so the AP wakes in bursts, not continuously. */
        private const val BATCH_LATENCY_US = 10_000_000

        /** A firm pickup or jostle (e.g. the pocket false-positive, #97) — noticeable but a single event. */
        private const val HANDLED_PEAK_DELTA_G = 1.5f
        /** Footstep impacts read markedly larger than a handling jostle. Calibration knob. */
        private const val WALKING_PEAK_DELTA_G = 3.0f
        /** Walking is rhythmic — sustained variance, not one spike. Calibration knob. */
        private const val WALKING_VARIANCE = 2.0f

        /** Pure decision — unit-testable. */
        internal fun classify(sampleCount: Int, peakDeltaG: Float, variance: Float): MotionClassification = when {
            sampleCount == 0 -> MotionClassification.Unknown
            peakDeltaG >= WALKING_PEAK_DELTA_G && variance >= WALKING_VARIANCE -> MotionClassification.Walking
            peakDeltaG >= HANDLED_PEAK_DELTA_G -> MotionClassification.Handled
            else -> MotionClassification.Still
        }
    }
}
