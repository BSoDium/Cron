package fr.bsodium.cron.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import fr.bsodium.cron.BuildConfig

/**
 * Profile fields surfaced to the rest of the app after a successful Sign-in
 * with Google flow. We deliberately ignore the ID token itself — Cron does
 * not call any Google backend, the sign-in is purely a name+avatar source.
 */
data class GoogleProfile(
    val displayName: String?,
    val givenName: String?,
    val photoUrl: String?,
)

sealed class GoogleAuthResult {
    data class Success(val profile: GoogleProfile) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Misconfigured(val reason: String) : GoogleAuthResult()
    data class Failure(val throwable: Throwable) : GoogleAuthResult()
}

class GoogleAuthClient(private val context: Context) {

    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    suspend fun signIn(activityContext: Context): GoogleAuthResult {
        if (webClientId.isBlank()) {
            return GoogleAuthResult.Misconfigured(
                "GOOGLE_WEB_CLIENT_ID not set in local.properties"
            )
        }
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val manager = CredentialManager.create(context)
        return runCatching {
            val response = manager.getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthResult.Success(
                    GoogleProfile(
                        displayName = google.displayName?.takeIf { it.isNotBlank() }
                            ?: google.givenName?.takeIf { it.isNotBlank() },
                        givenName = google.givenName?.takeIf { it.isNotBlank() },
                        photoUrl = google.profilePictureUri?.toString(),
                    )
                )
            } else {
                GoogleAuthResult.Failure(
                    IllegalStateException("Unexpected credential type: ${credential::class.java}")
                )
            }
        }.getOrElse { t ->
            when (t) {
                is GetCredentialCancellationException -> GoogleAuthResult.Cancelled
                is GetCredentialException -> GoogleAuthResult.Failure(t)
                else -> GoogleAuthResult.Failure(t)
            }
        }
    }
}
