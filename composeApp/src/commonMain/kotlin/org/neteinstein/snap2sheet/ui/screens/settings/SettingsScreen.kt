package org.neteinstein.snap2sheet.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.platform.PermissionStatus
import org.neteinstein.snap2sheet.platform.rememberCameraPermissionState
import org.neteinstein.snap2sheet.platform.rememberNotificationPermissionState
import org.neteinstein.snap2sheet.ui.components.FaturaCard
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.FaturaTopBar
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.components.SectionTitle
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeDefaults: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberCameraPermissionState()
    val notificationPermission = rememberNotificationPermissionState()
    val isDemo = state.account?.isDemo == true

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface).navigationBarsPadding()) {
        FaturaTopBar(title = "Settings", onBack = onBack)

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SettingsSection(title = "Account") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(FaturaColors.AccentSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(state.account?.initials ?: "?", style = MaterialTheme.typography.labelMedium, color = FaturaColors.Accent)
                            }
                            Column {
                                Text(
                                    state.account?.email ?: "Not signed in",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FaturaColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    when {
                                        state.account == null -> "Disconnected"
                                        isDemo -> "Demo mode · nothing leaves this device"
                                        else -> "Connected"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.account != null && !isDemo) FaturaColors.Success else FaturaColors.Muted,
                                )
                            }
                        }
                        Text(
                            "Sign out",
                            style = MaterialTheme.typography.labelMedium,
                            color = FaturaColors.Danger,
                            modifier = Modifier.clickable(onClick = viewModel::signOut),
                        )
                    }
                    if (state.account != null && !isDemo) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Saves failing with a sign-in error?",
                                style = MaterialTheme.typography.bodySmall,
                                color = FaturaColors.Muted,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (state.isReconnecting) "Reconnecting…" else "Reconnect",
                                style = MaterialTheme.typography.labelMedium,
                                color = FaturaColors.Accent,
                                modifier = if (state.isReconnecting) Modifier else Modifier.clickable(onClick = viewModel::reconnect),
                            )
                        }
                    }
                    Text(
                        "Signing out removes this device's invoice history. Your spreadsheets are untouched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FaturaColors.MutedStrong,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }
            }

            item {
                SettingsSection(title = "Defaults") {
                    DefaultRow(
                        label = "SPREADSHEET",
                        value = state.defaultSpreadsheetName,
                        onClick = onChangeDefaults,
                    ) { FaturaIcons.Document(tint = FaturaColors.Accent, size = 18.dp) }
                    DefaultRow(
                        label = "DRIVE FOLDER FOR PHOTOS",
                        value = state.defaultFolderName,
                        onClick = onChangeDefaults,
                    ) { FaturaIcons.Folder(tint = FaturaColors.Accent, size = 18.dp) }
                }
            }

            item {
                SettingsSection(title = "Append Rules") {
                    SettingsSwitchRow(
                        title = "Match columns by header",
                        subtitle = "Fills each column by its title, not position",
                        checked = state.appendRules.matchColumnsByHeader,
                        onCheckedChange = viewModel::setMatchColumnsByHeader,
                    )
                    SettingsSwitchRow(
                        title = "Skip duplicate invoices",
                        subtitle = "Matched by ATCUD code",
                        checked = state.appendRules.skipDuplicateInvoices,
                        onCheckedChange = viewModel::setSkipDuplicates,
                    )
                    SettingsSwitchRow(
                        title = "New sheet tab each month",
                        subtitle = "Otherwise all rows append to one tab",
                        checked = state.appendRules.newSheetTabEachMonth,
                        onCheckedChange = viewModel::setNewSheetTabEachMonth,
                    )
                }
            }

            item {
                SettingsSection(title = "Permissions") {
                    val camera = cameraPermission.status
                    PermissionRow(
                        label = "Camera",
                        value = when (camera) {
                            PermissionStatus.GRANTED -> "Allowed"
                            PermissionStatus.DENIED -> if (cameraPermission.canOpenSettings) "Denied · Open settings" else "Blocked in browser"
                            PermissionStatus.NOT_DETERMINED -> "Not asked · Allow"
                        },
                        allowed = camera == PermissionStatus.GRANTED,
                        onClick = when (camera) {
                            PermissionStatus.GRANTED -> null
                            PermissionStatus.DENIED -> if (cameraPermission.canOpenSettings) cameraPermission::openSettings else null
                            PermissionStatus.NOT_DETERMINED -> cameraPermission::request
                        },
                    ) { FaturaIcons.Camera(tint = FaturaColors.Muted, size = 17.dp) }
                    val notifications = notificationPermission.status
                    PermissionRow(
                        label = "Notifications",
                        value = when (notifications) {
                            PermissionStatus.GRANTED -> "Allowed"
                            PermissionStatus.DENIED -> if (notificationPermission.canOpenSettings) "Denied · Open settings" else "Blocked in browser"
                            PermissionStatus.NOT_DETERMINED -> "Not asked · Allow"
                        },
                        allowed = notifications == PermissionStatus.GRANTED,
                        onClick = when (notifications) {
                            PermissionStatus.GRANTED -> null
                            PermissionStatus.DENIED -> if (notificationPermission.canOpenSettings) notificationPermission::openSettings else null
                            PermissionStatus.NOT_DETERMINED -> notificationPermission::request
                        },
                    ) { FaturaIcons.Clock(tint = FaturaColors.Muted, size = 17.dp) }
                    PermissionRow(
                        label = "Google Sheets & Drive",
                        value = when {
                            state.account == null -> "Not connected"
                            isDemo -> "Demo mode"
                            else -> "Allowed"
                        },
                        allowed = state.account != null && !isDemo,
                        onClick = null,
                    ) { FaturaIcons.Document(tint = FaturaColors.Muted, size = 17.dp) }
                }
            }

            item {
                SettingsSection(title = "Notifications") {
                    SettingsSwitchRow(
                        title = "Notify me when a save finishes",
                        subtitle = "Invoices save in the background — get a notification when each one is done, or fails",
                        checked = state.notifyWhenSaveFinishes,
                        onCheckedChange = { enabled ->
                            viewModel.setNotifyWhenSaveFinishes(enabled)
                            if (enabled && notificationPermission.status == PermissionStatus.NOT_DETERMINED) notificationPermission.request()
                        },
                    )
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
                    Text("Fatura v1.0.0", style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        SectionTitle(text = title)
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        FaturaCard(modifier = Modifier.fillMaxWidth(), contentPadding = 6.dp) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = FaturaColors.Accent))
    }
}

@Composable
private fun PermissionRow(label: String, value: String, allowed: Boolean, onClick: (() -> Unit)?, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
        }
        Text(value, style = MaterialTheme.typography.labelSmall, color = if (allowed) FaturaColors.Success else if (onClick != null) FaturaColors.Accent else FaturaColors.Muted)
    }
}

@Composable
private fun DefaultRow(label: String, value: String?, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(size = 36.dp) { icon() }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
            Text(
                value ?: "None selected",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = FaturaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("Change", style = MaterialTheme.typography.labelMedium, color = FaturaColors.Accent)
    }
}
