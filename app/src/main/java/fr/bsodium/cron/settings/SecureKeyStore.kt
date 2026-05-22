package fr.bsodium.cron.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for sensitive credentials. Currently used for the user's
 * Anthropic API key.
 */
class SecureKeyStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var anthropicApiKey: String?
        get() = prefs.getString(KEY_ANTHROPIC, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_ANTHROPIC) else putString(KEY_ANTHROPIC, value)
            }.apply()
        }

    fun hasAnthropicKey(): Boolean = !anthropicApiKey.isNullOrBlank()

    companion object {
        private const val FILE_NAME = "cron_secure"
        private const val KEY_ANTHROPIC = "anthropic_api_key"
    }
}
