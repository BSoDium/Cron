package fr.bsodium.cron.worker

import android.content.Context
import android.location.LocationManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import fr.bsodium.cron.BuildConfig
import fr.bsodium.cron.engine.calendar.CalendarReaderImpl
import fr.bsodium.cron.engine.config.CronConfig
import fr.bsodium.cron.engine.orchestrator.CronOrchestrator
import fr.bsodium.cron.engine.scheduler.AlarmSchedulerImpl
import fr.bsodium.cron.engine.travel.GoogleRoutesTravelTimeProvider

/**
 * WorkManager worker that runs the Cron synchronization pass.
 *
 * Used for:
 * - Periodic background sync (every 3 hours, safety net)
 * - One-time sync triggered by [fr.bsodium.cron.receiver.CalendarChangeReceiver]
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val config = CronConfig.DEFAULT
        val calendarReader = CalendarReaderImpl(applicationContext.contentResolver, config)
        val alarmScheduler = AlarmSchedulerImpl(applicationContext)
        val travelTimeProvider = BuildConfig.GOOGLE_ROUTES_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { GoogleRoutesTravelTimeProvider(it) }
        val orchestrator = CronOrchestrator(
            calendarReader, alarmScheduler, config, travelTimeProvider
        )

        val (lat, lng) = getLastKnownLocation()

        return try {
            orchestrator.synchronize(originLat = lat, originLng = lng)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun getLastKnownLocation(): Pair<Double?, Double?> {
        return try {
            val locationManager = applicationContext
                .getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location =
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location != null) Pair(location.latitude, location.longitude) else Pair(null, null)
        } catch (_: SecurityException) {
            Pair(null, null)
        }
    }
}
