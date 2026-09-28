package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

/** A one-field dialog: new spreadsheet name, pasted QR payload. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    singleLine: Boolean = true,
    supportingText: String? = null,
    placeholder: String? = null,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FaturaColors.Surface,
        title = { Text(title, color = FaturaColors.Ink) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it, color = FaturaColors.Subtle) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FaturaColors.Ink,
                    unfocusedTextColor = FaturaColors.Ink,
                    focusedBorderColor = FaturaColors.Accent,
                    focusedLabelColor = FaturaColors.Accent,
                    cursorColor = FaturaColors.Accent,
                ),
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                supportingText = supportingText?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(confirmText, color = FaturaColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FaturaColors.Muted) }
        },
    )
}
