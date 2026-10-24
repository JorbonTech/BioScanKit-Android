package com.bioscankit.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bioscankit.android.settings.R
import com.bioscankit.android.design.BioScanDesign
import com.bioscankit.android.design.BioScanDesignSystem

@Composable
fun defaultAppLanguageCopy(): AppLanguageCopy = AppLanguageCopy(
    title = stringResource(R.string.bioscan_language_title),
    followSystem = stringResource(R.string.bioscan_language_follow_system),
    availableLanguagesTitle = stringResource(R.string.bioscan_language_available),
    informationText = stringResource(R.string.bioscan_language_information),
    languageChangedTitle = stringResource(R.string.bioscan_language_changed_title),
    languageChangedMessage = stringResource(R.string.bioscan_language_changed_message),
    restartNow = stringResource(R.string.bioscan_language_restart_now),
    restartLater = stringResource(R.string.bioscan_language_restart_later),
)

data class AppLanguageCopy(
    val title: String = "App Language",
    val followSystem: String = "Follow System",
    val availableLanguagesTitle: String = "AVAILABLE LANGUAGES",
    val informationText: String = "Offline encyclopedia languages are managed separately.",
    val backContentDescription: String = "Back",
    val languageChangedTitle: String = "Language changed",
    val languageChangedMessage: String = "Restart the app to apply the selected language.",
    val restartNow: String = "Restart now",
    val restartLater: String = "Later",
)

@Composable
fun AppLanguageScreen(
    onBack: () -> Unit,
    onSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    copy: AppLanguageCopy = defaultAppLanguageCopy(),
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(BioScanAppLanguage.selectedIdentifier(context)) }
    var showRestartDialog by remember { mutableStateOf(false) }

    BioScanDesignSystem(darkTheme = darkTheme) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsTopBar(
                title = copy.title,
                onBack = onBack,
                backContent = {
                    SettingsIcon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = copy.backContentDescription,
                    )
                },
            )

            SettingsSection(copy.availableLanguagesTitle) {
                SettingsRow(
                    title = copy.followSystem,
                    subtitle = BioScanAppLanguage.displayName(BioScanAppLanguage.systemIdentifier()),
                    detail = if (selected.isEmpty()) "✓" else null,
                    onClick = {
                        if (selected.isNotEmpty()) {
                            selected = ""
                            BioScanAppLanguage.select(context, "")
                            onSelected("")
                            showRestartDialog = true
                        }
                    },
                    leading = { SettingsIcon(imageVector = Icons.Rounded.Settings) },
                )
            }

            SettingsCard {
                BioScanAppLanguage.availableIdentifiers.forEachIndexed { index, identifier ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        title = BioScanAppLanguage.displayName(identifier),
                        subtitle = identifier,
                        detail = if (selected == identifier) "✓" else null,
                        onClick = {
                            if (selected != identifier) {
                                selected = identifier
                                BioScanAppLanguage.select(context, identifier)
                                onSelected(identifier)
                                showRestartDialog = true
                            }
                        },
                        leading = { SettingsIcon(imageVector = Icons.Rounded.Language) },
                    )
                }
            }

            Text(
                text = copy.informationText,
                color = BioScanDesign.colors.secondaryText,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }

        if (showRestartDialog) {
            AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text(copy.languageChangedTitle) },
                text = { Text(copy.languageChangedMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestartDialog = false
                        (context as? android.app.Activity)?.recreate()
                    }) { Text(copy.restartNow) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestartDialog = false }) { Text(copy.restartLater) }
                },
            )
        }
    }
}
