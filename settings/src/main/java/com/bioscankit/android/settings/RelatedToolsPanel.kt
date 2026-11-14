package com.bioscankit.android.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioscankit.android.design.AdaptiveColor
import com.bioscankit.android.design.BioScanDesign
import java.util.Locale

/**
 * 面板需要的颜色。宿主可以传自己的结果页配色，避免公共组件绑死某个 App 的视觉。
 * 默认值对应 iNature 结果页的 database 面板。
 */
data class RelatedToolsPalette(
    val panelBackground: AdaptiveColor,
    val panelBorder: AdaptiveColor,
    val rowBackground: AdaptiveColor,
    val retakeBackground: AdaptiveColor,
    val accent: Color,
    val primaryText: AdaptiveColor,
    val secondaryText: AdaptiveColor,
    val tertiaryText: AdaptiveColor,
) {
    companion object {
        val Result = RelatedToolsPalette(
            panelBackground = AdaptiveColor(Color(0xFFFAFCFD), Color(0xFF1A212B)),
            panelBorder = AdaptiveColor(Color(0xFFE6EFFA), Color(0xFF334252)),
            rowBackground = AdaptiveColor(Color(0xFFE0EFFF), Color(0xFF29405C)),
            retakeBackground = AdaptiveColor(Color(0xFFFAFCFD), Color(0xFF1A212B)),
            accent = Color(0xFF476FBD),
            primaryText = AdaptiveColor(Color(0xFF0D1A33), Color.White.copy(alpha = 0.94f)),
            secondaryText = AdaptiveColor(Color(0xFF576883), Color(0xFF94A3BB)),
            tertiaryText = AdaptiveColor(Color(0xFF707F99), Color(0xFF8293AA)),
        )
    }
}

/**
 * 与 iOS `RelatedToolsPanel` 对齐的推荐面板。
 *
 * - [RelatedToolsRecommendation.Variant.Related]：结果可信，作为脚注放在概览之后。
 * - [RelatedToolsRecommendation.Variant.Caution]：结果不可信，放在物种标题之前，
 *   显示低置信度说明与重拍入口。
 */
@Composable
fun RelatedToolsPanel(
    recommendation: RelatedToolsRecommendation,
    onOpen: (RelatedTool) -> Unit,
    modifier: Modifier = Modifier,
    palette: RelatedToolsPalette = RelatedToolsPalette.Result,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    rowCornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    onRetake: (() -> Unit)? = null,
) {
    val isDark = BioScanDesign.isDark
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.panelBackground.resolve(isDark), shape)
            .border(1.dp, palette.panelBorder.resolve(isDark), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(recommendation, palette, isDark)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recommendation.tools.forEach { tool ->
                ToolRow(
                    tool = tool,
                    palette = palette,
                    isDark = isDark,
                    rowCornerRadius = rowCornerRadius,
                    onClick = { onOpen(tool) },
                )
            }
            if (recommendation.variant == RelatedToolsRecommendation.Variant.Caution && onRetake != null) {
                RetakeRow(
                    palette = palette,
                    isDark = isDark,
                    rowCornerRadius = rowCornerRadius,
                    onClick = onRetake,
                )
            }
        }
    }
}

@Composable
private fun Header(
    recommendation: RelatedToolsRecommendation,
    palette: RelatedToolsPalette,
    isDark: Boolean,
) {
    if (recommendation.variant == RelatedToolsRecommendation.Variant.Caution) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(palette.rowBackground.resolve(isDark), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Eyebrow(
                    text = stringResource(
                        R.string.related_low_confidence_eyebrow_format,
                        "${(recommendation.confidence * 100).toInt()}%",
                    ),
                    color = palette.tertiaryText.resolve(isDark),
                )
                Text(
                    text = stringResource(R.string.related_low_confidence_title),
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText.resolve(isDark),
                )
                Text(
                    text = stringResource(R.string.related_low_confidence_detail),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.secondaryText.resolve(isDark),
                )
            }
        }
    } else {
        Eyebrow(
            text = stringResource(R.string.related_eyebrow),
            color = palette.tertiaryText.resolve(isDark),
        )
    }
}

@Composable
private fun Eyebrow(text: String, color: Color) {
    Text(
        text = text.uppercase(Locale.ROOT),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = color,
    )
}

@Composable
private fun ToolRow(
    tool: RelatedTool,
    palette: RelatedToolsPalette,
    isDark: Boolean,
    rowCornerRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(rowCornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(palette.rowBackground.resolve(isDark), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(tool.imageName, palette, isDark)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tool.action,
                fontSize = 13.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText.resolve(isDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tool.detail,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText.resolve(isDark),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun RetakeRow(
    palette: RelatedToolsPalette,
    isDark: Boolean,
    rowCornerRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(rowCornerRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(palette.retakeBackground.resolve(isDark), shape)
            .border(1.dp, palette.panelBorder.resolve(isDark), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(palette.panelBorder.resolve(isDark).copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CenterFocusStrong,
                contentDescription = null,
                tint = palette.secondaryText.resolve(isDark),
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.related_retake_action),
                fontSize = 13.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText.resolve(isDark),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.related_retake_detail),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.secondaryText.resolve(isDark),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.tertiaryText.resolve(isDark),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AppIcon(
    imageName: String?,
    palette: RelatedToolsPalette,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val bitmap = remember(imageName) {
        imageName?.let { name ->
            runCatching {
                context.assets.open("bioscan_shared/Icons/$name.png")
                    .use(BitmapFactory::decodeStream)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(palette.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
