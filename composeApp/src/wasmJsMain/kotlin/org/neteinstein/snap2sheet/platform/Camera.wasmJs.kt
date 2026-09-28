package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.Promise

private fun scanJs(): Promise<JsString> = js("window.faturaScanner.scan()")
private fun permissionJs(): Promise<JsString> = js("window.faturaScanner.permission()")
private fun requestPermissionJs(): Promise<JsString> = js("window.faturaScanner.requestPermission()")

private fun permissionFrom(state: String) = when (state) {
    "granted" -> PermissionStatus.GRANTED
    "denied" -> PermissionStatus.DENIED
    else -> PermissionStatus.NOT_DETERMINED
}

private class WebCameraPermissionState(private val scope: CoroutineScope) : PermissionState {
    override var status by mutableStateOf(PermissionStatus.NOT_DETERMINED)

    suspend fun refresh() {
        status = permissionFrom(permissionJs().await().toString())
    }

    override fun request() {
        scope.launch { status = permissionFrom(requestPermissionJs().await().toString()) }
    }

    /** Browsers keep per-site permissions behind the address bar's site settings; there's no URL for them. */
    override fun openSettings() = Unit

    override val canOpenSettings: Boolean = false
}

@Composable
actual fun rememberCameraPermissionState(): PermissionState {
    val scope = rememberCoroutineScope()
    val state = remember { WebCameraPermissionState(scope) }
    LaunchedEffect(state) { state.refresh() }
    return state
}

actual val hasInlineCameraPreview: Boolean = false

@Serializable
private data class ScanResult(val text: String? = null, val cancelled: Boolean = false)

private val scanJson = Json { ignoreUnknownKeys = true }

@Composable
actual fun QrScanner(onQrCode: (String) -> Unit, onClosed: () -> Unit, modifier: Modifier) {
    val currentOnQrCode by rememberUpdatedState(onQrCode)
    val currentOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(Unit) {
        val result = scanJson.decodeFromString<ScanResult>(scanJs().await().toString())
        val text = result.text
        if (text != null) currentOnQrCode(text) else currentOnClosed()
    }
}
