package com.bioscankit.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioscankit.android.cloudsync.CloudFavoritesSyncStatus
import com.bioscankit.android.design.BioScanDesign

/** 云收藏区块里两个「清理」动作的文案，对应 iOS 的 `CloudFavoritesActionRow`。 */
data class SettingsCloudActionsCopy(
    val clearLocalTitle: String = "Clear on This Device",
    val clearLocalSubtitle: String = "Keeps your saved items in the cloud",
    val clearLocalConfirmationTitle: String = "Clear saved items on this device?",
    val clearLocalConfirmationMessage: String = "Cloud sync will be turned off. Saved items stored in the cloud will not be deleted.",
    val deleteCloudTitle: String = "Delete from Cloud",
    val deleteCloudSubtitle: String = "Removes saved items from all synced devices",
    val deleteCloudConfirmationTitle: String = "Delete saved items from the cloud?",
    val deleteCloudConfirmationMessage: String = "This removes synced items from the cloud and every device using this app.",
    val confirmTitle: String = "Delete",
    val cancelTitle: String = "Cancel",
)

private enum class CloudAction { ClearLocal, DeleteCloud }

/**
 * 云收藏设置区块，对应 iOS `SettingsCloudFavoritesSectionView`。
 *
 * 只负责展示与回调，同步逻辑由宿主 App 持有的 `CloudFavoritesSyncController` 驱动，
 * 这样 NatureEar / iNature / MrRock 三端共用同一套 UI，但各自接自己的远端实现。
 *
 * 布局与 iOS 对齐：强调色 44dp 图标块 + 15sp 标题 + 带状态图标的一行状态 + 开关；
 * 开关打开后才显示账号 / 立即同步行。
 */
@Composable
fun SettingsCloudFavoritesSection(
    title: String,
    statusText: String,
    accountEmail: String?,
    connectLabel: String,
    syncNowLabel: String,
    isEnabled: Boolean,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
    syncTitle: String = title,
    status: CloudFavoritesSyncStatus = CloudFavoritesSyncStatus.Disabled,
    actionsCopy: SettingsCloudActionsCopy? = null,
    onClearLocal: (() -> Unit)? = null,
    onDeleteFromCloud: (() -> Unit)? = null,
) {
    val colors = BioScanDesign.colors
    var pendingAction by remember { mutableStateOf<CloudAction?>(null) }
    SettingsSection(title = title, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = syncTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.primaryText,
                )
                CloudFavoritesStatusLine(status = status, text = statusText)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                enabled = !isBusy,
            )
        }

        if (isEnabled) {
            SettingsDivider()
            SettingsRow(
                title = accountEmail ?: connectLabel,
                subtitle = null,
                accessory = if (accountEmail == null) {
                    SettingsAccessory.Navigation
                } else {
                    SettingsAccessory.None
                },
                enabled = !isBusy,
                onClick = onConnect,
                leading = { SettingsIcon(Icons.Filled.Person) },
            )
            if (accountEmail != null && status !is CloudFavoritesSyncStatus.Syncing) {
                SettingsDivider()
                SettingsRow(
                    title = syncNowLabel,
                    subtitle = null,
                    accessory = SettingsAccessory.Navigation,
                    enabled = !isBusy,
                    onClick = onSyncNow,
                    leading = { SettingsIcon(Icons.Filled.Refresh) },
                )
            }
        }

        if (actionsCopy != null) {
            SettingsDivider()
            CloudFavoritesActionRow(
                icon = Icons.Filled.Smartphone,
                title = actionsCopy.clearLocalTitle,
                subtitle = actionsCopy.clearLocalSubtitle,
                enabled = onClearLocal != null,
            ) { pendingAction = CloudAction.ClearLocal }

            SettingsDivider(Modifier.padding(start = 46.dp))

            CloudFavoritesActionRow(
                icon = Icons.Filled.CloudOff,
                title = actionsCopy.deleteCloudTitle,
                subtitle = actionsCopy.deleteCloudSubtitle,
                enabled = isEnabled && onDeleteFromCloud != null,
            ) { pendingAction = CloudAction.DeleteCloud }
        }
    }

    val copy = actionsCopy
    val action = pendingAction
    if (copy != null && action != null) {
        val deletingCloud = action == CloudAction.DeleteCloud
        SettingsConfirmationDialog(
            title = if (deletingCloud) {
                copy.deleteCloudConfirmationTitle
            } else {
                copy.clearLocalConfirmationTitle
            },
            message = if (deletingCloud) {
                copy.deleteCloudConfirmationMessage
            } else {
                copy.clearLocalConfirmationMessage
            },
            confirmTitle = copy.confirmTitle,
            dismissTitle = copy.cancelTitle,
            destructive = true,
            onConfirm = {
                pendingAction = null
                if (deletingCloud) onDeleteFromCloud?.invoke() else onClearLocal?.invoke()
            },
            onDismiss = { pendingAction = null },
        )
    }
}

/** 红色 34dp 图标块 + 标题/副标题的动作行，对应 iOS `CloudFavoritesActionRow`。 */
@Composable
private fun CloudFavoritesActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = BioScanDesign.colors
    val tint = if (enabled) colors.danger else colors.secondaryText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(tint.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.primaryText,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = colors.secondaryText,
            )
        }
    }
}

/** 状态行：iOS 为 11–12sp 的图标 + 12sp medium 次要文字。 */
@Composable
private fun CloudFavoritesStatusLine(
    status: CloudFavoritesSyncStatus,
    text: String,
) {
    val colors = BioScanDesign.colors
    val isWorking = status is CloudFavoritesSyncStatus.CheckingAccount ||
        status is CloudFavoritesSyncStatus.Syncing
    val icon: ImageVector? = when (status) {
        is CloudFavoritesSyncStatus.Disabled -> Icons.Filled.Smartphone
        is CloudFavoritesSyncStatus.Synced -> Icons.Filled.CheckCircle
        is CloudFavoritesSyncStatus.Unavailable -> Icons.Filled.Error
        is CloudFavoritesSyncStatus.Failed -> Icons.Filled.Warning
        else -> null
    }
    val tint = when (status) {
        is CloudFavoritesSyncStatus.Synced -> colors.success
        is CloudFavoritesSyncStatus.Unavailable,
        is CloudFavoritesSyncStatus.Failed,
        -> colors.warning
        else -> colors.secondaryText
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isWorking) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                color = colors.accent,
                strokeWidth = 1.5.dp,
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = colors.secondaryText,
            maxLines = 2,
        )
    }
}
