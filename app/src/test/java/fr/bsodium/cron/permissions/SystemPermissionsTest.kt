package fr.bsodium.cron.permissions

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Thin wrapper around Android permission checks — minimal smoke coverage confirming the boolean
 * reflects the granted/revoked state, not exhaustive per-SDK-level branching.
 */
@RunWith(RobolectricTestRunner::class)
class SystemPermissionsTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun hasForegroundLocation_is_false_when_nothing_granted() {
        assertFalse(SystemPermissions.hasForegroundLocation(context))
    }

    @Test
    fun hasForegroundLocation_is_true_once_fine_location_is_granted() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(SystemPermissions.hasForegroundLocation(context))
    }

    @Test
    fun hasForegroundLocation_is_true_with_only_coarse_location_granted() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(SystemPermissions.hasForegroundLocation(context))
    }

    @Test
    fun batteryOptimizationIntent_targets_this_package() {
        val intent = SystemPermissions.batteryOptimizationIntent(context)
        assertTrue(intent.data.toString().contains(context.packageName))
    }

    @Test
    fun exactAlarmSettingsIntent_targets_this_package() {
        val intent = SystemPermissions.exactAlarmSettingsIntent(context)
        assertTrue(intent.data.toString().contains(context.packageName))
    }
}
