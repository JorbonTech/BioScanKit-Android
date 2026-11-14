package com.bioscankit.android.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 相机权限引导页，与 iOS `CameraScreen.permissionContent(isDenied:)` 对齐。
 *
 * 文案优先使用宿主传入的 [CameraScreenConfiguration]；未传时回落到组件内置资源，
 * 因此宿主无需再为公共相机页各写一份本地化文案。
 *
 * - 未决：`permissionTitle` + `permissionMessage` + 「继续」
 * - 已拒绝：`permissionTitle` + `deniedMessage` + 「打开设置」
 */
@Composable
fun CameraPermissionIntro(
    configuration: CameraScreenConfiguration = CameraScreenConfiguration.INature,
    isDenied: Boolean,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    continueTitle: String? = null,
    openSettingsTitle: String? = null,
    choosePhotoTitle: String? = null,
    cancelTitle: String? = null,
    onChoosePhoto: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val resolvedContinueTitle = continueTitle ?: stringResource(R.string.bioscan_capture_continue)
    val resolvedOpenSettingsTitle = openSettingsTitle ?: stringResource(R.string.bioscan_capture_open_settings)
    val resolvedChoosePhotoTitle = choosePhotoTitle ?: stringResource(R.string.bioscan_capture_choose_from_photos)
    val resolvedCancelTitle = cancelTitle ?: stringResource(R.string.bioscan_capture_cancel)

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                imageVector = if (isDenied) Icons.Filled.VideocamOff else Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = configuration.theme.accent,
                modifier = Modifier.size(48.dp),
            )

            Text(
                text = configuration.permissionTitle
                    ?: stringResource(R.string.bioscan_capture_permission_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (isDenied) {
                    configuration.deniedMessage ?: stringResource(R.string.bioscan_capture_permission_denied)
                } else {
                    configuration.permissionMessage ?: stringResource(R.string.bioscan_capture_permission_message)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
            )

            Surface(
                onClick = if (isDenied) onOpenSettings else onContinue,
                shape = RoundedCornerShape(14.dp),
                color = configuration.theme.accent,
                contentColor = Color.White,
            ) {
                Text(
                    text = if (isDenied) resolvedOpenSettingsTitle else resolvedContinueTitle,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }

            if (configuration.supportsPhotoLibrary && onChoosePhoto != null) {
                TextButton(onClick = onChoosePhoto) {
                    Text(resolvedChoosePhotoTitle, color = Color.White)
                }
            }

            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text(resolvedCancelTitle, color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}
