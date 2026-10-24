package com.bioscankit.android.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.bioscankit.android.design.BioScanDesign

@Composable
fun SettingsMessageDialog(
    title: String,
    message: String,
    confirmTitle: String,
    onDismiss: () -> Unit,
) {
    val colors = BioScanDesign.colors
    val palette = settingsPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.secondaryText) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(confirmTitle, color = colors.accent) }
        },
        containerColor = palette.cardBackground,
    )
}

@Composable
fun SettingsConfirmationDialog(
    title: String,
    message: String,
    confirmTitle: String,
    dismissTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = BioScanDesign.colors
    val palette = settingsPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.secondaryText) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmTitle, color = if (destructive) colors.danger else colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissTitle, color = colors.primaryText) }
        },
        containerColor = palette.cardBackground,
    )
}
