package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.DocumentType
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.domain.validation.Nif
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

/** A saved invoice's fields, warnings and last error, with Retry (for failed saves) and Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailSheet(
    invoice: Invoice,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FaturaColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
                    Text(
                        when (invoice.status) {
                            InvoiceStatus.QUEUED -> "Saving to ${invoice.destinationSpreadsheetName} in the background…"
                            InvoiceStatus.DUPLICATE -> "Already in ${invoice.destinationSpreadsheetName} — skipped"
                            InvoiceStatus.FAILED -> "Not saved to ${invoice.destinationSpreadsheetName}"
                            else -> "Saved to ${invoice.destinationSpreadsheetName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FaturaColors.Muted,
                    )
                }
                StatusChip(status = invoice.status)
            }
            Spacer(Modifier.height(14.dp))

            invoice.errorMessage?.let {
                val failed = invoice.status == InvoiceStatus.FAILED
                Notice(
                    text = it,
                    color = if (failed) FaturaColors.Danger else FaturaColors.Muted,
                    background = if (failed) FaturaColors.DangerSoft else FaturaColors.Background,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (invoice.warnings.isNotEmpty()) {
                Notice(text = invoice.warnings.joinToString("\n") { "• $it" }, color = FaturaColors.Warning, background = FaturaColors.WarningSoft)
                Spacer(Modifier.height(10.dp))
            }

            FaturaCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                DetailRow("NIF Emitente", Nif.format(invoice.nifEmitente))
                DetailRow("NIF Adquirente", Nif.format(invoice.nifAdquirente))
                DetailRow("Tipo de Documento", DocumentType.label(invoice.documentType))
                DetailRow("N.º Documento", invoice.documentNumber)
                DetailRow("Data", invoice.issueDate?.let(Formatting::date).orEmpty())
                DetailRow("ATCUD", invoice.atcud)
                DetailRow("Base Tributável", Formatting.euros(invoice.taxBase))
                DetailRow("IVA", Formatting.euros(invoice.vat))
                DetailRow("Total", Formatting.euros(invoice.total), bold = true)
            }
            Spacer(Modifier.height(12.dp))

            val uriHandler = LocalUriHandler.current
            val link = invoice.driveFileLink
            FaturaCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FaturaIcons.Folder(tint = FaturaColors.Accent, size = 18.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PHOTO", style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
                        Text(
                            when {
                                link != null -> "In ${invoice.destinationFolderName}"
                                invoice.photo != null -> "Waiting to upload to ${invoice.destinationFolderName}"
                                else -> "No photo"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = FaturaColors.Ink,
                        )
                    }
                    if (link != null) {
                        Text(
                            "Open",
                            style = MaterialTheme.typography.labelMedium,
                            color = FaturaColors.Accent,
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(link) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (invoice.status == InvoiceStatus.FAILED && invoice.destinationSpreadsheetId != null) {
                FaturaPrimaryButton(text = "Retry saving", onClick = onRetry)
                Spacer(Modifier.height(6.dp))
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Remove from history", color = FaturaColors.Danger, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                if (invoice.status == InvoiceStatus.QUEUED) "Removing it here cancels the save." else "Removing it here doesn't touch the spreadsheet or Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = FaturaColors.MutedStrong,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun Notice(text: String, color: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = FaturaColors.Muted)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
            color = FaturaColors.Ink,
        )
    }
}
