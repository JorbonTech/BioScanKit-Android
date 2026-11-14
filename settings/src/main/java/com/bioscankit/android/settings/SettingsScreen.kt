package com.bioscankit.android.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bioscankit.android.design.BioScanDesign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bioscankit.android.settings.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun defaultSettingsScreenCopy(): SettingsScreenCopy = SettingsScreenCopy(
    title = stringResource(R.string.bioscan_settings_title),
    backContentDescription = stringResource(R.string.bioscan_settings_back),
    general = stringResource(R.string.bioscan_settings_general),
    appearance = stringResource(R.string.bioscan_settings_appearance),
    support = stringResource(R.string.bioscan_settings_support),
    systemSettings = stringResource(R.string.bioscan_settings_system_settings),
    systemSettingsSubtitle = stringResource(R.string.bioscan_settings_system_settings_subtitle),
    privacy = stringResource(R.string.bioscan_settings_privacy),
    privacySubtitle = stringResource(R.string.bioscan_settings_privacy_subtitle),
    terms = stringResource(R.string.bioscan_settings_terms),
    termsSubtitle = stringResource(R.string.bioscan_settings_terms_subtitle),
    feedback = stringResource(R.string.bioscan_settings_feedback),
    feedbackSubtitle = stringResource(R.string.bioscan_settings_feedback_subtitle),
    language = stringResource(R.string.bioscan_language_title),
    languageSubtitle = null,
    languagePack = stringResource(R.string.bioscan_settings_language_pack),
    languagePackSubtitle = stringResource(R.string.bioscan_settings_language_pack_subtitle),
    restore = stringResource(R.string.bioscan_settings_restore),
    restoreSubtitle = stringResource(R.string.bioscan_settings_restore_subtitle),
    restoring = stringResource(R.string.bioscan_settings_restoring),
    rate = stringResource(R.string.bioscan_settings_rate),
    rateSubtitle = stringResource(R.string.bioscan_settings_rate_subtitle),
    restoreSuccessTitle = stringResource(R.string.bioscan_settings_restore_success_title),
    restoreSuccessMessage = stringResource(R.string.bioscan_settings_restore_success_message),
    restoreNotFoundTitle = stringResource(R.string.bioscan_settings_restore_not_found_title),
    restoreNotFoundMessage = stringResource(R.string.bioscan_settings_restore_not_found_message),
    restoreErrorTitle = stringResource(R.string.bioscan_settings_restore_error_title),
    restoreErrorMessage = stringResource(R.string.bioscan_settings_restore_error_message),
    ok = stringResource(R.string.bioscan_settings_ok),
    developerOptionsUnlocked = stringResource(R.string.bioscan_settings_developer_unlocked),
    recommendedApps = stringResource(R.string.bioscan_settings_recommended_apps),
)

data class SettingsScreenCopy(
    val title: String = "Settings",
    val backContentDescription: String = "Back",
    val general: String = "General",
    val appearance: String = "Appearance",
    val support: String = "Support",
    val systemSettings: String = "System Settings",
    val systemSettingsSubtitle: String = "Manage permissions and app settings",
    val privacy: String = "Privacy Policy",
    val privacySubtitle: String = "How we collect and use data",
    val terms: String = "Terms of Use",
    val termsSubtitle: String = "Terms of use and responsibilities",
    val feedback: String = "Send Feedback",
    val feedbackSubtitle: String = "Help us improve the app",
    val language: String = "Language",
    val languageSubtitle: String? = null,
    val languagePack: String = "Encyclopedia languages",
    val languagePackSubtitle: String = "Download and switch offline species languages",
    val restore: String = "Restore purchase",
    val restoreSubtitle: String = "Restore a previous purchase",
    val restoring: String = "Checking the store…",
    val rate: String = "Rate App",
    val rateSubtitle: String = "Leave a rating on Google Play",
    val restoreSuccessTitle: String = "Purchase restored",
    val restoreSuccessMessage: String = "Your purchase is active on this device.",
    val restoreNotFoundTitle: String = "Nothing to restore",
    val restoreNotFoundMessage: String = "No eligible purchase was found for this store account.",
    val restoreErrorTitle: String = "Restore failed",
    val restoreErrorMessage: String = "Unable to restore purchases right now. Please try again.",
    val ok: String = "OK",
    val developerOptionsUnlocked: String = "Developer options unlocked",
    val recommendedApps: String = "OTHER APPS",
)

