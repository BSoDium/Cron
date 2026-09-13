package fr.bsodium.cron.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class SecureKeyStoreTest {

    @Test
    fun succeeds_on_first_try_without_recovering() {
        var recovered = false
        val result = openWithRecovery(open = { "ok" }, recover = { recovered = true })

        assertEquals("ok", result)
        assertTrue(!recovered)
    }

    @Test
    fun recovers_once_from_a_decrypt_failure_and_returns_the_retried_value() {
        var attempts = 0
        var recoveredCause: Exception? = null
        val result = openWithRecovery(
            open = {
                attempts++
                if (attempts == 1) throw AEADBadTagException("corrupt keyset") else "recovered"
            },
            recover = { recoveredCause = it },
        )

        assertEquals("recovered", result)
        assertEquals(2, attempts)
        assertTrue(recoveredCause is AEADBadTagException)
    }

    @Test
    fun recovers_from_a_general_keyset_failure() {
        var attempts = 0
        val result = openWithRecovery(
            open = { if (++attempts == 1) throw GeneralSecurityException("bad keyset") else "recovered" },
            recover = {},
        )

        assertEquals("recovered", result)
    }

    @Test
    fun recovers_from_an_io_failure() {
        var attempts = 0
        val result = openWithRecovery(
            open = { if (++attempts == 1) throw IOException("file unusable") else "recovered" },
            recover = {},
        )

        assertEquals("recovered", result)
    }

    @Test(expected = IllegalStateException::class)
    fun does_not_swallow_an_unrelated_failure() {
        openWithRecovery<String>(open = { throw IllegalStateException("bug") }, recover = {})
    }
}
