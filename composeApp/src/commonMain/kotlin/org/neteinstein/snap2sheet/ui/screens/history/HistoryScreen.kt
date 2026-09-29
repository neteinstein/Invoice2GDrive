package org.neteinstein.snap2sheet.ui.screens.history

import org.neteinstein.snap2sheet.platform.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.neteinstein.snap2sheet.ui.components.FaturaTopBar
import org.neteinstein.snap2sheet.ui.components.InvoiceDetailSheet
import org.neteinstein.snap2sheet.ui.components.InvoiceRow
import org.neteinstein.snap2sheet.ui.components.SectionTitle
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(FaturaColors.Surface).navigationBarsPadding()) {
        FaturaTopBar(title = tr("History", "Histórico"), onBack = onBack)

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(HistoryFilter.entries) { filter ->
                FilterChip(text = filter.label, selected = state.filter == filter, onClick = { viewModel.setFilter(filter) })
            }
        }

        if (state.isEmpty) {
            Text(
                if (state.filter == HistoryFilter.ALL) tr("No invoices yet. Scan one from Home.", "Ainda sem faturas. Digitalize uma a partir do Início.") else tr("Nothing here.", "Nada aqui."),
                style = MaterialTheme.typography.bodySmall,
                color = FaturaColors.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.rows) { row ->
                when (row) {
                    is HistoryRow.Header -> Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        SectionTitle(text = row.label)
                    }
                    is HistoryRow.Item -> Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        InvoiceRow(
                            invoice = row.item.invoice,
                            subtitle = row.item.subtitle,
                            onClick = { viewModel.actions.open(row.item.invoice.id) },
                        )
                    }
                }
            }
        }
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
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) FaturaColors.Accent else FaturaColors.Surface)
            .border(1.5.dp, if (selected) FaturaColors.Accent else FaturaColors.Border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) FaturaColors.Surface else FaturaColors.Muted,
        )
    }
}
