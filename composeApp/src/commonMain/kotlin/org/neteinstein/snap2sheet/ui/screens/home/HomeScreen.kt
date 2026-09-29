package org.neteinstein.snap2sheet.ui.screens.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.ui.components.BottomNavBar
import org.neteinstein.snap2sheet.ui.components.BottomNavTab
import org.neteinstein.snap2sheet.ui.components.FaturaCard
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import org.neteinstein.snap2sheet.ui.components.IconBadge
import org.neteinstein.snap2sheet.ui.components.InvoiceDetailSheet
import org.neteinstein.snap2sheet.ui.components.InvoiceRow
import org.neteinstein.snap2sheet.ui.components.SectionTitle
import org.neteinstein.snap2sheet.ui.components.formatAmount
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fatura", style = MaterialTheme.typography.titleLarge, color = FaturaColors.Ink)
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(FaturaColors.AccentSoft).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.account?.initials ?: "?",
                    style = MaterialTheme.typography.labelMedium,
                    color = FaturaColors.Accent,
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.failedInvoiceIds.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                        FailedSavesBanner(
                            count = state.failedInvoiceIds.size,
                            onRetry = { viewModel.actions.retryAllFailed(state.failedInvoiceIds) },
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    FaturaCard(containerColor = FaturaColors.SurfaceMuted, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            tr("THIS MONTH", "ESTE MÊS"),
                            style = MaterialTheme.typography.labelMedium,
                            color = FaturaColors.Muted,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${state.invoicesThisMonth}", style = MaterialTheme.typography.headlineSmall, color = FaturaColors.Ink)
                            Text(tr("invoices ·", "faturas ·"), style = MaterialTheme.typography.bodyMedium, color = FaturaColors.Muted)
                            Text("€${formatAmount(state.totalThisMonth)}", style = MaterialTheme.typography.titleSmall, color = FaturaColors.Ink)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FaturaIcons.Document(tint = FaturaColors.Accent, size = 15.dp)
                            Text(
                                state.primarySpreadsheetName ?: tr("No spreadsheet yet", "Ainda sem folha de cálculo"),
                                style = MaterialTheme.typography.labelMedium,
                                color = FaturaColors.Accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    FaturaCard(
                        containerColor = FaturaColors.AccentSoft,
                        borderColor = FaturaColors.Accent,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onScan),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(background = FaturaColors.Accent, size = 44.dp) {
                                FaturaIcons.Camera(tint = androidx.compose.ui.graphics.Color.White, size = 22.dp)
                            }
                            Column {
                                Text(tr("Scan a new invoice", "Digitalizar nova fatura"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
                                Text(tr("Point your camera at the QR code", "Aponte a câmara ao código QR"), style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    SectionTitle(
                        text = if (state.savingCount > 0) tr("Recent scans · ${state.savingCount} saving", "Digitalizações recentes · ${state.savingCount} a guardar") else tr("Recent scans", "Digitalizações recentes"),
                        trailing = tr("See all", "Ver tudo"),
                        onTrailingClick = onHistory,
                    )
                }
            }

            if (state.recentInvoices.isEmpty()) {
                item {
                    Text(
                        tr("Nothing scanned yet. Your invoices will show up here once they're saved.", "Ainda nada digitalizado. As suas faturas aparecerão aqui depois de guardadas."),
                        style = MaterialTheme.typography.bodySmall,
                        color = FaturaColors.Muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            items(state.recentInvoices, key = { it.invoice.id }) { item ->
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    InvoiceRow(invoice = item.invoice, subtitle = item.subtitle, onClick = { viewModel.actions.open(item.invoice.id) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        BottomNavBar(
            selected = BottomNavTab.HOME,
            onHome = {},
            onHistory = onHistory,
            onSettings = onSettings,
            onScan = onScan,
        )
    }

    state.openInvoice?.let { invoice ->
        InvoiceDetailSheet(
            invoice = invoice,
            onRetry = { viewModel.actions.retry(invoice.id) },
            onDelete = { viewModel.actions.delete(invoice.id) },
            onDismiss = viewModel.actions::close,
        )
    }
}

@Composable
private fun FailedSavesBanner(count: Int, onRetry: () -> Unit) {
    FaturaCard(containerColor = FaturaColors.DangerSoft, borderColor = FaturaColors.DangerSoft, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (count == 1) tr("1 invoice didn't reach its spreadsheet.", "1 fatura não chegou à folha de cálculo.") else tr("$count invoices didn't reach their spreadsheet.", "$count faturas não chegaram à folha de cálculo."),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = FaturaColors.Danger,
                modifier = Modifier.weight(1f),
            )
            Text(
                tr("Retry", "Tentar novamente"),
                style = MaterialTheme.typography.labelMedium,
                color = FaturaColors.Danger,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}
