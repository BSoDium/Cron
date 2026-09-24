package fr.bsodium.cron.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** Shared one-shot-sensor-window scaffolding for [ProximityReader]: registers [sensor] for up to
 *  [window], resolving as soon as [onEvent] returns a non-null result. Always unregisters the listener
 *  on completion or cancellation. See [sampleSensorWindow] for [MotionProbe]'s full-window variant. */
internal suspend fun <T> SensorManager.awaitSensorReading(
    sensor: Sensor,
    window: Duration,
    samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL,
    maxReportLatencyUs: Int = 0,
    onEvent: (SensorEvent) -> T?,
): T? = withTimeoutOrNull(window) {
    suspendCancellableCoroutine { cont ->
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val result = onEvent(event) ?: return
                unregisterListener(this)
                if (cont.isActive) cont.resumeWith(Result.success(result))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        cont.invokeOnCancellation { unregisterListener(listener) }
        registerListener(listener, sensor, samplingPeriodUs, maxReportLatencyUs)
    }
}

/** [MotionProbe]'s variant: registers [sensor] for the full [window], forwarding every reading to
 *  [onEvent] with no early exit — [MotionProbe] accumulates into its own state and only summarizes
 *  once the window elapses. Always unregisters the listener on completion or cancellation. */
internal suspend fun SensorManager.sampleSensorWindow(
    sensor: Sensor,
    window: Duration,
    samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL,
    maxReportLatencyUs: Int = 0,
    onEvent: (SensorEvent) -> Unit,
) {
    withTimeoutOrNull(window) {
        suspendCancellableCoroutine<Nothing> { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) = onEvent(event)
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            cont.invokeOnCancellation { unregisterListener(listener) }
            registerListener(listener, sensor, samplingPeriodUs, maxReportLatencyUs)
        }
    }
}
