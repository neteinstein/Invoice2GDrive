package org.neteinstein.snap2sheet.platform

import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

@Suppress("UNUSED_PARAMETER")
private fun cropJs(base64: String, corners: String): Promise<JsString> = js("window.faturaPhoto.crop(base64, corners)")

actual suspend fun cropPerspective(bytes: ByteArray, corners: FloatArray): ByteArray? {
    val result = cropJs(Base64.encode(bytes), corners.joinToString(",")).await<JsString>().toString()
    return if (result.isEmpty()) null else Base64.decode(result)
}
