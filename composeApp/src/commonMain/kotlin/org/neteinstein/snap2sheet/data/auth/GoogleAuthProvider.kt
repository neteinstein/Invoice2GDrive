package org.neteinstein.snap2sheet.data.auth

import io.ktor.client.HttpClient
import org.neteinstein.snap2sheet.data.local.KeyValueStore

/** OAuth scopes requested at sign-in — mirrored by the two bullets on the Connect Google screen. */
object GoogleScopes {
    /** Read and append rows, create spreadsheets. */
    const val SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"

    /**
     * List spreadsheets and folders by name, and upload invoice photos into a folder the user
     * picked. The narrower `drive.file` scope only reaches files the app created itself, which
     * rules out saving into an existing folder.
     */
    const val DRIVE = "https://www.googleapis.com/auth/drive"

    const val EMAIL = "email"
    const val PROFILE = "profile"

    val ALL = listOf(SPREADSHEETS, DRIVE, EMAIL, PROFILE)
}

/** Thrown when an access token can't be obtained without showing Google's sign-in UI again. */
class NotSignedInException(message: String = "Your Google session expired. Sign in again.") : Exception(message)

/**
 * The platform's Google OAuth flow, reduced to "give me an access token for [GoogleScopes.ALL]".
 * Android uses Google Identity Services' `AuthorizationClient`; iOS runs an authorization-code +
 * PKCE flow in `ASWebAuthenticationSession`; the web uses the Google Identity Services token
 * client. See `GoogleClientConfig` for how the OAuth client IDs get into the build, and
 * [OAuthClientSetup] for entering one in the app instead.
 */
interface GoogleAuthProvider {
    /** False while there's no OAuth client ID for the platform — only demo mode is offered then. */
    val isConfigured: Boolean

    /**
     * Where the OAuth client ID comes from, on platforms that need one in code (iOS, web); null
     * where Google matches the app itself (Android).
     */
    val clientSetup: OAuthClientSetup? get() = null

    /** Runs the interactive flow (consent screen, account picker) and returns an access token. */
    suspend fun signIn(): String

    /**
     * An access token without user interaction — cached, refreshed or silently re-granted.
     * [forceRefresh] discards the cached token first (after the API rejected it with a 401).
     * @throws NotSignedInException when only [signIn] can get a new one.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String

    /** Forgets tokens and, where the platform allows, revokes the grant. */
    suspend fun signOut()
}

/** Anything that hands out Google access tokens — the sheets gateway only needs this much. */
fun interface AccessTokenProvider {
    suspend fun accessToken(forceRefresh: Boolean): String
}

expect fun platformGoogleAuthProvider(http: HttpClient, store: KeyValueStore): GoogleAuthProvider