data class SettingsScreenVisibility(
    val showsAppearance: Boolean = true,
    val showsLanguage: Boolean = false,
    val showsLanguagePack: Boolean = false,
    val showsSystemSettings: Boolean = true,
    val showsSupportSection: Boolean = true,
    val showsRestorePurchase: Boolean = true,
    val showsRateApp: Boolean = true,
    val showsRecommendedApps: Boolean = true,
)

data class SettingsLibraryConfig(
    val copy: SettingsLibraryCopy,
    val isHistoryEmpty: Boolean,
    val onOpenHistory: () -> Unit,
    val onClearHistory: () -> Unit,
    val isClearingHistory: Boolean = false,
)

/** High-level shared Settings surface matching the Swift BioScanSettings entry point. */
@Composable
fun SettingsScreen(
    versionText: String,
    appearanceOptions: List<SettingsSegmentOption>,
    selectedAppearanceId: String,
    onAppearanceSelected: (String) -> Unit,
    membershipState: SettingsMembershipState,
    onMembershipAction: (() -> Unit)?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    copy: SettingsScreenCopy = defaultSettingsScreenCopy(),
    visibility: SettingsScreenVisibility = SettingsScreenVisibility(),
    library: SettingsLibraryConfig? = null,
    recommendedApps: List<RecommendedApp> = emptyList(),
    recommendedAppIcon: (@Composable (RecommendedApp) -> Unit)? = null,
    developerUnlockTapCount: Int? = null,
    extra: @Composable ColumnScope.(SettingsPageMetrics) -> Unit = {},
    developerOptions: @Composable ColumnScope.() -> Unit = {},
    membershipActionBackground: Color? = null,
    membershipActionContentColor: Color? = null,
) {
    CanonicalSettingsScreen(
        versionText = versionText,
        appearanceOptions = appearanceOptions,
        selectedAppearanceId = selectedAppearanceId,
        onAppearanceSelected = onAppearanceSelected,
        membershipState = membershipState,
        onMembershipAction = onMembershipAction,
        actions = actions,
        modifier = modifier,
        copy = copy,
        visibility = visibility,
        library = library,
        recommendedApps = recommendedApps,
        recommendedAppIcon = recommendedAppIcon,
        developerUnlockTapCount = developerUnlockTapCount,
        extra = extra,
        developerOptions = developerOptions,
        membershipActionBackground = membershipActionBackground,
        membershipActionContentColor = membershipActionContentColor,
    )
}

