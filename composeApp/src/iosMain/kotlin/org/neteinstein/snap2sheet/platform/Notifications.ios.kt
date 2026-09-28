package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Shows banners even while Fatura is in the foreground — the save may finish on another screen.
 * A class, not an `object`: Kotlin/Native can't have singleton objects subclass Objective-C classes.
 */
private class ForegroundPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList)
    }
}

private class IosNotifier : Notifier {
    // The notification center only holds its delegate weakly.
    private val presenter = ForegroundPresenter()
    private val center = UNUserNotificationCenter.currentNotificationCenter().also { it.delegate = presenter }

    override fun notify(title: String, message: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
            setSound(UNNotificationSound.defaultSound)
        }
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(NSUUID().UUIDString, content, trigger = null),
            withCompletionHandler = null,
        )
    }
}

actual fun platformNotifier(): Notifier = IosNotifier()

private class IosNotificationPermissionState : PermissionState {
    override var status by mutableStateOf(PermissionStatus.NOT_DETERMINED)

    fun refresh() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val next = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> PermissionStatus.GRANTED
                UNAuthorizationStatusDenied -> PermissionStatus.DENIED
                else -> PermissionStatus.NOT_DETERMINED
            }
            dispatch_async(dispatch_get_main_queue()) { status = next }
        }
    }

    override fun request() {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
                }
            }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override val canOpenSettings: Boolean = true
}

@Composable
actual fun rememberNotificationPermissionState(): PermissionState {
    val state = remember { IosNotificationPermissionState() }
    LaunchedEffect(state) { state.refresh() }
    return state
}
