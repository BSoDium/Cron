package fr.bsodium.cron.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/**
 * homeAddressLat/Lng and onboardingComplete have no "unset" API, so — unlike
 * [SettingsRepositoryTest] — this class can't reset them between tests. [MethodSorters.NAME_ASCENDING]
 * keeps the default-reading test alphabetically first, before the mutating test runs.
 *
 * settingsUpdatedAt is deliberately not asserted to be zero here: it's a process-wide DataStore
 * singleton (see SettingsRepositoryTest's class doc) that any other plan-affecting test in the
 * suite may have already stamped, so "defaults to zero" isn't a safe cross-class assertion. Its
 * stamping behavior is covered by SettingsRepositoryTest instead.
 */
@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SettingsRepositoryWriteOnceDefaultsTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val repo = SettingsRepository(context)

    @Test
    fun a_defaults_are_unset() = runTest {
        repo.homeAddressLat.test(timeout = 5.seconds) { assertNull(awaitItem()) }
        repo.homeAddressLng.test(timeout = 5.seconds) { assertNull(awaitItem()) }
        repo.onboardingComplete.test(timeout = 5.seconds) { assertFalse(awaitItem()) }
    }

    @Test
    fun b_setOnboardingComplete_flips_the_flag() = runTest {
        repo.setOnboardingComplete()
        repo.onboardingComplete.test(timeout = 5.seconds) { assertTrue(awaitItem()) }
    }

    @Test
    fun c_homeAddress_round_trips_lat_lng() = runTest {
        repo.setHomeAddress(48.85, 2.35)
        repo.homeAddressLat.test(timeout = 5.seconds) { assertEquals(48.85, awaitItem()) }
        repo.homeAddressLng.test(timeout = 5.seconds) { assertEquals(2.35, awaitItem()) }
    }
}
