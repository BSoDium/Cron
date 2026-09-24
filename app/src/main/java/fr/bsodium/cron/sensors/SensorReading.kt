package fr.bsodium.cron.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** Shared one-shot-sensor-window scaffolding for [ProximityReader] and [MotionProbe]: registers
 *  [sensor] for up to [window], forwarding every reading to [onEvent] until it returns a non-null
 *  result (early exit -- [ProximityReader]'s use case) or the window elapses ([onEvent] always
 *  returning null -- [MotionProbe]'s use case, which accumulates into its own state instead of
 *  resolving here). Always unregisters the listener on completion or cancellation. */
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
