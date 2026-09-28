package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

@Composable
fun StatusChip(status: InvoiceStatus, modifier: Modifier = Modifier) {
    val (label, foreground, background) = when (status) {
        InvoiceStatus.QUEUED -> Triple("Saving", FaturaColors.Accent, FaturaColors.AccentSoft)
        InvoiceStatus.SYNCED -> Triple("Synced", FaturaColors.Success, FaturaColors.SuccessSoft)
        InvoiceStatus.NEEDS_REVIEW -> Triple("Review", FaturaColors.Warning, FaturaColors.WarningSoft)
        InvoiceStatus.DUPLICATE -> Triple("Duplicate", FaturaColors.Muted, FaturaColors.Background)
        InvoiceStatus.FAILED -> Triple("Failed", FaturaColors.Danger, FaturaColors.DangerSoft)
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = background) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}
