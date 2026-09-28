package org.neteinstein.snap2sheet.data.auth

import io.ktor.client.HttpClient

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
 * client. See `GoogleClientConfig` for how the OAuth client IDs get into the build.
 */
interface GoogleAuthProvider {
    /** False when this build has no OAuth client configured for the platform — only demo mode is offered then. */
    val isConfigured: Boolean

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

expect fun platformGoogleAuthProvider(http: HttpClient): GoogleAuthProvider
