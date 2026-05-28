package fr.bsodium.cron.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime

private val Context.dataStore by preferencesDataStore(name = "cron_settings")

/**
 * Backing store for non-sensitive user preferences.
 *
 * Secrets (Anthropic API key) live separately in [SecureKeyStore].
 */
class SettingsRepository(private val context: Context) {

    val eveningTriggerLocalTime: Flow<LocalTime> = context.dataStore.data.map { prefs ->
        prefs.localTime(EVENING_TRIGGER, default = LocalTime(22, 0))
    }

    val hardLatestDefault: Flow<LocalTime> = context.dataStore.data.map { prefs ->
        prefs.localTime(HARD_LATEST_DEFAULT, default = LocalTime(10, 0))
    }

    val freeDayWakeStart: Flow<LocalTime> = context.dataStore.data.map { prefs ->
        prefs.localTime(FREE_DAY_WAKE_START, default = LocalTime(8, 0))
    }

    val freeDayWakeEnd: Flow<LocalTime> = context.dataStore.data.map { prefs ->
        prefs.localTime(FREE_DAY_WAKE_END, default = LocalTime(9, 30))
    }

    val commuteBufferMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[COMMUTE_BUFFER] ?: 15
    }

    val preparationBufferMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PREPARATION_BUFFER] ?: 15
    }

    val homeAddressLat: Flow<Double?> = context.dataStore.data.map { prefs ->
        prefs[HOME_LAT]?.toDoubleOrNull()
    }

    val homeAddressLng: Flow<Double?> = context.dataStore.data.map { prefs ->
        prefs[HOME_LNG]?.toDoubleOrNull()
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETE] ?: false
    }

    /** User-supplied display name shown in the home greeting. */
    val displayName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_NAME]?.takeIf { it.isNotBlank() }
    }

    /** Avatar URL captured from a Sign-in with Google flow. Null when not signed in. */
    val displayPhotoUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_PHOTO_URL]?.takeIf { it.isNotBlank() }
    }

    suspend fun setEveningTriggerLocalTime(time: LocalTime) =
        context.dataStore.edit { it[EVENING_TRIGGER] = time.toString() }

    suspend fun setHardLatestDefault(time: LocalTime) =
        context.dataStore.edit { it[HARD_LATEST_DEFAULT] = time.toString() }

    suspend fun setFreeDayWakeWindow(start: LocalTime, end: LocalTime) =
        context.dataStore.edit {
            it[FREE_DAY_WAKE_START] = start.toString()
            it[FREE_DAY_WAKE_END] = end.toString()
        }

    suspend fun setCommuteBufferMinutes(minutes: Int) =
        context.dataStore.edit { it[COMMUTE_BUFFER] = minutes }

    suspend fun setPreparationBufferMinutes(minutes: Int) =
        context.dataStore.edit { it[PREPARATION_BUFFER] = minutes }

    suspend fun setHomeAddress(lat: Double, lng: Double) =
        context.dataStore.edit {
            it[HOME_LAT] = lat.toString()
            it[HOME_LNG] = lng.toString()
        }

    suspend fun setOnboardingComplete() =
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = true }

    suspend fun setDisplayName(name: String) =
        context.dataStore.edit { it[DISPLAY_NAME] = name.trim() }

    suspend fun setDisplayPhotoUrl(url: String?) =
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(DISPLAY_PHOTO_URL)
            else it[DISPLAY_PHOTO_URL] = url
        }

    suspend fun clearDisplayProfile() =
        context.dataStore.edit {
            it.remove(DISPLAY_NAME)
            it.remove(DISPLAY_PHOTO_URL)
        }

    /** One-shot read for use from broadcast receivers and one-shot workers. */
    suspend fun currentEveningTriggerLocalTime(): LocalTime = eveningTriggerLocalTime.first()
    suspend fun currentHardLatestDefault(): LocalTime = hardLatestDefault.first()

    private fun Preferences.localTime(key: Preferences.Key<String>, default: LocalTime): LocalTime {
        val raw = this[key] ?: return default
        return runCatching { LocalTime.parse(raw) }.getOrDefault(default)
    }

    private companion object {
        val EVENING_TRIGGER = stringPreferencesKey("evening_trigger_local_time")
        val HARD_LATEST_DEFAULT = stringPreferencesKey("hard_latest_default")
        val FREE_DAY_WAKE_START = stringPreferencesKey("free_day_wake_start")
        val FREE_DAY_WAKE_END = stringPreferencesKey("free_day_wake_end")
        val COMMUTE_BUFFER = intPreferencesKey("commute_buffer_minutes")
        val PREPARATION_BUFFER = intPreferencesKey("preparation_buffer_minutes")
        val HOME_LAT = stringPreferencesKey("home_address_lat")
        val HOME_LNG = stringPreferencesKey("home_address_lng")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DISPLAY_PHOTO_URL = stringPreferencesKey("display_photo_url")
    }
}
