package com.bioscankit.android.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全局统一的对话框外壳：圆角纸张卡片、细边框、底部纵向操作按钮。
 * 保留 Material 的语义与无障碍行为，只替换视觉层。
 */
@Composable
fun BioScanDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = BioScanDesign.colors
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(28.dp),
                color = colors.cardBackground,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 24.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon?.let {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    color = (if (destructive) colors.danger else colors.accent).copy(alpha = 0.12f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                tint = if (destructive) colors.danger else colors.accent,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.Black,
                        ),
                        color = colors.primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (content != null) {
                        Spacer(Modifier.height(20.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = content,
                        )
                    }
                    if (confirmLabel != null || dismissLabel != null) {
                        Spacer(Modifier.height(24.dp))
                    }
                    if (confirmLabel != null) {
                        Button(
                            onClick = { onConfirm?.invoke() ?: onDismissRequest() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = confirmEnabled,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (destructive) colors.danger else colors.accent,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            ),
                        ) {
                            Text(
                                text = confirmLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (dismissLabel != null) {
                        TextButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                        ) {
                            Text(
                                text = dismissLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 统一的圆角菜单，用于图鉴操作等轻量弹层。 */
@Composable
fun BioScanDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BioScanDesign.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.elevatedCardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
fun BioScanDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val colors = BioScanDesign.colors
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (destructive) colors.danger else colors.primaryText,
            )
        },
        onClick = onClick,
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (destructive) colors.danger else colors.secondaryText,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    )
}
