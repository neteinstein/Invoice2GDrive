package org.neteinstein.snap2sheet.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.parameters
import io.ktor.http.isSuccess
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
private const val KEYCHAIN_REFRESH_TOKEN = "google_refresh_token"

/**
 * Google's OAuth 2.0 flow for native apps (authorization code + PKCE, RFC 8252) run in an
 * `ASWebAuthenticationSession`, so no Google Sign-In SDK has to be linked into the Xcode project.
 * Needs an "iOS" OAuth client ID (`google.iosClientId`, see composeApp/build.gradle.kts, or
 * entered in the app — see [OAuthClientSetup]); its reversed form is the redirect URI scheme. The refresh token is kept in the Keychain.
 */
class IosGoogleAuthProvider(
    private val http: HttpClient,
    override val clientSetup: OAuthClientSetup,
    private val clock: Clock = Clock.System,
) : GoogleAuthProvider {

    private val clientId: String get() = clientSetup.clientId

    private val keychain = Keychain(service = "org.neteinstein.snap2sheet.google")
    private var accessToken: String? = null
    private var expiresAt: Instant = Instant.DISTANT_PAST

    // Held so the session and its presentation provider outlive the call that started them.
    private var session: ASWebAuthenticationSession? = null
    private val presentationProvider = PresentationProvider()

    override val isConfigured: Boolean get() = clientId.isNotBlank()

    /** `123-abc.apps.googleusercontent.com` → `com.googleusercontent.apps.123-abc`. */
    private val redirectScheme: String
        get() = "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")

    private val redirectUri: String get() = "$redirectScheme:/oauth2redirect"

    override suspend fun signIn(): String {
        check(isConfigured) { "Enter your Google OAuth client ID first." }
        val verifier = base64Url(randomBytes(48))
        val state = base64Url(randomBytes(16))
        val authUrl = NSURLComponents(string = AUTH_ENDPOINT).apply {
            queryItems = listOf(
                NSURLQueryItem(name = "client_id", value = clientId),
                NSURLQueryItem(name = "redirect_uri", value = redirectUri),
                NSURLQueryItem(name = "response_type", value = "code"),
                NSURLQueryItem(name = "scope", value = GoogleScopes.ALL.joinToString(" ")),
                NSURLQueryItem(name = "code_challenge", value = base64Url(sha256(verifier.encodeToByteArray()))),
                NSURLQueryItem(name = "code_challenge_method", value = "S256"),
                NSURLQueryItem(name = "state", value = state),
                NSURLQueryItem(name = "prompt", value = "select_account consent"),
            )
        }.URL ?: error("Couldn't build the Google sign-in URL.")

        val callback = authenticate(authUrl)
        val query = NSURLComponents(uRL = callback, resolvingAgainstBaseURL = false).queryItems
            .orEmpty().filterIsInstance<NSURLQueryItem>().associate { it.name to it.value }
        query["error"]?.let { error(if (it == "access_denied") "Access to Google was denied." else "Google sign-in failed ($it).") }
        check(query["state"] == state) { "Google sign-in was interrupted. Try again." }
        val code = query["code"] ?: error("Google didn't return an authorization code.")

        val tokens = requestTokens(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to redirectUri,
        ) ?: error("Google rejected the sign-in. Try again.")
        tokens.refreshToken?.let { keychain.set(KEYCHAIN_REFRESH_TOKEN, it) }
        return remember(tokens)
    }

    override suspend fun accessToken(forceRefresh: Boolean): String {
        val cached = accessToken
        if (!forceRefresh && cached != null && clock.now() < expiresAt) return cached
        val refreshToken = keychain.get(KEYCHAIN_REFRESH_TOKEN) ?: throw NotSignedInException()
        val tokens = requestTokens("grant_type" to "refresh_token", "refresh_token" to refreshToken)
            ?: run {
                // The grant was revoked or expired — only a new interactive sign-in can fix that.
                keychain.remove(KEYCHAIN_REFRESH_TOKEN)
                throw NotSignedInException()
            }
        return remember(tokens)
    }

    override suspend fun signOut() {
        val token = keychain.get(KEYCHAIN_REFRESH_TOKEN) ?: accessToken
        keychain.remove(KEYCHAIN_REFRESH_TOKEN)
        accessToken = null
        expiresAt = Instant.DISTANT_PAST
        if (token != null) runCatching { http.post(REVOKE_ENDPOINT) { parameter("token", token) } }
    }

    private fun remember(tokens: TokenResponse): String {
        accessToken = tokens.accessToken
        // Renew a minute early so a request never goes out with a token about to expire.
        expiresAt = clock.now() + (tokens.expiresIn - 60).coerceAtLeast(0).seconds
        return tokens.accessToken
    }

    /** Null when Google answers with an OAuth error (invalid/revoked grant); throws on network failure. */
    private suspend fun requestTokens(vararg fields: Pair<String, String>): TokenResponse? {
        val response = http.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = parameters {
                append("client_id", clientId)
                fields.forEach { (key, value) -> append(key, value) }
            },
        )
        return if (response.status.isSuccess()) response.body<TokenResponse>() else null
    }

    private suspend fun authenticate(url: NSURL): NSURL = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val authSession = ASWebAuthenticationSession(
                uRL = url,
                callbackURLScheme = redirectScheme,
            ) { callback, error ->
                session = null
                when {
                    callback != null -> continuation.resume(callback)
                    error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin ->
                        continuation.resumeWithException(IllegalStateException("Sign-in was cancelled."))
                    else -> continuation.resumeWithException(
                        IllegalStateException(error?.localizedDescription ?: "Google sign-in failed.")
                    )
                }
            }
            authSession.presentationContextProvider = presentationProvider
            authSession.prefersEphemeralWebBrowserSession = false
            session = authSession
            continuation.invokeOnCancellation { authSession.cancel() }
            if (!authSession.start()) {
                session = null
                continuation.resumeWithException(IllegalStateException("Couldn't open Google sign-in."))
            }
        }
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    private class PresentationProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
            UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}

private fun randomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    bytes.usePinned { pinned ->
        check(SecRandomCopyBytes(kSecRandomDefault, count.toULong(), pinned.addressOf(0)) == 0) { "No secure randomness available." }
    }
    return bytes
}

private fun sha256(input: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(if (input.isEmpty()) null else inputPinned.addressOf(0), input.size.toUInt(), digestPinned.addressOf(0))
        }
    }
    return digest.toByteArray()
}

private fun base64Url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

actual fun platformGoogleAuthProvider(http: HttpClient, store: KeyValueStore): GoogleAuthProvider =
    IosGoogleAuthProvider(
        http = http,
        clientSetup = OAuthClientSetup(
            store = store,
            buildTimeClientId = GoogleClientConfig.IOS_CLIENT_ID,
            clientType = "iOS",
            registration = "Bundle ID: " + (NSBundle.mainBundle.bundleIdentifier ?: "org.neteinstein.snap2sheet"),
        ),
    )
