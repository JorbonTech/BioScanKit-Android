package com.bioscankit.android.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioscankit.android.design.BioScanDesign

@Composable
fun PaywallTopBar(
    restoreTitle: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    closeIcon: @Composable () -> Unit,
    /** 顶部渐变背景上需要白色文字；为 null 时用主题强调色。 */
    restoreTint: Color? = null,
) {
    val colors = BioScanDesign.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 与 iOS `CardSelectionPaywallView.header` 对齐：32dp 圆形底（cardBackground 86%）+ 图标
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.cardBackground.copy(alpha = 0.86f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            closeIcon()
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = restoreTitle,
            color = restoreTint ?: colors.accent,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clickable(enabled = !isBusy, onClick = onRestore)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

/** 右上角圆形关闭按钮，对应 iOS `CardSelectionPaywallView.header`。 */
@Composable
fun PaywallCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    closeIcon: @Composable () -> Unit,
) {
    val colors = BioScanDesign.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.cardBackground.copy(alpha = 0.86f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            closeIcon()
        }
    }
}

@Composable
fun PaywallStoreStatusBanner(
    isLoading: Boolean,
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    connectingMessage: String? = null,
    retryTitle: String? = null,
    statusIcon: @Composable () -> Unit,
) {
    if (message.isNullOrBlank() && !isLoading) return
    val colors = BioScanDesign.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.cardBackground, RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
        } else {
            statusIcon()
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (isLoading) {
                connectingMessage ?: stringResource(R.string.bioscan_paywall_connecting)
            } else {
                message.orEmpty()
            },
            color = colors.primaryText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        if (!isLoading) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = retryTitle ?: stringResource(R.string.bioscan_paywall_retry),
                color = colors.accent,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
fun PaywallHero(
    copy: PaywallCopy,
    modifier: Modifier = Modifier,
    /** 覆盖第三行强调色；为 null 时用主题 success 色。 */
    footnoteColor: Color? = null,
) {
    val colors = BioScanDesign.colors
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.cardBackground, RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = copy.heroTitle,
            color = colors.primaryText,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = copy.heroSubtitle,
            color = colors.accent,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = copy.heroFootnote,
            color = footnoteColor ?: colors.success,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun PaywallProductOptionCard(
    title: String,
    subtitle: String,
    formattedPrice: String,
    priceCaption: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = null,
    originalPriceText: String? = null,
    /** 终身卡：深色渐变 + 白字（iOS lifetime 卡片）。 */
    highlighted: Boolean = false,
    /** 单价，例如 “US$0.20/scan”；为 null 时回退到 [priceCaption]。 */
    unitPriceText: String? = null,
    /** 价格下方的补充文案，例如 “一次性购买”。 */
    footerText: String? = null,
    onSelect: () -> Unit,
) {
    val colors = BioScanDesign.colors
    val isLifetime = highlighted
    val shape = RoundedCornerShape(20.dp)

    val background = when {
        isLifetime -> Brush.linearGradient(
            listOf(
                lerp(colors.accent, Color.Black, 0.45f),
                lerp(colors.accent, Color.Black, 0.58f),
            )
        )
        selected -> Brush.linearGradient(
            listOf(
                lerp(colors.cardBackground, colors.accent, 0.16f),
                colors.cardBackground,
            )
        )
        else -> Brush.linearGradient(listOf(colors.cardBackground, colors.cardBackground))
    }

    val titleColor = if (isLifetime) Color.White else colors.primaryText
    val subtitleColor = if (isLifetime) Color.White.copy(alpha = 0.78f) else colors.secondaryText
    val tertiaryColor = if (isLifetime) Color.White.copy(alpha = 0.88f) else colors.accent
    val highlight = lerp(colors.accent, Color.White, 0.45f)
    val ringColor = when {
        isLifetime && selected -> highlight
        isLifetime -> Color.White.copy(alpha = 0.45f)
        selected -> colors.accent
        else -> colors.border
    }
    val borderColor = when {
        isLifetime && selected -> highlight
        isLifetime -> Color.White.copy(alpha = 0.16f)
        selected -> colors.accent
        else -> colors.border
    }
    val caption = unitPriceText ?: priceCaption.takeIf { !isLifetime && it.isNotBlank() }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background, shape)
                .border(if (selected) 2.dp else 1.dp, borderColor, shape)
                .clickable(enabled = enabled, onClick = onSelect)
                .padding(
                    horizontal = 16.dp,
                    vertical = if (isLifetime) 17.dp else 15.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .border(2.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(Modifier.size(10.dp).background(ringColor, CircleShape))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = if (isLifetime) 20.sp else 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = if (isLifetime) 14.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(
                    top = if (isLifetime) {
                        if (badge != null) 26.dp else 12.dp
                    } else {
                        0.dp
                    },
                ),
            ) {
                originalPriceText?.let {
                    Text(
                        text = it,
                        color = subtitleColor,
                        fontSize = if (isLifetime) 14.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
                Text(
                    text = formattedPrice,
                    color = titleColor,
                    fontSize = if (isLifetime) 26.sp else 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        color = tertiaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                footerText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = tertiaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        badge?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.uppercase(),
                color = if (isLifetime) Color.White else colors.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp)
                    .background(
                        if (isLifetime) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            colors.accent.copy(alpha = 0.14f)
                        },
                        CircleShape,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun PaywallPrimaryButton(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BioScanDesign.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                if (enabled) {
                    Brush.linearGradient(
                        listOf(colors.accent, lerp(colors.accent, Color.White, 0.22f))
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            colors.secondaryText.copy(alpha = 0.35f),
                            colors.secondaryText.copy(alpha = 0.35f),
                        )
                    )
                },
                RoundedCornerShape(18.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
        )
    }
}
