package fr.bsodium.cron.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A one-shot proximity read, abstracted so [ScreenStateMonitor] can be tested with a fake instead
 *  of a real sensor -- see docs/sleep-detection-architecture.md, and [FakeProximitySource] in tests. */
interface ProximitySource {
    /** Null if there's no proximity sensor, or none of the sensors report within [timeout]. */
    suspend fun readCovered(timeout: Duration = 500.milliseconds): Boolean?
}

/**
 * One-shot proximity read for [PlacementClassifier], not a continuous listener like
 * [AmbientLightReader] — a placement check only needs a single sample at screen-off or dismiss,
 * so registering/unregistering per-call avoids paying for the sensor all night.
 */
class ProximityReader(context: Context) : ProximitySource {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    override suspend fun readCovered(timeout: Duration): Boolean? {
        val s = sensor ?: return null
        return withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val distance = event.values.firstOrNull() ?: return
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resumeWith(Result.success(distance < s.maximumRange))
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }
}
