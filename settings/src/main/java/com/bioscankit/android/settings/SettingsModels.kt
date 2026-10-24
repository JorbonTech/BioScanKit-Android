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

    data class Upgrade(
        val remainingScans: Int,
    ) : SettingsMembershipState
}

sealed interface RestoreResult {
    data object Success : RestoreResult
    data object NotFound : RestoreResult
    data class Error(val message: String) : RestoreResult
}

sealed interface SettingsEvent {
    data class RowTapped(val id: String) : SettingsEvent
    data object LanguageTapped : SettingsEvent
    data object LanguagePackTapped : SettingsEvent
    data object RestoreTapped : SettingsEvent
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
    val track: (SettingsEvent) -> Unit = {},
)
