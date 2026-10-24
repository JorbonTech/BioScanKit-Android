package com.bioscankit.android.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val highlighted: Boolean = false,
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
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    hero: @Composable () -> Unit,
    closeIcon: @Composable () -> Unit,
    statusIcon: @Composable () -> Unit,
) {
    var selectedProductId by rememberSaveable { mutableStateOf(defaultProductId) }

    LaunchedEffect(products, defaultProductId) {
        if (products.none { it.id == selectedProductId }) {
            selectedProductId = products.firstOrNull { it.id == defaultProductId }?.id
                ?: products.firstOrNull()?.id
                ?: defaultProductId
        }
    }

    val colors = BioScanDesign.colors
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val busy = isLoading || isPurchasing
    val background = Brush.verticalGradient(
        listOf(
            colors.accent,
            colors.accent.copy(alpha = if (BioScanDesign.isDark) 0.32f else 0.22f),
            colors.pageBackground,
            colors.pageBackground,
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaywallTopBar(
                    restoreTitle = copy.restoreTitle,
                    isBusy = busy,
                    onDismiss = onDismiss,
                    onRestore = onRestore,
                    modifier = Modifier.padding(top = 14.dp),
                    closeIcon = closeIcon,
                )

                Box(
                    modifier = Modifier
                        .width(168.dp)
                        .height(95.dp)
                        .align(Alignment.CenterHorizontally)
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

                Text(
                    text = copy.balanceText,
                    color = colors.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(colors.cardBackground.copy(alpha = 0.82f), CircleShape)
                        .border(1.dp, colors.border, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )

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
                        selected = product.id == selectedProductId,
                        enabled = !busy,
                        onSelect = { selectedProductId = product.id },
                    )
                }

                PaywallPrimaryButton(
                    title = if (isPurchasing) copy.loadingActionTitle
                    else selectedProduct?.actionTitle ?: copy.loadingActionTitle,
                    enabled = selectedProduct != null && !busy,
                    onClick = { selectedProduct?.let { onPurchase(it.id) } },
                )

                Text(
                    text = copy.footerText,
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
