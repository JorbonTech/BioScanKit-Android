package com.bioscankit.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bioscankit.android.design.BioScanBackButton
import com.bioscankit.android.design.BioScanDesign
import com.bioscankit.android.design.BioScanDesignSystem
import com.bioscankit.android.design.BioScanTheme

/** 语言包条目状态，宿主把下载器/管理器状态映射到这里。 */
enum class LanguagePackStatus {
    BuiltIn,
    Active,
    Installed,
    Available,
    Downloading,
    Installing,
    Paused,
    Failed,
}

/**
 * 语言包页面的展示模型。
 *
 * 公共组件不依赖任何具体下载实现；宿主负责下载、校验和切换，
 * 只把当前状态映射成 [LanguagePackItem]。
 */
data class LanguagePackItem(
    val id: String,
    val title: String,
    val status: LanguagePackStatus,
    /** 已格式化的大小，例如 “5.9 MB”；仅 Available 时用于按钮。 */
    val sizeText: String? = null,
    /** 0..1；Downloading 时用于进度条。 */
    val progress: Float? = null,
    /** 宿主提供的行内错误/提示，例如断网、校验失败。 */
    val detail: String? = null,
    /** 是否展示 “Remove download”。 */
    val canDelete: Boolean = false,
)

data class LanguagePackScreenActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onUse: (String) -> Unit = {},
    val onDownload: (String) -> Unit = {},
    val onCancelDownload: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
)

/**
 * 共享语言包管理页，布局与 iNature iOS `V7LanguagePackManagerView` 对齐。
 *
 * 当前语言、条目状态、进度和错误都来自宿主；组件只负责展示与交互。
 */
@Composable
fun LanguagePackScreen(
    entries: List<LanguagePackItem>,
    isLoading: Boolean,
    currentLanguageName: String?,
    actions: LanguagePackScreenActions,
    modifier: Modifier = Modifier,
    message: String? = null,
    theme: BioScanTheme = BioScanTheme.INature,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    BioScanDesignSystem(theme = theme, darkTheme = darkTheme) {
        val colors = BioScanDesign.colors
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BioScanBackButton(
                    onClick = actions.onBack,
                    contentDescription = stringResource(R.string.bioscan_settings_back),
                )
                SettingsItemTitle(
                    title = stringResource(R.string.language_pack_title),
                    subtitle = stringResource(R.string.language_pack_subtitle),
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
            }

            currentLanguageName?.let { name ->
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingsItemTitle(
                            title = stringResource(R.string.language_pack_current, name),
                        )
                        Text(
                            text = stringResource(R.string.language_pack_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                        )
                    }
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.language_pack_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryText,
                    )
                }
            }

            entries.forEach { entry ->
                LanguagePackCard(
                    entry = entry,
                    actions = actions,
                )
            }

            if (!isLoading && entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.language_pack_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }

            Text(
                text = stringResource(R.string.language_pack_background_note),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
            )
            Text(
                text = stringResource(R.string.language_pack_note),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
            )
            TextButton(onClick = actions.onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.language_pack_refresh))
            }
        }
    }
}

@Composable
private fun LanguagePackCard(
    entry: LanguagePackItem,
    actions: LanguagePackScreenActions,
) {
    val colors = BioScanDesign.colors
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primaryText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = statusLabel(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }

            if (entry.status == LanguagePackStatus.Downloading) {
                val progress = entry.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = stringResource(
                        R.string.language_pack_downloading,
                        ((entry.progress ?: 0f) * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }

            if (entry.status == LanguagePackStatus.Installing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            entry.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguagePackRowAction(
                    entry = entry,
                    actions = actions,
                    modifier = Modifier.weight(1f),
                )
                if (entry.canDelete) {
                    TextButton(onClick = { actions.onDelete(entry.id) }) {
                        Text(stringResource(R.string.language_pack_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePackRowAction(
    entry: LanguagePackItem,
    actions: LanguagePackScreenActions,
    modifier: Modifier = Modifier,
) {
    when (entry.status) {
        LanguagePackStatus.Active -> Unit
        LanguagePackStatus.BuiltIn,
        LanguagePackStatus.Installed,
        -> TextButton(
            onClick = { actions.onUse(entry.id) },
            modifier = modifier,
        ) {
            Text(stringResource(R.string.language_pack_use))
        }

        LanguagePackStatus.Available -> TextButton(
            onClick = { actions.onDownload(entry.id) },
            modifier = modifier,
        ) {
            Text(
                if (entry.sizeText.isNullOrBlank()) {
                    stringResource(R.string.language_pack_download)
                } else {
                    stringResource(R.string.language_pack_download_action, entry.sizeText)
                },
            )
        }

        LanguagePackStatus.Paused -> TextButton(
            onClick = { actions.onDownload(entry.id) },
            modifier = modifier,
        ) {
            Text(stringResource(R.string.language_pack_resume))
        }

        LanguagePackStatus.Failed -> TextButton(
            onClick = { actions.onDownload(entry.id) },
            modifier = modifier,
        ) {
            Text(stringResource(R.string.language_pack_retry))
        }

        LanguagePackStatus.Downloading,
        LanguagePackStatus.Installing,
        -> TextButton(
            onClick = { actions.onCancelDownload(entry.id) },
            modifier = modifier,
        ) {
            Text(stringResource(R.string.language_pack_cancel))
        }
    }
}

@Composable
private fun statusLabel(entry: LanguagePackItem): String = when (entry.status) {
    LanguagePackStatus.BuiltIn -> stringResource(R.string.language_pack_built_in)
    LanguagePackStatus.Active -> stringResource(R.string.language_pack_active)
    LanguagePackStatus.Installed -> stringResource(R.string.language_pack_installed)
    LanguagePackStatus.Available -> stringResource(R.string.language_pack_available)
    LanguagePackStatus.Downloading -> stringResource(R.string.language_pack_downloading, ((entry.progress ?: 0f) * 100).toInt())
    LanguagePackStatus.Installing -> stringResource(R.string.language_pack_installing)
    LanguagePackStatus.Paused -> stringResource(R.string.language_pack_paused)
    LanguagePackStatus.Failed -> stringResource(R.string.language_pack_failed)
}
