package com.bioscankit.android.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsPageMetrics(
    val usesTwoColumnLayout: Boolean,
    val contentMaxWidthDp: Int,
    val horizontalPaddingDp: Int,
    val columnSpacingDp: Int,
)

enum class SettingsAccessory {
    None,
    Navigation,
    External,
}

@Immutable
data class SettingsSegmentOption(
    val id: String,
    val title: String,
)

sealed interface SettingsMembershipState {
    data object Lifetime : SettingsMembershipState

    /**
     * 终身会员卡片，文案可由宿主覆盖；为 null 时使用库内默认英文文案，
     * 保持与旧调用点（iNature / NatureEar）兼容。
     */
    data class LifetimeCopy(
        val title: String,
        val subtitle: String,
    ) : SettingsMembershipState

    data class Upgrade(
        val remainingScans: Int = 0,
        val remainingText: String? = null,
        val actionTitle: String? = null,
        val footnote: String? = null,
    ) : SettingsMembershipState
}

sealed interface RestoreResult {
    data object Success : RestoreResult
    data object NotFound : RestoreResult
    data class Error(val message: String) : RestoreResult
}

sealed interface SettingsEvent {
    data class RowTapped(val id: String) : SettingsEvent
    data object Shown : SettingsEvent
    data class AppearanceChanged(val id: String) : SettingsEvent
    data object LanguageTapped : SettingsEvent
    data object LanguagePackTapped : SettingsEvent
    data object PrivacyPolicyTapped : SettingsEvent
    data object TermsOfUseTapped : SettingsEvent
    data object FeedbackTapped : SettingsEvent
    data object RestoreTapped : SettingsEvent
    data class RestoreFinished(val result: RestoreResult) : SettingsEvent
    data object RestoreFailed : SettingsEvent
    data object RateAppTapped : SettingsEvent
    data class RecommendedAppTapped(val id: String) : SettingsEvent
    data object VersionTapped : SettingsEvent
}

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onRestore: suspend () -> RestoreResult = { RestoreResult.NotFound },
    val onOpenSystemSettings: () -> Unit = {},
    val onOpenLanguageSettings: (() -> Unit)? = null,
    val onOpenLanguagePackSettings: (() -> Unit)? = null,
    val onOpenPrivacyPolicy: () -> Unit = {},
    val onOpenUserAgreement: () -> Unit = {},
    val onSendFeedback: () -> Unit = {},
    val onRateApp: () -> Unit = {},
    val onOpenRecommendedApp: ((RecommendedApp) -> Unit)? = null,
    val track: (SettingsEvent) -> Unit = {},
)
