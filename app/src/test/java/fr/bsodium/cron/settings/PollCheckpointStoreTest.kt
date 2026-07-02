package fr.bsodium.cron.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PollCheckpointStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun missing_health_connect_checkpoint_returns_null() {
        assertNull(PollCheckpointStore(context).lastHealthConnectPoll())
    }

    @Test
    fun health_connect_checkpoint_round_trips() {
        val store = PollCheckpointStore(context)
        store.setLastHealthConnectPoll(Fixtures.T0)
        assertEquals(Fixtures.T0, store.lastHealthConnectPoll())
    }

    @Test
    fun missing_location_fix_returns_null_for_both_timestamp_and_latlng() {
        val store = PollCheckpointStore(context)
        assertNull(store.lastLocationFixAt())
        assertNull(store.lastLocationLatLng())
    }

    @Test
    fun location_fix_round_trips_timestamp_and_coordinates() {
        val store = PollCheckpointStore(context)
        store.setLastLocationFix(46.624, 14.308, Fixtures.T0)

        assertEquals(Fixtures.T0, store.lastLocationFixAt())
        val latLng = store.lastLocationLatLng()
        assertEquals(46.624, latLng?.first ?: Double.NaN, 0.001)
        assertEquals(14.308, latLng?.second ?: Double.NaN, 0.001)
    }

    @Test
    fun checkpoints_persist_across_store_instances() {
        PollCheckpointStore(context).setLastLocationFix(1.0, 2.0, Fixtures.T0)
        val reloaded = PollCheckpointStore(context)
        assertEquals(Fixtures.T0, reloaded.lastLocationFixAt())
    }
}
