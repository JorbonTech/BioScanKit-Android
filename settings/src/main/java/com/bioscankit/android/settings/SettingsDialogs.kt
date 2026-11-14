package com.bioscankit.android.settings

import androidx.compose.runtime.Composable
import com.bioscankit.android.design.BioScanDialog

@Composable
fun SettingsMessageDialog(
    title: String,
    message: String,
    confirmTitle: String,
    onDismiss: () -> Unit,
) {
    BioScanDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmLabel = confirmTitle,
        onConfirm = onDismiss,
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
    BioScanDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        destructive = destructive,
        confirmLabel = confirmTitle,
        onConfirm = onConfirm,
        dismissLabel = dismissTitle,
    )
}
