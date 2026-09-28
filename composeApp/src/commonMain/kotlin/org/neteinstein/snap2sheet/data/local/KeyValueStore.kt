package org.neteinstein.snap2sheet.data.local

/**
 * A minimal, synchronous string key-value store — preferences, plus the invoice history and
 * spreadsheet list serialized as JSON (small enough that a database would be overkill).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** SharedPreferences on Android, NSUserDefaults on iOS, localStorage on the web. */
expect fun platformKeyValueStore(): KeyValueStore
