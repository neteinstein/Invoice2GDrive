package org.neteinstein.snap2sheet.platform

import org.neteinstein.snap2sheet.platform.tr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.neteinstein.snap2sheet.data.local.AndroidAppContext
import org.neteinstein.snap2sheet.shared.R
import java.util.concurrent.atomic.AtomicInteger

private const val CHANNEL_ID = "invoice_saves"

private fun Context.canPostNotifications() =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private class AndroidNotifier(private val context: Context) : Notifier {
    private val nextId = AtomicInteger(1)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, tr("Invoice saves", "Registo de faturas"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = tr("When an invoice finishes saving to Google Sheets and Drive, or fails to.", "Quando uma fatura termina de ser guardada no Google Sheets e Drive, ou falha.")
                }
            )
        }
    }

    override fun notify(title: String, message: String) {
        if (!context.canPostNotifications()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fatura_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(nextId.getAndIncrement(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}

actual fun platformNotifier(): Notifier = AndroidNotifier(AndroidAppContext.instance)

private class AndroidNotificationPermissionState(initial: PermissionStatus, private val context: Context) : PermissionState {
    override var status by mutableStateOf(initial)
    var launch: () -> Unit = {}
    override fun request() = launch()
    override fun openSettings() {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    override val canOpenSettings: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}

@Composable
actual fun rememberNotificationPermissionState(): PermissionState {
    val context = LocalContext.current
    val state = remember {
        AndroidNotificationPermissionState(
            if (context.canPostNotifications()) PermissionStatus.GRANTED else PermissionStatus.NOT_DETERMINED,
            context,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }
    state.launch = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else state.status = PermissionStatus.GRANTED
    }
    LifecycleResumeEffect(state) {
        if (context.canPostNotifications()) state.status = PermissionStatus.GRANTED
        else if (state.status == PermissionStatus.GRANTED) state.status = PermissionStatus.DENIED
        onPauseOrDispose { }
    }
    return state
}
