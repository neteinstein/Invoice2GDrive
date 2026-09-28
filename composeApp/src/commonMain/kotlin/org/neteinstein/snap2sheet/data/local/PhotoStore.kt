package org.neteinstein.snap2sheet.data.local

/**
 * Invoice photos waiting to be uploaded. They're kept on the device — not in memory — so a save
 * interrupted by a crash, a reboot or a long time offline can still upload them later, and
 * they're deleted once they're in Drive. App-private files on Android/iOS, IndexedDB on the web.
 */
interface PhotoStore {
    /** Stores [bytes] and returns the key to [load] them with. */
    suspend fun save(bytes: ByteArray): String
    suspend fun load(key: String): ByteArray?
    suspend fun delete(key: String)
}

expect fun platformPhotoStore(): PhotoStore
