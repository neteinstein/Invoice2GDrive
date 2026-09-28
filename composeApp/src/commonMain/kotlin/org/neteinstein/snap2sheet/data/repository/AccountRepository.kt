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
import org.neteinstein.snap2sheet.data.auth.AccessTokenProvider
import org.neteinstein.snap2sheet.data.auth.GoogleAuthProvider
import org.neteinstein.snap2sheet.data.auth.NotSignedInException
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import org.neteinstein.snap2sheet.domain.model.GoogleAccount

private const val KEY_ACCOUNT = "account"
private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

/**
 * The signed-in Google account (or a local demo account), persisted across launches. Also the
 * app's [AccessTokenProvider]: the sheets gateway asks it for tokens, it asks the platform's
 * [GoogleAuthProvider].
 */
interface AccountRepository : AccessTokenProvider {
    val account: StateFlow<GoogleAccount?>

    /** False when this build has no Google OAuth client for the platform — only demo mode works then. */
    val isGoogleSignInAvailable: Boolean

    /** Runs Google sign-in and fetches the account's email/name. Throws with a user-facing message on failure. */
    suspend fun signInWithGoogle(): GoogleAccount

    /** Signs in to a local demo account whose spreadsheets live in memory. */
    fun startDemo(): GoogleAccount

    suspend fun signOut()
}

class DefaultAccountRepository(
    private val store: KeyValueStore,
    private val auth: GoogleAuthProvider,
    private val http: HttpClient,
    private val json: Json,
) : AccountRepository {

    private val _account = MutableStateFlow(
        store.getString(KEY_ACCOUNT)?.let { runCatching { json.decodeFromString<GoogleAccount>(it) }.getOrNull() }
    )
    override val account: StateFlow<GoogleAccount?> = _account.asStateFlow()

    override val isGoogleSignInAvailable: Boolean get() = auth.isConfigured

    override suspend fun signInWithGoogle(): GoogleAccount {
        val token = auth.signIn()
        val response = http.get(USERINFO_URL) { bearerAuth(token) }
        if (!response.status.isSuccess()) error("Signed in, but Google didn't return your profile (${response.status.value}).")
        val info: UserInfo = response.body()
        val email = info.email ?: error("Google didn't share your email address.")
        return GoogleAccount(email = email, initials = GoogleAccount.initialsFor(info.name, email)).also(::setAccount)
    }

    override fun startDemo(): GoogleAccount =
        GoogleAccount(email = "demo@fatura.app", initials = "DE", isDemo = true).also(::setAccount)

    override suspend fun signOut() {
        val wasGoogle = _account.value?.isDemo == false
        setAccount(null)
        if (wasGoogle) runCatching { auth.signOut() }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        if (_account.value?.isDemo != false) throw NotSignedInException("Sign in to Google first.")
        return auth.accessToken(forceRefresh)
    }

    private fun setAccount(account: GoogleAccount?) {
        _account.value = account
        if (account == null) store.remove(KEY_ACCOUNT) else store.putString(KEY_ACCOUNT, json.encodeToString(account))
    }

    @Serializable
    private data class UserInfo(val email: String? = null, val name: String? = null)
}
