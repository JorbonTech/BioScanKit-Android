package com.bioscankit.android.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioscankit.android.design.BioScanDesign

/** Product presentation used by the shared selectable-card paywall. */
@Immutable
data class CardSelectionPaywallProduct(
    val id: String,
    val title: String,
    val subtitle: String,
    val formattedPrice: String,
    val priceCaption: String,
    val actionTitle: String,
    val badge: String? = null,
    val originalPriceText: String? = null,
    /** 终身卡：深色填充 + 白字（iOS 的 lifetime 卡片）。 */
    val highlighted: Boolean = false,
    /** 单价，例如 “US$0.20/scan”；扫描包才显示。 */
    val unitPriceText: String? = null,
    /** 价格下方的补充文案，例如 “一次性购买”。 */
    val footerText: String? = null,
)

/** App-owned copy for the shared selectable-card paywall. */
@Immutable
data class CardSelectionPaywallCopy(
    val title: String,
    val subtitle: String,
    val balanceText: String,
    val restoreTitle: String,
    val loadingStoreTitle: String,
    val retryTitle: String,
    val loadingActionTitle: String,
    val footerText: String,
    /** CTA 兜底文案，未选中商品时显示（对齐 iOS `copy.purchaseTitle`）。 */
    val purchaseTitle: String = "Continue",
    /** 已拥有终身会员时的余额区文案（对齐 iOS `copy.activeLifetimeTitle`）。 */
    val activeLifetimeTitle: String = balanceText,
)

/**
 * Complete selectable-card paywall shared across BioScanKit Android apps.
 *
 * The host owns billing and localized copy while this component owns selection,
 * information hierarchy, loading/error presentation, and the visual layout.
 */
@Composable
fun CardSelectionPaywallScreen(
    products: List<CardSelectionPaywallProduct>,
    defaultProductId: String,
    copy: CardSelectionPaywallCopy,
    isLoading: Boolean,
    isPurchasing: Boolean,
    storeMessage: String?,
    onDismiss: () -> Unit,
    onReloadProducts: () -> Unit,
    onSelectProduct: ((String) -> Unit)? = null,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
    closeIcon: @Composable () -> Unit,
    statusIcon: @Composable () -> Unit,
) {
    // 选中态优先由外部（PaywallStore）持有；未提供回调时退回内部状态。
    var internalSelection by rememberSaveable { mutableStateOf(defaultProductId) }
    val selectedProductId = onSelectProduct?.let { defaultProductId } ?: internalSelection

    fun selectProduct(id: String) {
        internalSelection = id
        onSelectProduct?.invoke(id)
    }

    LaunchedEffect(products, defaultProductId) {
        if (products.none { it.id == selectedProductId }) {
            selectProduct(
                products.firstOrNull { it.id == defaultProductId }?.id
                    ?: products.firstOrNull()?.id
                    ?: defaultProductId
            )
        }
    }

    val colors = BioScanDesign.colors
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val busy = isLoading || isPurchasing
    // 全屏页面必须完全不透明：中间色用与 pageBackground 的实色混色，
    // 而不是给 accent 加 alpha，否则会透出下层的首页内容。
    val midColor = lerp(
        colors.accent,
        colors.pageBackground,
        if (BioScanDesign.isDark) 0.68f else 0.78f,
    )
    val background = Brush.verticalGradient(
        listOf(
            colors.accent,
            midColor,
            colors.pageBackground,
            colors.pageBackground,
        )
    )

    // 背景必须先铺满整屏（含系统栏区域），再对内容做 inset 处理；
    // 否则状态栏区域会透出下层页面的内容。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // iOS：关闭按钮在右上角，恢复购买是 CTA 下方的文字链接。
                PaywallCloseButton(
                    onDismiss = onDismiss,
                    modifier = Modifier.padding(top = 14.dp),
                    closeIcon = closeIcon,
                )

                // hero 通常是 AndroidView（GIF），AndroidView 不会遵守 Compose 的 clip，
                // 因此用 graphicsLayer 做真正的图层裁剪，避免盖住下方标题。
                Box(
                    modifier = Modifier
                        .width(168.dp)
                        .height(95.dp)
                        .align(Alignment.CenterHorizontally)
                        .graphicsLayer {
                            clip = true
                            shape = RoundedCornerShape(26.dp)
                        }
                        .background(colors.cardBackground, RoundedCornerShape(26.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    hero()
                }

                Text(
                    text = copy.title,
                    color = colors.primaryText,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                // iOS `balanceBadge`：小圆点 + 12sp bold 文案。
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(colors.cardBackground.copy(alpha = 0.82f), CircleShape)
                        .border(1.dp, colors.border, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(colors.accent, CircleShape),
                    )
                    Text(
                        text = copy.balanceText,
                        color = colors.primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = copy.subtitle,
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )

                PaywallStoreStatusBanner(
                    isLoading = isLoading && products.isEmpty(),
                    message = storeMessage,
                    onRetry = onReloadProducts,
                    connectingMessage = copy.loadingStoreTitle,
                    retryTitle = copy.retryTitle,
                    statusIcon = statusIcon,
                )

                products.forEach { product ->
                    PaywallProductOptionCard(
                        title = product.title,
                        subtitle = product.subtitle,
                        formattedPrice = product.formattedPrice,
                        priceCaption = product.priceCaption,
                        badge = product.badge,
                        originalPriceText = product.originalPriceText,
                        highlighted = product.highlighted,
                        unitPriceText = product.unitPriceText,
                        footerText = product.footerText,
                        selected = product.id == selectedProductId,
                        enabled = !busy,
                        onSelect = { selectProduct(product.id) },
                    )
                }

                PaywallPrimaryButton(
                    title = if (isPurchasing) {
                        copy.loadingActionTitle
                    } else {
                        selectedProduct?.actionTitle ?: copy.purchaseTitle
                    },
                    enabled = selectedProduct != null && !busy,
                    onClick = { if (selectedProduct != null) onPurchase() },
                )

                // iOS: `Text(restoreTitle).underline()`，12 semibold。
                Text(
                    text = copy.restoreTitle,
                    color = if (BioScanDesign.isDark) colors.accent else colors.primaryText.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy, onClick = onRestore)
                        .padding(vertical = 8.dp),
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
