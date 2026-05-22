package fr.bsodium.cron.sensors.healthconnect

import fr.bsodium.cron.session.model.SignalConfidence

/**
 * Maps a Health Connect record's `dataOrigin.packageName` to a confidence
 * level. Records from real wearables (Garmin, Pixel Watch / Fitbit feed,
 * Samsung Health) carry [SignalConfidence.High]; everything else degrades
 * to medium or low.
 *
 * Used by [SleepStageReader] and surfaced to the AI in the event payload.
 */
object DataOriginClassifier {

    private val HIGH_CONFIDENCE_PACKAGES = setOf(
        "com.garmin.android.apps.connectmobile",
        "com.google.android.apps.fitness", // Pixel Watch + Fitbit feed
        "com.samsung.health",
        "com.samsung.android.shealth",
        "com.fitbit.FitbitMobile",
        "com.fitbit",
    )

    fun classify(packageName: String, ownPackage: String): SignalConfidence = when {
        packageName == ownPackage -> SignalConfidence.Low
        HIGH_CONFIDENCE_PACKAGES.any { packageName.startsWith(it) } -> SignalConfidence.High
        else -> SignalConfidence.Medium
    }
}
