package fr.bsodium.cron.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.bsodium.cron.ai.BudgetStore
import fr.bsodium.cron.calendar.DEFAULT_RSVP_STATUSES
import fr.bsodium.cron.calendar.RsvpStatus
import fr.bsodium.cron.session.model.CommuteMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/**
 * The `preferencesDataStore` delegate in SettingsRepository.kt caches its Preferences in-process
 * for the JVM's lifetime (Gradle doesn't fork per test class), so every test in this class resets
 * the fields it can via setters in [setUp]. Fields with no "unset" API (home address, onboarding)
 * are covered separately in [SettingsRepositoryWriteOnceDefaultsTest].
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val repo = SettingsRepository(context)

    @Before
    fun setUp() = runTest {
        repo.setEveningTriggerLocalTime(LocalTime(20, 0))
        repo.setHardLatestDefault(LocalTime(10, 0))
        repo.setFreeDayWakeWindow(LocalTime(8, 0), LocalTime(9, 30))
        repo.setCommuteBufferMinutes(15)
        repo.setPreparationBufferMinutes(15)
        repo.setAllowedCommuteModes(CommuteMode.entries.toSet())
        repo.setAllowedRsvpStatuses(DEFAULT_RSVP_STATUSES)
        repo.setHapticsEnabled(true)
        repo.setCompactNavEnabled(false)
        repo.setAutoAlarmsEnabled(true)
        repo.setDisplayName("")
        repo.setUserInstructions("")
        repo.setDailyTokenLimit(BudgetStore.DEFAULT_DAILY_TOKEN_LIMIT)
    }

    private suspend fun <T> Flow<T>.first(): T {
        var result: T? = null
        test(timeout = 5.seconds) {
            result = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun defaults_match_documented_values() = runTest {
        assertEquals(LocalTime(20, 0), repo.eveningTriggerLocalTime.first())
        assertEquals(LocalTime(10, 0), repo.hardLatestDefault.first())
        assertEquals(LocalTime(8, 0), repo.freeDayWakeStart.first())
        assertEquals(LocalTime(9, 30), repo.freeDayWakeEnd.first())
        assertEquals(15, repo.commuteBufferMinutes.first())
        assertEquals(15, repo.preparationBufferMinutes.first())
        assertEquals(CommuteMode.entries.toSet(), repo.allowedCommuteModes.first())
        assertEquals(DEFAULT_RSVP_STATUSES, repo.allowedRsvpStatuses.first())
        assertTrue(repo.hapticsEnabled.first())
        assertFalse(repo.compactNavEnabled.first())
        assertTrue(repo.autoAlarmsEnabled.first())
    }

    @Test
    fun setEveningTriggerLocalTime_is_reflected_and_stamps_settingsUpdatedAt() = runTest {
        repo.setEveningTriggerLocalTime(LocalTime(21, 15))
        repo.eveningTriggerLocalTime.test(timeout = 5.seconds) {
            assertEquals(LocalTime(21, 15), awaitItem())
        }
        repo.settingsUpdatedAt.test(timeout = 5.seconds) {
            assertTrue(awaitItem() > 0L)
        }
    }

    @Test
    fun setHapticsEnabled_does_not_stamp_settingsUpdatedAt() = runTest {
        val before = repo.settingsUpdatedAt.first()
        repo.setHapticsEnabled(false)
        repo.hapticsEnabled.test(timeout = 5.seconds) { assertFalse(awaitItem()) }
        val after = repo.settingsUpdatedAt.first()
        assertEquals(before, after)
    }

    @Test
    fun allowedCommuteModes_round_trips_a_restricted_set() = runTest {
        repo.setAllowedCommuteModes(setOf(CommuteMode.Walk))
        repo.allowedCommuteModes.test(timeout = 5.seconds) {
            assertEquals(setOf(CommuteMode.Walk), awaitItem())
        }
    }

    @Test
    fun allowedCommuteModes_empty_set_reads_back_as_all_modes() = runTest {
        repo.setAllowedCommuteModes(emptySet())
        repo.allowedCommuteModes.test(timeout = 5.seconds) {
            assertEquals(CommuteMode.entries.toSet(), awaitItem())
        }
    }

    @Test
    fun allowedRsvpStatuses_round_trips_a_restricted_set() = runTest {
        repo.setAllowedRsvpStatuses(setOf(RsvpStatus.Accepted))
        repo.allowedRsvpStatuses.test(timeout = 5.seconds) {
            assertEquals(setOf(RsvpStatus.Accepted), awaitItem())
        }
    }

    @Test
    fun autoAlarmsEnabledNow_reflects_the_persisted_flag() = runTest {
        assertTrue(repo.autoAlarmsEnabledNow())
        repo.setAutoAlarmsEnabled(false)
        assertFalse(repo.autoAlarmsEnabledNow())
        repo.setAutoAlarmsEnabled(true)
        assertTrue(repo.autoAlarmsEnabledNow())
    }

    @Test
    fun displayName_blank_reads_back_as_null() = runTest {
        repo.setDisplayName("  ")
        repo.displayName.test(timeout = 5.seconds) { assertNull(awaitItem()) }
    }

    @Test
    fun displayName_is_trimmed_on_write() = runTest {
        repo.setDisplayName("  Elliot  ")
        repo.displayName.test(timeout = 5.seconds) { assertEquals("Elliot", awaitItem()) }
    }

    @Test
    fun userInstructions_blank_reads_back_as_null() = runTest {
        repo.setUserInstructions("   ")
        repo.userInstructions.test(timeout = 5.seconds) { assertNull(awaitItem()) }
        assertNull(repo.currentUserInstructions())
    }

    @Test
    fun currentDailyTokenLimit_reflects_persisted_value() = runTest {
        repo.setDailyTokenLimit(500)
        assertEquals(500, repo.currentDailyTokenLimit())
    }
}
