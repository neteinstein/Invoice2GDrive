package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.js.Promise

@Suppress("UNUSED_PARAMETER")
private fun showJs(title: String, body: String): Unit = js("window.faturaNotify.show(title, body)")
private fun permissionJs(): String = js("window.faturaNotify.permission()")
private fun requestJs(): Promise<JsString> = js("window.faturaNotify.request()")

private class WebNotifier : Notifier {
    override fun notify(title: String, message: String) = showJs(title, message)
}

actual fun platformNotifier(): Notifier = WebNotifier()

private fun statusFrom(value: String) = when (value) {
    "granted" -> PermissionStatus.GRANTED
    "denied", "unsupported" -> PermissionStatus.DENIED
    else -> PermissionStatus.NOT_DETERMINED
}

private class WebNotificationPermissionState(private val scope: CoroutineScope) : PermissionState {
    override var status by mutableStateOf(statusFrom(permissionJs()))
    override fun request() {
        scope.launch { status = statusFrom(requestJs().await().toString()) }
    }
    override fun openSettings() = Unit
    override val canOpenSettings: Boolean = false
}

@Composable
actual fun rememberNotificationPermissionState(): PermissionState {
    val scope = rememberCoroutineScope()
    return remember { WebNotificationPermissionState(scope) }.also { state ->
        LaunchedEffect(state) { state.status = statusFrom(permissionJs()) }
    }
}
