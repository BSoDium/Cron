package fr.bsodium.cron.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Encrypted storage for sensitive credentials. Currently used for the user's
 * Anthropic API key.
 */
class SecureKeyStore(context: Context) {

    private val prefs = createPrefs(context.applicationContext)

    var anthropicApiKey: String?
        get() = prefs.getString(KEY_ANTHROPIC, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_ANTHROPIC) else putString(KEY_ANTHROPIC, value)
            }.apply()
        }

    fun hasAnthropicKey(): Boolean = !anthropicApiKey.isNullOrBlank()

    private fun createPrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return openWithRecovery(
            open = { openEncryptedPrefs(appContext, masterKey) },
            recover = {
                Log.w(TAG, "Failed to decrypt $FILE_NAME (likely restored without its Keystore key); wiping and retrying", it)
                appContext.deleteSharedPreferences(FILE_NAME)
            },
        )
    }

    private fun openEncryptedPrefs(appContext: Context, masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    companion object {
        private const val TAG = "SecureKeyStore"
        private const val FILE_NAME = "cron_secure"
        private const val KEY_ANTHROPIC = "anthropic_api_key"
    }
}

/** Retries [open] once via [recover] if it fails to decrypt (Auto Backup can restore the file without its Keystore key). */
internal inline fun <T> openWithRecovery(open: () -> T, recover: (Exception) -> Unit): T = try {
    open()
} catch (e: GeneralSecurityException) {
    recover(e)
    open()
} catch (e: IOException) {
    recover(e)
    open()
}
