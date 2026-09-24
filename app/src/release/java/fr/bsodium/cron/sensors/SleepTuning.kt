package fr.bsodium.cron.sensors

import android.content.Context
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** RELEASE variant — production sleep-detection timings; no debug overrides. */
object SleepTuning {
    @Suppress("UNUSED_PARAMETER")
    fun onsetThreshold(context: Context): Duration = 20.minutes

    @Suppress("UNUSED_PARAMETER")
    fun rearmThreshold(context: Context): Duration = 15.minutes

    @Suppress("UNUSED_PARAMETER")
    fun outOfBedConfirmThreshold(context: Context): Duration = 90.seconds

    @Suppress("UNUSED_PARAMETER")
    fun sustainedMovementThreshold(context: Context): Duration = 10.minutes

    @Suppress("UNUSED_PARAMETER")
    fun staleLocationThreshold(context: Context): Duration = 4.hours

    @Suppress("UNUSED_PARAMETER")
    fun motionProbeWindow(context: Context): Duration = 90.seconds

    @Suppress("UNUSED_PARAMETER")
    fun onsetRecheckInterval(context: Context): Duration = 5.minutes
}
