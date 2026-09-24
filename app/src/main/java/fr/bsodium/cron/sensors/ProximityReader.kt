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

/**
 * One-shot proximity read for [PlacementClassifier], not a continuous listener like
 * [AmbientLightReader] — a placement check only needs a single sample at screen-off or dismiss,
 * so registering/unregistering per-call avoids paying for the sensor all night.
 */
class ProximityReader(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    /** Null if there's no proximity sensor, or none of the sensors report within [timeout]. */
    suspend fun readCovered(timeout: Duration = DEFAULT_TIMEOUT): Boolean? {
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

    private companion object {
        val DEFAULT_TIMEOUT = 500.milliseconds
    }
}
