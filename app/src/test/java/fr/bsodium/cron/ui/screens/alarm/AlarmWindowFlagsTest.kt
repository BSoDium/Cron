package fr.bsodium.cron.ui.screens.alarm

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the lock-screen contract of [ALARM_WINDOW_FLAGS]. `FLAG_DISMISS_KEYGUARD` (and the
 * equivalent `KeyguardManager.requestDismissKeyguard`) puts the credential prompt over the alarm on a
 * secure device, so the dismiss control is unreachable (#177).
 */
class AlarmWindowFlagsTest {

    @Suppress("DEPRECATION")
    @Test
    fun alarm_window_shows_over_the_keyguard_and_keeps_the_screen_on() {
        assertFlagSet(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, "FLAG_SHOW_WHEN_LOCKED")
        assertFlagSet(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON, "FLAG_TURN_SCREEN_ON")
        assertFlagSet(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, "FLAG_KEEP_SCREEN_ON")
    }

    @Suppress("DEPRECATION")
    @Test
    fun alarm_window_never_asks_to_dismiss_the_keyguard() {
        assertEquals(
            "FLAG_DISMISS_KEYGUARD must stay unset — it surfaces the credential prompt over the alarm (#177)",
            0,
            ALARM_WINDOW_FLAGS and WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
    }

    private fun assertFlagSet(flag: Int, name: String) =
        assertEquals("$name must be set on the alarm window", flag, ALARM_WINDOW_FLAGS and flag)
}
