package fr.bsodium.cron.location

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.settings.PollCheckpointStore
import fr.bsodium.cron.settings.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric grants no runtime permissions by default, so [LocationProvider.acquireForEveningPlan]
 * takes the `permission_denied` branch without ever touching FusedLocationProviderClient — this
 * exercises the fallback-tier ordering (stored last-known -> home address -> unavailable) in isolation.
 */
@RunWith(RobolectricTestRunner::class)
class LocationProviderFallbackTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val provider = LocationProvider(context)

    private fun settings(homeLat: Double? = null, homeLng: Double? = null): SettingsRepository {
        val settings = mockk<SettingsRepository>()
        every { settings.homeAddressLat } returns flowOf(homeLat)
        every { settings.homeAddressLng } returns flowOf(homeLng)
        return settings
    }

    @Test
    fun no_permission_and_no_fallback_data_yields_unavailable() = runTest {
        val checkpoints = mockk<PollCheckpointStore>()
        every { checkpoints.lastLocationLatLng() } returns null
        every { checkpoints.lastLocationFixAt() } returns null

        val result = provider.acquireForEveningPlan(checkpoints, settings())
        assertEquals(LocationSource.Unavailable, result.source)
        assertEquals(0.0, result.lat, 0.0)
        assertEquals(0.0, result.lng, 0.0)
        assertNull(result.accuracyMeters)
    }

    @Test
    fun no_permission_falls_back_to_stored_last_known_fix() = runTest {
        val checkpoints = mockk<PollCheckpointStore>()
        every { checkpoints.lastLocationLatLng() } returns (46.624 to 14.308)
        every { checkpoints.lastLocationFixAt() } returns fr.bsodium.cron.testutil.Fixtures.T0

        val result = provider.acquireForEveningPlan(checkpoints, settings())
        assertEquals(LocationSource.LastKnown, result.source)
        assertEquals(46.624, result.lat, 0.0001)
        assertEquals(14.308, result.lng, 0.0001)
    }

    @Test
    fun no_permission_and_no_stored_fix_falls_back_to_home_address() = runTest {
        val checkpoints = mockk<PollCheckpointStore>()
        every { checkpoints.lastLocationLatLng() } returns null
        every { checkpoints.lastLocationFixAt() } returns null

        val result = provider.acquireForEveningPlan(checkpoints, settings(homeLat = 48.85, homeLng = 2.35))
        assertEquals(LocationSource.HomeAddress, result.source)
        assertEquals(48.85, result.lat, 0.0001)
        assertEquals(2.35, result.lng, 0.0001)
    }

    @Test
    fun stored_last_known_fix_is_preferred_over_home_address() = runTest {
        val checkpoints = mockk<PollCheckpointStore>()
        every { checkpoints.lastLocationLatLng() } returns (46.624 to 14.308)
        every { checkpoints.lastLocationFixAt() } returns fr.bsodium.cron.testutil.Fixtures.T0

        val result = provider.acquireForEveningPlan(checkpoints, settings(homeLat = 48.85, homeLng = 2.35))
        assertEquals(LocationSource.LastKnown, result.source)
    }
}
