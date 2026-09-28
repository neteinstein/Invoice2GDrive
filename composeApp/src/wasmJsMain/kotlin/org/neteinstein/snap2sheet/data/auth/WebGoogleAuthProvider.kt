package org.neteinstein.snap2sheet.data.auth

import io.ktor.client.HttpClient
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import kotlin.js.Promise
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val SESSION_TOKEN_KEY = "google_access_token"

@Suppress("UNUSED_PARAMETER")
private fun requestTokenJs(clientId: String, scopes: String, prompt: String): Promise<JsString> =
    js("window.faturaAuth.requestToken(clientId, scopes, prompt)")

@Suppress("UNUSED_PARAMETER")
private fun revokeJs(token: String): Unit = js("window.faturaAuth.revoke(token)")

/**
 * The Google Identity Services token client (`accounts.google.com/gsi/client`, loaded by
 * index.html). Browsers get short-lived access tokens only — no refresh token — so an expired one
 * is re-requested with `prompt: ''`, which completes without UI while the Google session and the
 * grant are still valid. Needs a "Web application" OAuth client whose authorized JavaScript
 * origins include the deployed site (`google.webClientId`, see composeApp/build.gradle.kts, or
 * entered in the app — see [OAuthClientSetup]).
 * The token is kept in sessionStorage so a reload doesn't need another round-trip.
 */
class WebGoogleAuthProvider(
    override val clientSetup: OAuthClientSetup,
    private val clock: Clock = Clock.System,
) : GoogleAuthProvider {

    private val clientId: String get() = clientSetup.clientId

    private val json = Json { ignoreUnknownKeys = true }
    private var cached: CachedToken? = sessionStorage.getItem(SESSION_TOKEN_KEY)
        ?.let { runCatching { json.decodeFromString<CachedToken>(it) }.getOrNull() }

    /** Why the last interactive request failed, in words fit for the sign-in screen. */
    private var lastError: String? = null

    override val isConfigured: Boolean get() = clientId.isNotBlank()

    override suspend fun signIn(): String = request(prompt = "consent")
        ?: throw IllegalStateException(lastError ?: "Google sign-in failed.")

    override suspend fun accessToken(forceRefresh: Boolean): String {
        val token = cached
        if (!forceRefresh && token != null && clock.now().toEpochMilliseconds() < token.expiresAtMillis) return token.value
        return request(prompt = "") ?: throw NotSignedInException()
    }

    override suspend fun signOut() {
        cached?.let { revokeJs(it.value) }
        cached = null
        sessionStorage.removeItem(SESSION_TOKEN_KEY)
    }

    private suspend fun request(prompt: String): String? {
        check(isConfigured) { "Enter your Google OAuth client ID first." }
        val raw = requestTokenJs(clientId, GoogleScopes.ALL.joinToString(" "), prompt).await().toString()
        val response = json.decodeFromString<TokenResponse>(raw)
        val token = response.accessToken
        if (token == null) {
            lastError = when (response.error) {
                "popup_closed" -> "Sign-in was cancelled."
                "popup_failed_to_open" -> "Your browser blocked Google's sign-in popup. Allow popups for this site."
                "access_denied" -> "Access to Google was denied."
                "invalid_client", "init_failed" ->
                    "Google rejected this client ID. Check it, and that ${window.location.origin} is one of its authorized JavaScript origins."
                "gis_unavailable" -> "Couldn't load Google sign-in. Check your connection or ad blocker."
                else -> "Google sign-in failed (${response.error ?: "unknown error"})."
            }
            return null
        }
        val expiresAt = clock.now() + (response.expiresIn - 60).coerceAtLeast(0).seconds
        cached = CachedToken(token, expiresAt.toEpochMilliseconds()).also {
            sessionStorage.setItem(SESSION_TOKEN_KEY, json.encodeToString(it))
        }
        return token
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        val error: String? = null,
    )

    @Serializable
    private data class CachedToken(val value: String, val expiresAtMillis: Long)
}

actual fun platformGoogleAuthProvider(http: HttpClient, store: KeyValueStore): GoogleAuthProvider =
    WebGoogleAuthProvider(
        OAuthClientSetup(
            store = store,
            buildTimeClientId = GoogleClientConfig.WEB_CLIENT_ID,
            clientType = "Web application",
            registration = "Authorized JavaScript origin: " + window.location.origin,
        )
    )
