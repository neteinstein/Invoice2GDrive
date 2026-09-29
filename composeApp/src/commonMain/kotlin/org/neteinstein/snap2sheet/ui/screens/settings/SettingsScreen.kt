package org.neteinstein.snap2sheet.ui.screens.settings

import org.neteinstein.snap2sheet.platform.appName

import org.neteinstein.snap2sheet.platform.tr
import org.neteinstein.snap2sheet.ui.components.CopyrightFooter

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

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface).navigationBarsPadding()) {
        FaturaTopBar(title = tr("Settings", "Definições"), onBack = onBack)

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SettingsSection(title = tr("Account", "Conta")) {
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
                                    state.account?.email ?: tr("Not signed in", "Sessão não iniciada"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FaturaColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    when {
                                        state.account == null -> tr("Disconnected", "Desligado")
                                        else -> tr("Connected", "Ligado")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.account != null) FaturaColors.Success else FaturaColors.Muted,
                                )
                            }
                        }
                        Text(
                            tr("Sign out", "Terminar sessão"),
                            style = MaterialTheme.typography.labelMedium,
                            color = FaturaColors.Danger,
                            modifier = Modifier.clickable(onClick = viewModel::signOut),
                        )
                    }
                    if (state.account != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tr("Saves failing with a sign-in error?", "Os registos falham com erro de início de sessão?"),
                                style = MaterialTheme.typography.bodySmall,
                                color = FaturaColors.Muted,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (state.isReconnecting) tr("Reconnecting…", "A reconectar…") else tr("Reconnect", "Reconectar"),
                                style = MaterialTheme.typography.labelMedium,
                                color = FaturaColors.Accent,
                                modifier = if (state.isReconnecting) Modifier else Modifier.clickable(onClick = viewModel::reconnect),
                            )
                        }
                    }
                    Text(
                        tr("Signing out removes this device's invoice history. Your spreadsheets are untouched.", "Terminar a sessão remove o histórico de faturas deste dispositivo. As suas folhas de cálculo não são alteradas."),
                        style = MaterialTheme.typography.bodySmall,
                        color = FaturaColors.MutedStrong,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }
            }

            item {
                SettingsSection(title = tr("Defaults", "Predefinições")) {
                    DefaultRow(
                        label = tr("SPREADSHEET", "FOLHA DE CÁLCULO"),
                        value = state.defaultSpreadsheetName,
                        onClick = onChangeDefaults,
                    ) { FaturaIcons.Document(tint = FaturaColors.Accent, size = 18.dp) }
                    DefaultRow(
                        label = tr("DRIVE FOLDER FOR PHOTOS", "PASTA DO DRIVE PARA FOTOS"),
                        value = state.defaultFolderName,
                        onClick = onChangeDefaults,
                    ) { FaturaIcons.Folder(tint = FaturaColors.Accent, size = 18.dp) }
                }
            }

            item {
                SettingsSection(title = tr("Append Rules", "Regras de adição")) {
                    SettingsSwitchRow(
                        title = tr("Match columns by header", "Associar colunas pelo cabeçalho"),
                        subtitle = tr("Fills each column by its title, not position", "Preenche cada coluna pelo título, não pela posição"),
                        checked = state.appendRules.matchColumnsByHeader,
                        onCheckedChange = viewModel::setMatchColumnsByHeader,
                    )
                    SettingsSwitchRow(
                        title = tr("Skip duplicate invoices", "Ignorar faturas duplicadas"),
                        subtitle = tr("Matched by ATCUD code", "Identificadas pelo código ATCUD"),
                        checked = state.appendRules.skipDuplicateInvoices,
                        onCheckedChange = viewModel::setSkipDuplicates,
                    )
                    SettingsSwitchRow(
                        title = tr("New sheet tab each month", "Novo separador em cada mês"),
                        subtitle = tr("Otherwise all rows append to one tab", "Caso contrário, todas as linhas vão para um só separador"),
                        checked = state.appendRules.newSheetTabEachMonth,
                        onCheckedChange = viewModel::setNewSheetTabEachMonth,
                    )
                }
            }

            item {
                SettingsSection(title = tr("Permissions", "Permissões")) {
                    val camera = cameraPermission.status
                    PermissionRow(
                        label = tr("Camera", "Câmara"),
                        value = when (camera) {
                            PermissionStatus.GRANTED -> tr("Allowed", "Permitido")
                            PermissionStatus.DENIED -> if (cameraPermission.canOpenSettings) tr("Denied · Open settings", "Recusado · Abrir definições") else tr("Blocked in browser", "Bloqueado no navegador")
                            PermissionStatus.NOT_DETERMINED -> tr("Not asked · Allow", "Por pedir · Permitir")
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
                        label = tr("Notifications", "Notificações"),
                        value = when (notifications) {
                            PermissionStatus.GRANTED -> tr("Allowed", "Permitido")
                            PermissionStatus.DENIED -> if (notificationPermission.canOpenSettings) tr("Denied · Open settings", "Recusado · Abrir definições") else tr("Blocked in browser", "Bloqueado no navegador")
                            PermissionStatus.NOT_DETERMINED -> tr("Not asked · Allow", "Por pedir · Permitir")
                        },
                        allowed = notifications == PermissionStatus.GRANTED,
                        onClick = when (notifications) {
                            PermissionStatus.GRANTED -> null
                            PermissionStatus.DENIED -> if (notificationPermission.canOpenSettings) notificationPermission::openSettings else null
                            PermissionStatus.NOT_DETERMINED -> notificationPermission::request
                        },
                    ) { FaturaIcons.Clock(tint = FaturaColors.Muted, size = 17.dp) }
                    PermissionRow(
                        label = tr("Google Sheets & Drive", "Google Sheets e Drive"),
                        value = when {
                            state.account == null -> tr("Not connected", "Não ligado")
                            else -> tr("Allowed", "Permitido")
                        },
                        allowed = state.account != null,
                        onClick = null,
                    ) { FaturaIcons.Document(tint = FaturaColors.Muted, size = 17.dp) }
                }
            }

            item {
                SettingsSection(title = tr("Notifications", "Notificações")) {
                    SettingsSwitchRow(
                        title = tr("Notify me when a save finishes", "Notificar-me quando um registo terminar"),
                        subtitle = tr("Invoices save in the background — get a notification when each one is done, or fails", "As faturas são guardadas em segundo plano — receba uma notificação quando cada uma terminar ou falhar"),
                        checked = state.notifyWhenSaveFinishes,
                        onCheckedChange = { enabled ->
                            viewModel.setNotifyWhenSaveFinishes(enabled)
                            if (enabled && notificationPermission.status == PermissionStatus.NOT_DETERMINED) notificationPermission.request()
                        },
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("$appName v1.0.0", style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted)
                    CopyrightFooter()
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
                value ?: tr("None selected", "Nenhuma selecionada"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = FaturaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(tr("Change", "Alterar"), style = MaterialTheme.typography.labelMedium, color = FaturaColors.Accent)
    }
}
