package org.neteinstein.snap2sheet.ui.screens.welcome

import org.neteinstein.snap2sheet.platform.appName

import org.neteinstein.snap2sheet.platform.tr
import org.neteinstein.snap2sheet.ui.components.CopyrightFooter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.ui.components.FaturaCard
import org.neteinstein.snap2sheet.ui.components.FaturaGhostButton
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.FaturaPrimaryButton
import org.neteinstein.snap2sheet.platform.PermissionStatus
import org.neteinstein.snap2sheet.platform.rememberCameraPermissionState
import org.neteinstein.snap2sheet.platform.rememberNotificationPermissionState
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val cameraPermission = rememberCameraPermissionState()
    val notificationPermission = rememberNotificationPermissionState()
    var requested by remember { mutableStateOf(false) }

    // Ask for the camera, then notifications, then move on — once each prompt has been answered,
    // whichever way.
    LaunchedEffect(requested, cameraPermission.status, notificationPermission.status) {
        if (!requested || cameraPermission.status == PermissionStatus.NOT_DETERMINED) return@LaunchedEffect
        if (notificationPermission.status == PermissionStatus.NOT_DETERMINED) notificationPermission.request() else onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FaturaColors.Surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(size = 72.dp, cornerRadius = 20.dp) {
                FaturaIcons.Grid(tint = FaturaColors.Accent, size = 34.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text(appName, style = MaterialTheme.typography.headlineMedium, color = FaturaColors.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr("Scan invoice QR codes.\nFill your spreadsheet automatically.", "Digitalize códigos QR de faturas.\nPreencha a folha de cálculo automaticamente."),
                style = MaterialTheme.typography.bodyLarge,
                color = FaturaColors.Muted,
                textAlign = TextAlign.Center,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingStep(tr("1. Scan", "1. Digitalizar")) { FaturaIcons.Camera(tint = FaturaColors.Accent, size = 24.dp) }
                StepChevron()
                OnboardingStep(tr("2. Extract", "2. Extrair")) { FaturaIcons.Document(tint = FaturaColors.Accent, size = 24.dp) }
                StepChevron()
                OnboardingStep(tr("3. Save", "3. Guardar")) { FaturaIcons.Grid(tint = FaturaColors.Accent, size = 22.dp) }
            }

            FaturaCard(containerColor = FaturaColors.SurfaceMuted) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(background = FaturaColors.Surface, size = 40.dp, cornerRadius = 10.dp) {
                        FaturaIcons.Camera(tint = FaturaColors.Accent, size = 20.dp)
                    }
                    Column {
                        Text(tr("Camera & notifications", "Câmara e notificações"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
                        Text(
                            tr("The camera reads the QR code and photographs the invoice, which goes only to your own Google Drive. ", "A câmara lê o código QR e fotografa a fatura, que vai apenas para o seu próprio Google Drive. ") +
                                tr("Notifications tell you when an invoice has finished saving.", "As notificações avisam quando uma fatura termina de ser guardada."),
                            style = MaterialTheme.typography.bodySmall,
                            color = FaturaColors.Muted,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cameraPermission.status == PermissionStatus.GRANTED && notificationPermission.status != PermissionStatus.NOT_DETERMINED) {
                    FaturaPrimaryButton(text = tr("Continue", "Continuar"), onClick = onContinue)
                } else {
                    FaturaPrimaryButton(
                        text = tr("Allow Camera & Notifications", "Permitir câmara e notificações"),
                        onClick = {
                            requested = true
                            if (cameraPermission.status == PermissionStatus.NOT_DETERMINED) cameraPermission.request()
                        },
                    )
                    FaturaGhostButton(text = tr("Not now", "Agora não"), onClick = onContinue)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Dot(on = true)
                Spacer(Modifier.width(6.dp))
                Dot(on = false)
            }
            CopyrightFooter(modifier = Modifier.padding(bottom = 20.dp))
        }
    }
}

@Composable
private fun OnboardingStep(label: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        IconBadge(size = 52.dp, cornerRadius = 14.dp) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = FaturaColors.Muted)
    }
}

@Composable
private fun StepChevron() {
    FaturaIcons.ChevronRight(tint = FaturaColors.Border, size = 16.dp)
}

@Composable
private fun Dot(on: Boolean) {
    Box(
        modifier = Modifier
            .height(7.dp)
            .width(if (on) 18.dp else 7.dp)
            .background(if (on) FaturaColors.Accent else FaturaColors.Border, RoundedCornerShape(4.dp))
    )
}
