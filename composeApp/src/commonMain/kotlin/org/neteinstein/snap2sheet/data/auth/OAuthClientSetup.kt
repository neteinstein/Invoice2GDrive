package org.neteinstein.snap2sheet.data.auth

import org.neteinstein.snap2sheet.data.local.KeyValueStore

private const val KEY_CLIENT_ID = "google_oauth_client_id"
private val CLIENT_ID_PATTERN = Regex("""^\d+-[a-z0-9]+\.apps\.googleusercontent\.com$""")

/**
 * The OAuth client ID a platform signs in with, on platforms that need one in code (iOS, web).
 * An ID entered in the app wins over the one baked into the build (`GoogleClientConfig`), so a
 * build made without one can't be stuck without sign-in: the user can paste their own client's ID on
 * the Connect Google screen.
 */
class OAuthClientSetup(
    private val store: KeyValueStore,
    private val buildTimeClientId: String,
    /** The application type to pick in Google Cloud Console, e.g. "Web application" or "iOS". */
    val clientType: String,
    /** What that client needs registered for this app, e.g. the site's origin or the bundle ID. */
    val registration: String,
) {
    val clientId: String
        get() = store.getString(KEY_CLIENT_ID)?.takeIf { it.isNotBlank() } ?: buildTimeClientId.trim()

    /** True when [clientId] was typed in the app rather than baked into the build. */
    val isEnteredInApp: Boolean get() = !store.getString(KEY_CLIENT_ID).isNullOrBlank()

    /** Saves [id], or goes back to the build's client ID when null. Throws on an ID that can't be one. */
    fun update(id: String?) {
        val trimmed = id?.trim()
        if (trimmed.isNullOrEmpty()) {
            store.remove(KEY_CLIENT_ID)
            return
        }
        require(isValid(trimmed)) { "That doesn't look like an OAuth client ID. It ends in .apps.googleusercontent.com." }
        store.putString(KEY_CLIENT_ID, trimmed)
    }

    companion object {
        fun isValid(id: String): Boolean = CLIENT_ID_PATTERN.matches(id.trim())
    }
}