@Composable
private fun CanonicalSettingsScreen(
    versionText: String,
    appearanceOptions: List<SettingsSegmentOption>,
    selectedAppearanceId: String,
    onAppearanceSelected: (String) -> Unit,
    membershipState: SettingsMembershipState,
    onMembershipAction: (() -> Unit)?,
    actions: SettingsActions,
    modifier: Modifier,
    copy: SettingsScreenCopy,
    visibility: SettingsScreenVisibility,
    library: SettingsLibraryConfig?,
    recommendedApps: List<RecommendedApp>,
    recommendedAppIcon: (@Composable (RecommendedApp) -> Unit)?,
    developerUnlockTapCount: Int?,
    extra: @Composable ColumnScope.(SettingsPageMetrics) -> Unit,
    developerOptions: @Composable ColumnScope.() -> Unit,
    membershipActionBackground: Color?,
    membershipActionContentColor: Color?,
) {
    val scope = rememberCoroutineScope()
    var isRestoring by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { actions.track(SettingsEvent.Shown) }
    var restoreDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var versionTapCount by rememberSaveable { mutableStateOf(0) }
    var developerOptionsUnlocked by rememberSaveable { mutableStateOf(false) }
    restoreDialog?.let { (title, message) ->
        SettingsMessageDialog(title, message, copy.ok) { restoreDialog = null }
    }
    // 与 iOS 一致：恢复购买期间显示居中浮层（ProgressView + regularMaterial 圆角底）。
    if (isRestoring) {
        SettingsRestoringOverlay(label = copy.restoring)
    }

    SettingsPageContainer(
        versionText = if (developerOptionsUnlocked) {
            "$versionText • ${copy.developerOptionsUnlocked}"
        } else {
            versionText
        },
        modifier = modifier,
        onVersionTapped = developerUnlockTapCount?.let { requiredTaps ->
            {
                actions.track(SettingsEvent.VersionTapped)
                if (!developerOptionsUnlocked) {
                    versionTapCount += 1
                    if (versionTapCount >= requiredTaps.coerceAtLeast(1)) {
                        developerOptionsUnlocked = true
                    }
                }
            }
        },
    ) { metrics ->
        SettingsTopBar(
            title = copy.title,
            onBack = actions.onBack,
            )

        SettingsMembershipCard(
            state = membershipState,
            onAction = onMembershipAction,
            actionBackground = membershipActionBackground,
            actionContentColor = membershipActionContentColor,
        )

        if (
            visibility.showsAppearance ||
            visibility.showsLanguage ||
            visibility.showsLanguagePack ||
            visibility.showsSystemSettings
        ) {
            SettingsSection(copy.general) {
                if (visibility.showsAppearance) {
                    SettingsChoiceField(
                        label = copy.appearance,
                        options = appearanceOptions,
                        selectedId = selectedAppearanceId,
                        onSelected = { id ->
                            onAppearanceSelected(id)
                            actions.track(SettingsEvent.AppearanceChanged(id))
                        },
                    )
                }
                if (visibility.showsLanguage) {
                    if (visibility.showsAppearance) SettingsDivider()
                    SharedSettingsRow(
                        title = copy.language,
                        subtitle = null,
                        icon = Icons.Filled.Language,
                        accessory = SettingsAccessory.Navigation,
                        action = {
                            actions.track(SettingsEvent.LanguageTapped)
                            actions.onOpenLanguageSettings?.invoke()
                        },
                    )
                }
                if (visibility.showsLanguagePack) {
                    if (visibility.showsAppearance || visibility.showsLanguage) SettingsDivider()
                    SharedSettingsRow(
                        title = copy.languagePack,
                        subtitle = copy.languagePackSubtitle,
                        icon = Icons.Filled.Language,
                        action = {
                            actions.track(SettingsEvent.LanguagePackTapped)
                            actions.onOpenLanguagePackSettings?.invoke()
                        },
                    )
                }
                if (visibility.showsSystemSettings) {
                    if (visibility.showsAppearance || visibility.showsLanguage || visibility.showsLanguagePack) {
                        SettingsDivider()
                    }
                    SharedSettingsRow(
                        title = copy.systemSettings,
                        subtitle = copy.systemSettingsSubtitle,
                        icon = Icons.Filled.Settings,
                        action = actions.onOpenSystemSettings,
                    )
                }
            }
        }

        library?.let { config ->
            SettingsLibrarySection(
                copy = config.copy,
                isHistoryEmpty = config.isHistoryEmpty,
                onOpenHistory = config.onOpenHistory,
                onClearHistory = config.onClearHistory,
                isClearingHistory = config.isClearingHistory,
            )
        }

        extra(metrics)

        if (developerOptionsUnlocked) {
            developerOptions()
        }

        if (visibility.showsSupportSection) {
            SettingsSection(copy.support) {
                SharedSettingsRow(copy.privacy, copy.privacySubtitle, Icons.Filled.Policy) {
                    actions.track(SettingsEvent.PrivacyPolicyTapped)
                    actions.onOpenPrivacyPolicy()
                }
                SettingsDivider()
                SharedSettingsRow(copy.terms, copy.termsSubtitle, Icons.AutoMirrored.Filled.OpenInNew) {
                    actions.track(SettingsEvent.TermsOfUseTapped)
                    actions.onOpenUserAgreement()
                }
                SettingsDivider()
                SharedSettingsRow(copy.feedback, copy.feedbackSubtitle, Icons.Filled.Email) {
                    actions.track(SettingsEvent.FeedbackTapped)
                    actions.onSendFeedback()
                }
                if (visibility.showsRestorePurchase) {
                    SettingsDivider()
                    SettingsRow(
                        title = copy.restore,
                        subtitle = if (isRestoring) copy.restoring else copy.restoreSubtitle,
                        accessory = SettingsAccessory.External,
                        enabled = !isRestoring,
                        onClick = {
                            if (isRestoring) return@SettingsRow
                            actions.track(SettingsEvent.RestoreTapped)
                            isRestoring = true
                            scope.launch {
                                try {
                                    val result = actions.onRestore()
                                    restoreDialog = when (result) {
                                        RestoreResult.Success -> copy.restoreSuccessTitle to copy.restoreSuccessMessage
                                        RestoreResult.NotFound -> copy.restoreNotFoundTitle to copy.restoreNotFoundMessage
                                        is RestoreResult.Error -> copy.restoreErrorTitle to result.message
                                    }
                                    actions.track(SettingsEvent.RestoreFinished(result))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Throwable) {
                                    actions.track(SettingsEvent.RestoreFailed)
                                    restoreDialog = copy.restoreErrorTitle to
                                        (error.message?.takeIf(String::isNotBlank) ?: copy.restoreErrorMessage)
                                } finally {
                                    isRestoring = false
                                }
                            }
                        },
                        leading = { SettingsIcon(Icons.Filled.Restore) },
                    )
                }
            }
        }

        if (visibility.showsRateApp) {
            SettingsCard {
                SharedSettingsRow(copy.rate, copy.rateSubtitle, Icons.Filled.Star) {
                    actions.track(SettingsEvent.RateAppTapped)
                    actions.onRateApp()
                }
            }
        }

        if (visibility.showsRecommendedApps && recommendedApps.isNotEmpty()) {
            SettingsSection(copy.recommendedApps) {
                recommendedApps.forEachIndexed { index, app ->
                    if (index > 0) SettingsDivider()
                    SettingsRecommendedAppCard(
                        app = app,
                        onClick = {
                            actions.track(SettingsEvent.RecommendedAppTapped(app.id))
                            actions.onOpenRecommendedApp?.invoke(app)
                        },
                    ) {
                        recommendedAppIcon?.invoke(app) ?: RecommendedAppIconFallback(app)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSettingsRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accessory: SettingsAccessory = SettingsAccessory.External,
    action: () -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        accessory = accessory,
        onClick = action,
        leading = { SettingsIcon(icon) },
    )
}

data class SettingsLibraryCopy(
    val sectionTitle: String = "Library",
    val historyTitle: String = "Identification History",
    val historySubtitle: String,
    val clearHistoryTitle: String = "Clear History",
    val clearConfirmationTitle: String = "Clear all history?",
    val clearConfirmationMessage: String = "Every saved scan and its local images will be removed.",
    val clearActionTitle: String = "Clear",
    val cancelTitle: String = "Cancel",
)

/** Shared history navigation + destructive clear section; persistence remains app-owned. */
@Composable
fun SettingsLibrarySection(
    copy: SettingsLibraryCopy,
    isHistoryEmpty: Boolean,
    onOpenHistory: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    isClearingHistory: Boolean = false,
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        SettingsConfirmationDialog(
            title = copy.clearConfirmationTitle,
            message = copy.clearConfirmationMessage,
            confirmTitle = copy.clearActionTitle,
            dismissTitle = copy.cancelTitle,
            destructive = true,
            onConfirm = { confirmClear = false; onClearHistory() },
            onDismiss = { confirmClear = false },
        )
    }
    SettingsSection(copy.sectionTitle, modifier) {
        SettingsRow(
            title = copy.historyTitle,
            subtitle = copy.historySubtitle,
            accessory = SettingsAccessory.Navigation,
            onClick = onOpenHistory,
            leading = { SettingsIcon(Icons.Filled.Schedule) },
        )
        SettingsDivider()
        SettingsDangerRow(
            title = copy.clearHistoryTitle,
            enabled = !isHistoryEmpty && !isClearingHistory,
            onClick = { confirmClear = true },
            leading = { SettingsIcon(Icons.Filled.DeleteOutline, style = SettingsIconStyle.Danger) },
        )
    }
}

/** 恢复购买浮层，对应 iOS `SettingsScreen` 的 `ProgressView` overlay。 */
@Composable
private fun SettingsRestoringOverlay(label: String) {
    val palette = settingsPalette()
    val colors = BioScanDesign.colors
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(palette.cardBackground)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryText,
            )
        }
    }
}
