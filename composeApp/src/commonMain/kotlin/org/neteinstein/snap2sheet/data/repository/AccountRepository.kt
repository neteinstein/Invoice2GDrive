package org.neteinstein.snap2sheet.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.neteinstein.snap2sheet.data.auth.AccessTokenProvider
import org.neteinstein.snap2sheet.data.auth.GoogleAuthProvider
import org.neteinstein.snap2sheet.data.auth.NotSignedInException
import org.neteinstein.snap2sheet.data.auth.OAuthClientSetup
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import org.neteinstein.snap2sheet.domain.model.GoogleAccount

private const val KEY_ACCOUNT = "account"
private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

/**
 * The signed-in Google account persisted across launches. Also the
 * app's [AccessTokenProvider]: the sheets gateway asks it for tokens, it asks the platform's
 * [GoogleAuthProvider].
 */
interface AccountRepository : AccessTokenProvider {
    val account: StateFlow<GoogleAccount?>

    /** False while there's no Google OAuth client ID for the platform. */
    val isGoogleSignInAvailable: Boolean

    /** Entering the OAuth client ID in the app, on platforms that need one (iOS, web); null on Android. */
    val oauthClientSetup: OAuthClientSetup?

    /** Runs Google sign-in and fetches the account's email/name. Throws with a user-facing message on failure. */
    suspend fun signInWithGoogle(): GoogleAccount

    suspend fun signOut()
}

class DefaultAccountRepository(
    private val store: KeyValueStore,
    private val auth: GoogleAuthProvider,
    private val http: HttpClient,
    private val json: Json,
) : AccountRepository {

    private val _account = MutableStateFlow(loadStoredAccount())
    override val account: StateFlow<GoogleAccount?> = _account.asStateFlow()

    override val isGoogleSignInAvailable: Boolean get() = auth.isConfigured

    override val oauthClientSetup: OAuthClientSetup? get() = auth.clientSetup

    override suspend fun signInWithGoogle(): GoogleAccount {
        val token = auth.signIn()
        val response = http.get(USERINFO_URL) { bearerAuth(token) }
        if (!response.status.isSuccess()) error("Signed in, but Google didn't return your profile (${response.status.value}).")
        val info: UserInfo = response.body()
        val email = info.email ?: error("Google didn't share your email address.")
        return GoogleAccount(email = email, initials = GoogleAccount.initialsFrom(info.name, email)).also(::setAccount)
    }

    override suspend fun signOut() {
        val wasSignedIn = _account.value != null
        setAccount(null)
        if (wasSignedIn) runCatching { auth.signOut() }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        if (_account.value == null) throw NotSignedInException("Sign in to Google first.")
        return auth.accessToken(forceRefresh)
    }

    /** Restores the persisted account; a leftover demo account from older builds is dropped so the user lands on sign-in. */
    private fun loadStoredAccount(): GoogleAccount? {
        val raw = store.getString(KEY_ACCOUNT) ?: return null
        val isLegacyDemo = runCatching {
            json.parseToJsonElement(raw).jsonObject["isDemo"]?.jsonPrimitive?.booleanOrNull == true
        }.getOrDefault(false)
        if (isLegacyDemo) {
            store.remove(KEY_ACCOUNT)
            return null
        }
        return runCatching { json.decodeFromString<GoogleAccount>(raw) }.getOrNull()
    }

    private fun setAccount(account: GoogleAccount?) {
        _account.value = account
        if (account == null) store.remove(KEY_ACCOUNT) else store.putString(KEY_ACCOUNT, json.encodeToString(account))
    }

    @Serializable
    private data class UserInfo(val email: String? = null, val name: String? = null)
}
