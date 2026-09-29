package org.neteinstein.snap2sheet.data.auth

import org.neteinstein.snap2sheet.platform.tr

import android.accounts.Account
import io.ktor.client.HttpClient
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.neteinstein.snap2sheet.data.local.AndroidAppContext
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Identity Services' `AuthorizationClient`: the consent screen when scopes haven't been
 * granted yet, and silently-refreshed access tokens after that (Play services caches and renews
 * them, so there's no refresh token to store here). Needs an "Android" OAuth client in Google
 * Cloud Console for this package name and signing certificate's SHA-1 — no client ID in code.
 */
class AndroidGoogleAuthProvider : GoogleAuthProvider {

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(GoogleScopes.ALL.map(::Scope))
        .build()

    private var lastToken: String? = null
    private var grantedEmail: String? = null

    override val isConfigured: Boolean = true

    override suspend fun signIn(): String {
        val activity = AndroidAppContext.currentActivity ?: error(tr("Open the app to sign in.", "Abra a app para iniciar sessão."))
        val client = Identity.getAuthorizationClient(activity)
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw IllegalStateException(describe(e), e)
        }
        val granted = if (result.hasResolution()) resolve(activity, result) else result
        grantedEmail = granted.toGoogleSignInAccount()?.email
        return granted.accessToken?.also { lastToken = it } ?: error(tr("Google didn't return an access token.", "O Google não devolveu um token de acesso."))
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        val client = Identity.getAuthorizationClient(AndroidAppContext.instance)
        if (forceRefresh) {
            lastToken?.let { stale -> runCatching { client.clearToken(ClearTokenRequest.builder().setToken(stale).build()).await() } }
            lastToken = null
        }
        val result = try {
            client.authorize(request).await()
        } catch (e: ApiException) {
            throw NotSignedInException(describe(e))
        }
        if (result.hasResolution()) throw NotSignedInException()
        return result.accessToken?.also { lastToken = it } ?: throw NotSignedInException()
    }

    override suspend fun signOut() {
        val client = Identity.getAuthorizationClient(AndroidAppContext.instance)
        lastToken?.let { runCatching { client.clearToken(ClearTokenRequest.builder().setToken(it).build()).await() } }
        grantedEmail?.let { email ->
            runCatching {
                client.revokeAccess(
                    RevokeAccessRequest.builder()
                        .setAccount(Account(email, "com.google"))
                        .setScopes(GoogleScopes.ALL.map(::Scope))
                        .build()
                ).await()
            }
        }
        lastToken = null
        grantedEmail = null
    }

    /** Launches Google's consent/account-picker UI and waits for the user's answer. */
    private suspend fun resolve(activity: ComponentActivity, pending: AuthorizationResult): AuthorizationResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                lateinit var launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
                launcher = activity.activityResultRegistry.register(
                    "fatura-google-authorization",
                    ActivityResultContracts.StartIntentSenderForResult(),
                ) { activityResult ->
                    launcher.unregister()
                    val data = activityResult.data
                    if (activityResult.resultCode != Activity.RESULT_OK || data == null) {
                        continuation.resumeWithException(IllegalStateException(tr("Sign-in was cancelled.", "O início de sessão foi cancelado.")))
                        return@register
                    }
                    try {
                        continuation.resume(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data))
                    } catch (e: ApiException) {
                        continuation.resumeWithException(IllegalStateException(describe(e), e))
                    }
                }
                continuation.invokeOnCancellation { launcher.unregister() }
                val intent = pending.pendingIntent ?: run {
                    launcher.unregister()
                    continuation.resumeWithException(IllegalStateException(tr("Google sign-in couldn't start.", "Não foi possível iniciar o início de sessão Google.")))
                    return@suspendCancellableCoroutine
                }
                launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
            }
        }

    private fun describe(e: ApiException): String = when (e.statusCode) {
        CommonStatusCodes.CANCELED -> tr("Sign-in was cancelled.", "O início de sessão foi cancelado.")
        CommonStatusCodes.NETWORK_ERROR -> tr("No connection. Check your network and try again.", "Sem ligação. Verifique a rede e tente novamente.")
        CommonStatusCodes.DEVELOPER_ERROR ->
            tr("This build isn't registered with Google (OAuth client for this package/SHA-1 missing).", "Esta versão não está registada no Google (falta o cliente OAuth para este pacote/SHA-1).")
        else -> tr("Google sign-in failed (${CommonStatusCodes.getStatusCodeString(e.statusCode)}).", "O início de sessão Google falhou (${CommonStatusCodes.getStatusCodeString(e.statusCode)}).")
    }
}

actual fun platformGoogleAuthProvider(http: HttpClient, store: KeyValueStore): GoogleAuthProvider = AndroidGoogleAuthProvider()
