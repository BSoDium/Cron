package fr.bsodium.cron.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Keeps the latest ambient-light reading so sleep-onset detection can require a *dark* room — the
 * signal that tells a phone idle on a lit couch apart from one on a bedside table (see Google Clock's
 * "motionless in a dark room"). Push-based: registers a low-power [Sensor.TYPE_LIGHT] listener for the
 * service's lifetime.
 */
class AmbientLightReader(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    @Volatile
    private var latestLux: Float? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            latestLux = event.values.firstOrNull()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (lightSensor == null) {
            Log.w(TAG, "No ambient-light sensor — dark gate will pass by default")
            return
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    /** True when the room is dark — or when there's no light sensor (don't block onset on missing hardware). */
    fun isDark(): Boolean = (latestLux ?: return true) < DARK_LUX_THRESHOLD

    companion object {
        private const val TAG = "AmbientLightReader"
        /** A dark bedroom reads only a few lux; dim indoor lighting is tens+. Calibration knob. */
        private const val DARK_LUX_THRESHOLD = 10f
    }
}
