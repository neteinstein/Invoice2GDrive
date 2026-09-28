package org.neteinstein.snap2sheet.data.local

import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

@Suppress("UNUSED_PARAMETER")
private fun putJs(key: String, base64: String): Promise<JsString> = js("window.faturaPhotos.put(key, base64)")

@Suppress("UNUSED_PARAMETER")
private fun getJs(key: String): Promise<JsString?> = js("window.faturaPhotos.get(key)")

@Suppress("UNUSED_PARAMETER")
private fun deleteJs(key: String): Promise<JsString> = js("window.faturaPhotos.remove(key)")

private fun randomKeyJs(): String = js("crypto.randomUUID()")

/** IndexedDB (via fatura-bridge.js) — localStorage's ~5 MB would fill up after a few photos. */
private class WasmPhotoStore : PhotoStore {
    override suspend fun save(bytes: ByteArray): String {
        val key = randomKeyJs()
        putJs(key, Base64.encode(bytes)).await()
        return key
    }

    override suspend fun load(key: String): ByteArray? = getJs(key).await()?.toString()?.let { Base64.decode(it) }

    override suspend fun delete(key: String) {
        deleteJs(key).await()
    }
}

actual fun platformPhotoStore(): PhotoStore = WasmPhotoStore()
