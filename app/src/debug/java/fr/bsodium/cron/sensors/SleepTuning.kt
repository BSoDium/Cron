package fr.bsodium.cron.sensors

import android.content.Context
import fr.bsodium.cron.debug.SleepTestPrefs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * DEBUG variant — collapses the sleep-onset/rearm thresholds to seconds when the developer
 * "fast onset" toggle is on, so the detection chain is testable without waiting 20 minutes.
 */
object SleepTuning {
    fun onsetThreshold(context: Context): Duration =
        if (SleepTestPrefs(context).fastOnset) 10.seconds else 20.minutes

    fun rearmThreshold(context: Context): Duration =
        if (SleepTestPrefs(context).fastOnset) 5.seconds else 15.minutes
}
