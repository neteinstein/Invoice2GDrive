package org.neteinstein.snap2sheet.ui.screens.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.ui.components.FaturaGhostButton
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.Notice
import org.neteinstein.snap2sheet.ui.components.FaturaPrimaryButton
import org.neteinstein.snap2sheet.ui.components.FaturaTopBar
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.components.TextInputDialog
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun SignInScreen(
    onBack: (() -> Unit)?,
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clientIdSetup = state.clientIdSetup
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface)) {
        FaturaTopBar(title = "", onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                IconBadge(size = 64.dp, cornerRadius = 18.dp) {
                    FaturaIcons.Document(tint = FaturaColors.Accent, size = 30.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect your Google Account",
                    style = MaterialTheme.typography.headlineSmall,
                    color = FaturaColors.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fatura saves scanned invoices straight to a Google Sheet you choose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FaturaColors.Muted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            org.neteinstein.snap2sheet.ui.components.FaturaCard(containerColor = FaturaColors.SurfaceMuted, contentPadding = 4.dp) {
                PermissionRow("View and manage your Google Sheets")
                PermissionRow("Upload invoice photos to your Google Drive")
                PermissionRow("See your name and email address")
            }

            Spacer(Modifier.height(24.dp))

            state.error?.let {
                Notice(text = it, color = FaturaColors.Danger, background = FaturaColors.DangerSoft)
                Spacer(Modifier.height(12.dp))
            }

            if (state.isGoogleSignInAvailable) {
                FaturaPrimaryButton(
                    text = if (state.isSigningIn) "Connecting…" else "Continue with Google",
                    onClick = viewModel::signInWithGoogle,
                    enabled = !state.isSigningIn,
                ) {
                    GoogleGlyph()
                }
                if (clientIdSetup?.isEnteredInApp == true) {
                    FaturaGhostButton(text = "Change OAuth client ID", onClick = viewModel::editClientId)
                }
            } else if (clientIdSetup != null) {
                Text(
                    "To connect Google, paste the ID of your own OAuth client (Google Cloud Console, " +
                        "APIs & Services, Credentials). Type: ${clientIdSetup.clientType}. " +
                        "${clientIdSetup.registration}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FaturaColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FaturaPrimaryButton(text = "Enter OAuth client ID", onClick = viewModel::editClientId) {
                    GoogleGlyph()
                }
            } else {
                Text(
                    "Google sign-in isn't set up in this build.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FaturaColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.isEditingClientId && clientIdSetup != null) {
            TextInputDialog(
                title = "Google OAuth client ID",
                label = "Client ID",
                confirmText = "Save",
                onConfirm = viewModel::saveClientId,
                onDismiss = viewModel::dismissClientIdEditor,
                initialValue = if (clientIdSetup.isEnteredInApp) clientIdSetup.clientId else "",
                placeholder = "1234-abc.apps.googleusercontent.com",
                supportingText = state.clientIdError
                    ?: "A \"${clientIdSetup.clientType}\" client. ${clientIdSetup.registration}.",
                extraAction = if (clientIdSetup.isEnteredInApp) ("Remove" to viewModel::resetClientId) else null,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "By continuing you agree to Fatura's Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                color = FaturaColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val success = FaturaColors.Success
        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = w * 0.11f
            drawCircle(
                color = success,
                radius = size.minDimension / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
            )
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.34f)
            }
            drawPath(
                path,
                color = success,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = FaturaColors.Ink)
    }
}

@Composable
private fun GoogleGlyph() {
    // A simplified four-color dot stands in for the Google "G" mark.
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853)).forEach { c ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(c))
        }
    }
}
