package org.neteinstein.snap2sheet.ui.screens.scan

import org.neteinstein.snap2sheet.platform.appName

import org.neteinstein.snap2sheet.platform.tr

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.platform.PermissionStatus
import org.neteinstein.snap2sheet.platform.QrScanner
import org.neteinstein.snap2sheet.platform.hasInlineCameraPreview
import org.neteinstein.snap2sheet.platform.rememberCameraPermissionState
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.TextInputDialog

private val ScanBackgroundTop = Color(0xFF2B2F38)
private val ScanBackgroundMid = Color(0xFF15171C)
private val ScanBackgroundBottom = Color(0xFF0C0D10)
private val ScanAccent = Color(0xFF5FA0FF)
private val ScanError = Color(0xFFFF8A80)
private val FrameSize = 250.dp

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onDraftReady: () -> Unit,
    viewModel: ScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberCameraPermissionState()
    // On the web each scan is a one-shot overlay; this reopens it.
    var browserScanActive by remember { mutableStateOf(true) }
    var showPasteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.draftReady) {
        if (state.draftReady) {
            viewModel.onNavigated()
            onDraftReady()
        }
    }
    LaunchedEffect(permission.status) {
        if (hasInlineCameraPreview && permission.status == PermissionStatus.NOT_DETERMINED) permission.request()
    }

    val cameraGranted = permission.status == PermissionStatus.GRANTED
    val showScanner = if (hasInlineCameraPreview) cameraGranted else browserScanActive && !showPasteDialog

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(ScanBackgroundTop, ScanBackgroundMid, ScanBackgroundBottom))),
    ) {
        if (showScanner) {
            QrScanner(
                onQrCode = {
                    if (!hasInlineCameraPreview) browserScanActive = false
                    viewModel.onQrCode(it)
                },
                onClosed = { browserScanActive = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(onClick = onBack) { FaturaIcons.Back(tint = Color.White) }
                Text(tr("Scan Invoice", "Digitalizar fatura"), style = MaterialTheme.typography.titleSmall, color = Color.White)
                RoundIconButton(onClick = viewModel::onManualEntry) { FaturaIcons.Document(tint = Color.White, size = 18.dp) }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(modifier = Modifier.size(FrameSize), contentAlignment = Alignment.Center) {
                    FaturaIcons.ScanCorners(tint = if (state.error != null) ScanError else ScanAccent, modifier = Modifier.fillMaxSize())
                    when {
                        hasInlineCameraPreview && !cameraGranted -> PermissionPrompt(
                            denied = permission.status == PermissionStatus.DENIED,
                            canOpenSettings = permission.canOpenSettings,
                            onAllow = permission::request,
                            onOpenSettings = permission::openSettings,
                        )
                        !hasInlineCameraPreview && !browserScanActive -> PillButton(text = tr("Scan again", "Digitalizar de novo"), onClick = { browserScanActive = true })
                        else -> ScanLine()
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val error = state.error
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (error != null) ScanError else Color(0xFF4ADE80)))
                    Text(
                        error ?: when {
                            showScanner -> tr("Looking for a QR code…", "À procura de um código QR…")
                            !hasInlineCameraPreview -> tr("Scanner closed", "Leitor fechado")
                            else -> tr("Camera is off", "Câmara desligada")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEDEFF2),
                    )
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    tr("Align the invoice's QR code inside the frame. It fills in automatically.", "Alinhe o código QR da fatura dentro da moldura. Os dados são preenchidos automaticamente."),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC6CAD2),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(260.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tr("No QR code, or it won't scan?", "Sem código QR, ou não lê?"), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA1AB), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(text = tr("Paste QR text", "Colar texto do QR"), onClick = { showPasteDialog = true }, subtle = true)
                    PillButton(text = tr("Type it in", "Escrever manualmente"), onClick = viewModel::onManualEntry, subtle = true)
                }
            }
        }
    }

    if (showPasteDialog) {
        TextInputDialog(
            title = tr("Paste QR code text", "Colar texto do código QR"),
            label = tr("QR code text", "Texto do código QR"),
            placeholder = "A:500100209*B:999999990*C:PT*…",
            confirmText = tr("Use", "Usar"),
            singleLine = false,
            supportingText = tr("The text encoded in the invoice's QR code, e.g. from another scanner app.", "O texto codificado no código QR da fatura, por exemplo de outra aplicação de leitura."),
            onConfirm = {
                showPasteDialog = false
                viewModel.onPasted(it)
            },
            onDismiss = { showPasteDialog = false },
        )
    }
}

@Composable
private fun ScanLine() {
    val transition = rememberInfiniteTransition()
    val progress by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(y = maxHeight * progress)
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(2.dp)
                .background(ScanAccent.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, canOpenSettings: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
            if (denied) tr("Camera access is off. Turn it on to scan QR codes.", "O acesso à câmara está desligado. Ative-o para ler códigos QR.") else tr("$appName needs the camera to scan QR codes.", "$appName precisa da câmara para ler códigos QR."),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        when {
            !denied -> PillButton(text = tr("Allow camera", "Permitir câmara"), onClick = onAllow)
            canOpenSettings -> PillButton(text = tr("Open settings", "Abrir definições"), onClick = onOpenSettings)
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, subtle: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (subtle) Color.White else Color(0xFF12151B),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (subtle) Color.White.copy(alpha = 0.14f) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
