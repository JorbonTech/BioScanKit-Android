package com.bioscankit.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import com.bioscankit.android.design.BioScanDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioscankit.android.settings.R
import com.bioscankit.android.design.BioScanDesign
import com.bioscankit.android.design.BioScanDesignSystem
import com.bioscankit.android.design.BioScanTheme

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
    theme: BioScanTheme = BioScanTheme.Neutral,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(BioScanAppLanguage.selectedIdentifier(context)) }
    var showRestartDialog by remember { mutableStateOf(false) }

    BioScanDesignSystem(theme = theme, darkTheme = darkTheme) {
        val colors = BioScanDesign.colors
        val palette = settingsPalette()
        val currentLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(palette.pageTop, palette.pageBottom)),
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsTopBar(
                title = copy.title,
                onBack = onBack,
            )

            SettingsCard {
                SettingsRow(
                    title = copy.followSystem,
                    // 与 iOS 一致：显示系统语言的自称（zh-Hans → 简体中文）。
                    subtitle = BioScanAppLanguage.displayName(BioScanAppLanguage.systemIdentifier()),
                    detail = null,
                    onClick = {
                        if (selected.isNotEmpty()) {
                            selected = ""
                            BioScanAppLanguage.select(context, "")
                            onSelected("")
                            showRestartDialog = BioScanAppLanguage.requiresRestart()
                        }
                    },
                    leading = { SettingsIcon(imageVector = Icons.Rounded.Settings) },
                    trailing = {
                        LanguageSelectionMark(selected = selected.isEmpty())
                    },
                )
            }

            SettingsSection(copy.availableLanguagesTitle) {
                BioScanAppLanguage.availableIdentifiers.forEachIndexed { index, identifier ->
                    if (index > 0) {
                        // iOS: Divider().padding(.leading, 56)
                        SettingsDivider(Modifier.padding(start = 56.dp))
                    }
                    val nativeName = BioScanAppLanguage.displayName(identifier)
                    // 与 iOS `locale.localizedString(forIdentifier:)` 对齐：
                    // 同一语言下该语言的称呼就是它的自称，此时不重复显示副标题。
                    val translatedName = if (identifier.substringBefore('-') == currentLocale.language) {
                        null
                    } else {
                        java.util.Locale.forLanguageTag(identifier)
                            .getDisplayLanguage(currentLocale)
                            .takeIf { it.isNotBlank() && it != nativeName }
                    }
                    SettingsRow(
                        title = nativeName,
                        subtitle = translatedName,
                        detail = null,
                        onClick = {
                            if (selected != identifier) {
                                selected = identifier
                                BioScanAppLanguage.select(context, identifier)
                                onSelected(identifier)
                                showRestartDialog = BioScanAppLanguage.requiresRestart()
                            }
                        },
                        leading = { SettingsIcon(imageVector = Icons.Rounded.Translate) },
                        trailing = {
                            LanguageSelectionMark(selected = selected == identifier)
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = copy.informationText,
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        if (showRestartDialog) {
            BioScanDialog(
                onDismissRequest = { showRestartDialog = false },
                title = copy.languageChangedTitle,
                message = copy.languageChangedMessage,
                confirmLabel = copy.restartNow,
                onConfirm = {
                    showRestartDialog = false
                    (context as? android.app.Activity)?.recreate()
                },
                dismissLabel = copy.restartLater,
            )
        }
    }
}

/** 与 iOS `AppLanguageView.languageRow` 的 checkmark.circle.fill / circle 对齐。 */
@Composable
private fun LanguageSelectionMark(selected: Boolean) {
    val colors = BioScanDesign.colors
    Icon(
        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) colors.accent else colors.secondaryText.copy(alpha = 0.35f),
        modifier = Modifier.size(22.dp),
    )
}
