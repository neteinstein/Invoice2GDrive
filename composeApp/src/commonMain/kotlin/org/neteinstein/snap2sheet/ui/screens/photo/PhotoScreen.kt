package org.neteinstein.snap2sheet.ui.screens.photo

import org.neteinstein.snap2sheet.platform.tr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.platform.PhotoSource
import org.neteinstein.snap2sheet.platform.rememberPhotoPicker
import org.neteinstein.snap2sheet.ui.components.FaturaDashedButton
import org.neteinstein.snap2sheet.ui.components.FaturaGhostButton
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.FaturaPrimaryButton
import org.neteinstein.snap2sheet.ui.components.FaturaTopBar
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.components.displayName
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

/** Step 2 of a scan: a photo of the whole invoice, uploaded to the Drive folder with the row. */
@Composable
fun PhotoScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: PhotoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pick = rememberPhotoPicker(viewModel::onCaptured)
    val invoice = state.invoice

    if (invoice == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface)) {
        FaturaTopBar(title = tr("Photo of the invoice", "Foto da fatura"), onBack = onBack)

        Text(
            if (invoice.rawQr != null) {
                tr("QR code read: ${invoice.displayName()} · ${Formatting.euros(invoice.total)}. Now snap the whole invoice — it's saved to your Drive folder next to the spreadsheet row.", "Código QR lido: ${invoice.displayName()} · ${Formatting.euros(invoice.total)}. Agora fotografe a fatura inteira — é guardada na pasta do Drive junto à linha da folha de cálculo.")
            } else {
                tr("Snap the whole invoice — it's saved to your Drive folder next to the spreadsheet row.", "Fotografe a fatura inteira — é guardada na pasta do Drive junto à linha da folha de cálculo.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = FaturaColors.Muted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(FaturaColors.SurfaceMuted)
                .border(1.dp, FaturaColors.Border, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bytes = state.previewBytes
            val bitmap = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
            when {
                state.isSaving -> Text(tr("Processing photo…", "A processar a foto…"), style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted)
                bitmap != null -> Image(bitmap = bitmap, contentDescription = "Invoice photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                state.isPdf -> PlaceholderContent(tr("PDF attached", "PDF anexado"), tr("It will be uploaded as-is.", "Será carregado tal como está."))
                else -> PlaceholderContent(tr("No photo yet", "Ainda sem foto"), tr("Lay the invoice flat, in good light, with all four corners visible.", "Coloque a fatura na horizontal, com boa luz e os quatro cantos visíveis."))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.hasPhoto) {
                FaturaPrimaryButton(text = tr("Continue", "Continuar"), onClick = onContinue, enabled = !state.isSaving)
                FaturaDashedButton(text = tr("Retake photo", "Tirar outra foto"), onClick = { pick(PhotoSource.CAMERA) })
                FaturaGhostButton(text = tr("Remove photo", "Remover foto"), onClick = viewModel::removePhoto)
            } else {
                FaturaPrimaryButton(text = tr("Take photo", "Tirar foto"), onClick = { pick(PhotoSource.CAMERA) }, enabled = !state.isSaving) {
                    FaturaIcons.Camera(tint = androidx.compose.ui.graphics.Color.White, size = 18.dp)
                }
                FaturaDashedButton(text = tr("Choose from library", "Escolher da biblioteca"), onClick = { pick(PhotoSource.LIBRARY) })
                FaturaGhostButton(text = tr("Skip — save the data only", "Saltar — guardar só os dados"), onClick = onContinue)
            }
        }
    }
}

@Composable
private fun PlaceholderContent(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        IconBadge(size = 56.dp, cornerRadius = 16.dp) { FaturaIcons.Camera(tint = FaturaColors.Accent, size = 26.dp) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted, textAlign = TextAlign.Center)
    }
}
