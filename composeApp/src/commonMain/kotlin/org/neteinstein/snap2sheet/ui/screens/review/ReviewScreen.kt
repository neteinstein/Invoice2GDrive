package org.neteinstein.snap2sheet.ui.screens.review

import org.neteinstein.snap2sheet.platform.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.DocumentType
import org.neteinstein.snap2sheet.ui.components.FaturaCard
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.FaturaPrimaryButton
import org.neteinstein.snap2sheet.ui.components.FaturaTopBar
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.components.Notice
import org.neteinstein.snap2sheet.ui.components.SectionTitle
import org.neteinstein.snap2sheet.ui.components.displayName
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onChangePhoto: () -> Unit,
    onChooseDestination: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (event) {
            ReviewEvent.CHOOSE_DESTINATION -> onChooseDestination()
            ReviewEvent.SAVED -> onSaved()
            null -> return@LaunchedEffect
        }
        viewModel.onEventHandled()
    }

    val invoice = state.invoice
    if (invoice == null) {
        // Nothing under review (e.g. restored after process death) — go back. After a save the
        // draft is gone too, but then the SAVED event does the navigating.
        if (!state.isSaving) LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface).imePadding()) {
        FaturaTopBar(title = tr("Review Invoice", "Rever fatura"), onBack = onBack)

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (state.fromQr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FaturaColors.SuccessSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckMark()
                    Text(tr("QR code scanned successfully", "Código QR lido com sucesso"), style = MaterialTheme.typography.labelMedium, color = FaturaColors.Success)
                }
            } else {
                Notice(text = tr("Type in the invoice details as printed.", "Escreva os dados da fatura tal como estão impressos."), color = FaturaColors.Muted, background = FaturaColors.Background)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    FaturaCard(containerColor = FaturaColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            IconBadge(size = 34.dp, cornerRadius = 9.dp, background = FaturaColors.Surface) {
                                FaturaIcons.Document(tint = FaturaColors.Muted, size = 18.dp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(invoice.displayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
                            Text(
                                listOfNotNull(
                                    invoice.documentNumber.ifBlank { null },
                                    invoice.issueDate?.let(Formatting::date),
                                    DocumentType.label(invoice.documentType).takeIf { DocumentType.isKnown(invoice.documentType) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = FaturaColors.Muted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(Formatting.euros(invoice.total), style = MaterialTheme.typography.headlineMedium, color = FaturaColors.Ink)
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                    FaturaCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onChangePhoto)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(size = 36.dp) { FaturaIcons.Camera(tint = FaturaColors.Accent, size = 18.dp) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("PHOTO OF THE INVOICE", "FOTO DA FATURA"), style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
                                Text(
                                    when {
                                        invoice.photo == null -> tr("None — tap to add one", "Nenhuma — toque para adicionar")
                                        invoice.photo.mimeType == "application/pdf" -> tr("PDF attached", "PDF anexado")
                                        else -> tr("Photo attached", "Foto anexada")
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (invoice.photo == null) FaturaColors.Muted else FaturaColors.Ink,
                                )
                            }
                            FaturaIcons.ChevronRight(tint = FaturaColors.Subtle)
                        }
                    }
                }
            }

            if (state.warnings.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                        Notice(
                            text = tr("Worth a second look:\n", "Convém rever:\n") + state.warnings.joinToString("\n") { "• $it" },
                            color = FaturaColors.Warning,
                            background = FaturaColors.WarningSoft,
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    SectionTitle(text = tr("Invoice details", "Dados da fatura"))
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    FaturaCard(modifier = Modifier.fillMaxWidth(), contentPadding = 6.dp) {
                        ReviewField.entries.forEach { field ->
                            FieldRow(
                                field = field,
                                value = state.values[field].orEmpty(),
                                error = state.fieldErrors[field],
                                showDivider = field != ReviewField.entries.last(),
                                onValueChange = { viewModel.onValueChange(field, it) },
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 6.dp)) {
                    SectionTitle(text = tr("Destination", "Destino"))
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                    FaturaCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseDestination)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(size = 36.dp) { FaturaIcons.Document(tint = FaturaColors.Accent, size = 18.dp) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("ROW IN", "LINHA EM"), style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
                                Text(
                                    state.selectedSpreadsheetName ?: tr("Choose a spreadsheet", "Escolha uma folha de cálculo"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FaturaColors.Ink,
                                )
                                if (invoice.photo != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(tr("PHOTO IN", "FOTO EM"), style = MaterialTheme.typography.labelSmall, color = FaturaColors.MutedStrong)
                                    Text(
                                        state.selectedFolderName ?: tr("Choose a Drive folder", "Escolha uma pasta do Drive"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FaturaColors.Ink,
                                    )
                                }
                            }
                            FaturaIcons.ChevronRight(tint = FaturaColors.Subtle)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp)) {
            FaturaPrimaryButton(
                text = when {
                    state.isSaving -> tr("Saving…", "A guardar…")
                    state.savesDirectly -> tr("Save to ${state.selectedSpreadsheetName}", "Guardar em ${state.selectedSpreadsheetName}")
                    else -> tr("Save Invoice", "Guardar fatura")
                },
                onClick = viewModel::onSave,
                enabled = state.canSave,
            )
        }
    }
}

@Composable
private fun FieldRow(
    field: ReviewField,
    value: String,
    error: String?,
    showDivider: Boolean,
    onValueChange: (String) -> Unit,
) {
    val bold = field == ReviewField.TOTAL
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                field.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (error != null) FaturaColors.Danger else FaturaColors.Muted,
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) {
                    Text(
                        if (field == ReviewField.MERCHANT) tr("Add a name", "Adicionar nome") else "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = FaturaColors.Subtle,
                        textAlign = TextAlign.End,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    cursorBrush = SolidColor(FaturaColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.kind) {
                            ReviewField.Kind.AMOUNT -> KeyboardType.Decimal
                            ReviewField.Kind.NUMBER -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                        capitalization = if (field == ReviewField.MERCHANT) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                    ),
                    textStyle = TextStyle(
                        color = FaturaColors.Ink,
                        fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = if (bold) 14.5.sp else 13.5.sp,
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = FaturaColors.Danger,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FaturaColors.Divider))
        }
    }
}

/** Drawn rather than a "✓" glyph, which the web build's bundled font doesn't have. */
@Composable
private fun CheckMark() {
    val success = FaturaColors.Success
    androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.55f)
            lineTo(size.width * 0.4f, size.height * 0.85f)
            lineTo(size.width * 0.92f, size.height * 0.2f)
        }
        drawPath(
            path,
            color = success,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.width * 0.18f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
