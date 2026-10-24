package com.bioscankit.android.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
) {
    val colors = BioScanDesign.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            closeIcon()
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = restoreTitle,
            color = colors.accent,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clickable(enabled = !isBusy, onClick = onRestore)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun PaywallStoreStatusBanner(
    isLoading: Boolean,
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    connectingMessage: String = "Connecting to Google Play...",
    retryTitle: String = "Retry",
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
            text = if (isLoading) connectingMessage else message.orEmpty(),
            color = colors.primaryText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        if (!isLoading) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = retryTitle,
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
            color = colors.success,
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
    highlighted: Boolean = false,
    onSelect: () -> Unit,
) {
    val colors = BioScanDesign.colors
    val shape = RoundedCornerShape(20.dp)
    val background = if (highlighted) colors.accent.copy(alpha = 0.20f) else colors.cardBackground
    val border = if (selected) colors.accent else colors.border
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(22.dp).border(2.dp, colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.primaryText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                )
                badge?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it,
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        modifier = Modifier
                            .background(colors.accent.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                color = colors.secondaryText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            originalPriceText?.let {
                Text(
                    text = it,
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.LineThrough,
                    ),
                )
            }
            Text(
                text = formattedPrice,
                color = colors.primaryText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            )
            Text(
                text = priceCaption,
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                if (enabled) colors.accent else colors.secondaryText.copy(alpha = 0.35f),
                RoundedCornerShape(BioScanDesign.theme.buttonCornerRadius),
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
