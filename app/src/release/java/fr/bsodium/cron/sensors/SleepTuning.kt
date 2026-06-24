package fr.bsodium.cron.sensors

import android.content.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** RELEASE variant — production sleep-detection timings; no debug overrides. */
object SleepTuning {
    @Suppress("UNUSED_PARAMETER")
    fun onsetThreshold(context: Context): Duration = 20.minutes

    @Suppress("UNUSED_PARAMETER")
    fun rearmThreshold(context: Context): Duration = 15.minutes
}
