package org.neteinstein.snap2sheet.ui.components

import org.neteinstein.snap2sheet.platform.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.domain.model.GoogleAccount
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

/** Account avatar that opens a menu with the signed-in email (read-only) and Sign out, confirmed first. */
@Composable
fun ProfileMenuButton(account: GoogleAccount?, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FaturaColors.AccentSoft)
                .clickable(role = Role.Button, onClick = { showMenu = true })
                .semantics { contentDescription = tr("Account", "Conta") },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account?.initials ?: "?",
                style = MaterialTheme.typography.labelMedium,
                color = FaturaColors.Accent,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = FaturaColors.Surface,
        ) {
            account?.let {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(tr("Signed in as", "Sessão iniciada como"), style = MaterialTheme.typography.labelSmall, color = FaturaColors.Muted)
                    Text(it.email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = FaturaColors.Ink)
                }
                HorizontalDivider(color = FaturaColors.Border)
            }
            DropdownMenuItem(
                text = { Text(tr("Sign out", "Terminar sessão"), color = FaturaColors.Danger) },
                leadingIcon = { FaturaIcons.SignOut(tint = FaturaColors.Danger, size = 18.dp) },
                onClick = {
                    showMenu = false
                    showSignOutDialog = true
                },
            )
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = FaturaColors.Surface,
            title = { Text(tr("Sign out?", "Terminar sessão?"), color = FaturaColors.Ink) },
            text = {
                Text(
                    tr("Signing out removes this device's invoice history. Your spreadsheets are untouched.", "Terminar a sessão remove o histórico de faturas deste dispositivo. As suas folhas de cálculo não são alteradas."),
                    color = FaturaColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut()
                }) { Text(tr("Sign out", "Terminar sessão"), color = FaturaColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text(tr("Cancel", "Cancelar"), color = FaturaColors.Muted) }
            },
        )
    }
}
