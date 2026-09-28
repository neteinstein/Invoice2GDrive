package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.js.Promise

@Suppress("UNUSED_PARAMETER")
private fun pickJs(useCamera: Boolean): Promise<JsString> = js("window.faturaPhoto.pick(useCamera)")

@Serializable
private data class PickResult(val base64: String? = null, val mimeType: String? = null)

private val pickJson = Json { ignoreUnknownKeys = true }

@Composable
actual fun rememberPhotoPicker(onResult: (CapturedPhoto?) -> Unit): (PhotoSource) -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        { source ->
            scope.launch {
                val result = pickJson.decodeFromString<PickResult>(pickJs(source == PhotoSource.CAMERA).await().toString())
                val base64 = result.base64
                currentOnResult.value(
                    if (base64 == null) null else CapturedPhoto(Base64.decode(base64), result.mimeType ?: "image/jpeg")
                )
            }
        }
    }
}
