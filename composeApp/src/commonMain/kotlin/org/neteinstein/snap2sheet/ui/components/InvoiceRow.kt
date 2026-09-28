package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.validation.Nif
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun InvoiceRow(invoice: Invoice, subtitle: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(background = FaturaColors.Background, size = 38.dp, cornerRadius = 10.dp) {
            FaturaIcons.Document(tint = FaturaColors.Muted, size = 18.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                invoice.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = FaturaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FaturaColors.MutedStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatting.euros(invoice.total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = FaturaColors.Ink,
            )
            StatusChip(status = invoice.status, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The merchant's name, or its NIF when the name was never filled in. */
fun Invoice.displayName(): String = merchantName.ifBlank { "NIF ${Nif.format(nifEmitente)}".takeIf { nifEmitente.isNotBlank() } ?: "Invoice" }

fun formatAmount(value: Double): String = Formatting.amount(value)
