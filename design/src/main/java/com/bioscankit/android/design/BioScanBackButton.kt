package com.bioscankit.android.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 全局统一返回按钮，对齐 iOS `bioScanPushNavigation` 的导航栏返回键：
 * 36dp 白色圆底（`systemBackground`）+ 柔和投影 + 20dp chevron，无描边。
 */
@Composable
fun BioScanBackButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = BioScanDesign.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        // iOS 亮色为白色圆底；暗色改用 secondarySystemBackground，避免在纯黑页面上看不见圆底。
        color = if (BioScanDesign.isDark) colors.secondarySystemBackground else colors.systemBackground,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = contentDescription,
                tint = colors.primaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
