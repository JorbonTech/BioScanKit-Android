package com.bioscankit.android.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一空状态：标本圆环 + 标题 + 说明 + 可选主操作。
 * 完整版用于整页，紧凑版用于卡片或书页内部。
 */
@Composable
fun BioScanEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    contained: Boolean = true,
) {
    val colors = BioScanDesign.colors
    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SpecimenOrb(
                icon = icon,
                size = if (compact) 64.dp else 96.dp,
                iconSize = if (compact) 24.dp else 34.dp,
            )
            Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (compact) 17.sp else 20.sp,
                    lineHeight = if (compact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = colors.primaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 13.sp else 13.5.sp,
                    lineHeight = if (compact) 19.5.sp else 20.25.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = if (compact) 240.dp else 268.dp),
            )
            if (actionLabel != null && onAction != null && !compact) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }

    if (!contained) {
        Box(
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        return
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (compact) 300.dp else 340.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 22.dp else 28.dp),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = if (compact) 0.dp else 18.dp,
        ) {
            Box(
                modifier = Modifier.padding(
                    horizontal = if (compact) 20.dp else 24.dp,
                    vertical = if (compact) 20.dp else 26.dp,
                ),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SpecimenOrb(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
) {
    val accent = BioScanDesign.colors.accent
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.06f)),
                    center = Offset(this.size.width * 0.35f, this.size.height * 0.30f),
                    radius = radius * 1.1f,
                ),
                radius = radius,
            )
            drawCircle(
                color = accent.copy(alpha = 0.32f),
                radius = radius - 0.5.dp.toPx(),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                    ),
                ),
            )
            drawCircle(
                color = accent.copy(alpha = 0.10f),
                radius = radius - 9.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}
