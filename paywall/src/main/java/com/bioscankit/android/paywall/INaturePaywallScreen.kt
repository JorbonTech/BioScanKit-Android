package com.bioscankit.android.paywall

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bioscankit.android.design.BioScanDialog
import com.bioscankit.android.design.BioScanDesign

/** Store-owned price data consumed by the shared iNature-style paywall. */
@Immutable
data class INaturePaywallProduct(
    val id: String,
    val priceText: String,
    val referencePriceText: String = "",
    val perUnitText: String = "",
    val available: Boolean = true,
)

/** All app-specific language for the shared iNature-style paywall. */
@Immutable
data class INaturePaywallCopy(
    val hero: PaywallCopy,
    val lifetimeTitle: String,
    val lifetimeSubtitle: String,
    val lifetimeBenefits: List<String>,
    val lifetimeBadge: String,
    val lifetimeCostCaption: String,
    val lifetimeValueCaption: String,
    val creditSectionTitle: String,
    val starterTitle: String,
    val starterUnits: String,
    val popularTitle: String,
    val popularUnits: String,
    val popularBadge: String,
    val lifetimeActionTitle: String,
    val lifetimeUnavailableTitle: String,
    val starterActionTemplate: String,
    val starterUnavailableTitle: String,
    val popularActionTemplate: String,
    val popularUnavailableTitle: String,
    val lifetimeActivatedTitle: String,
    val productsUnavailableTitle: String,
    /** 以下文案传 null 时使用组件内置的多语言资源。 */
    val restoreTitle: String? = null,
    val connectingMessage: String? = null,
    val retryTitle: String? = null,
    val dialogConfirmTitle: String? = null,
    /**
     * 额度区小标题。iOS 默认值为 "PAY ONLY FOR WHAT YOU USE"，但会走
     * `BioScanPaywallL10n` 本地化；这里同样允许宿主注入译文。
     */
    val creditSectionEyebrow: String = creditSectionTitle,
    /** 热门额度包角标（iOS "SAVE 25%"）。 */
    val popularSavingsBadge: String = popularBadge,
    /** 额度卡档位标题（iOS "POPULAR" / "STARTER"）。 */
    val popularTierTitle: String = popularTitle,
    val starterTierTitle: String = starterTitle,
)

/**
 * Complete iNature paywall presentation shared by Android apps.
 *
 * Apps own billing and localized copy. This component owns selection, responsive layout,
 * product cards, status presentation, the fixed CTA, and all visual styling.
 */
@Composable
fun INaturePaywallScreen(
    lifetimeProduct: INaturePaywallProduct?,
    starterProduct: INaturePaywallProduct?,
    popularProduct: INaturePaywallProduct?,
    defaultProductId: String,
    copy: INaturePaywallCopy,
    isLifetimeMember: Boolean,
    isLoading: Boolean,
    isPurchasing: Boolean,
    storeMessage: String?,
    messageTitle: String?,
    messageBody: String?,
    onDismiss: () -> Unit,
    onDismissMessage: () -> Unit,
    onRestore: () -> Unit,
    onReloadProducts: () -> Unit,
    onProductSelected: (String) -> Unit = {},
    onPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 埋点回调，对齐 iOS `PaywallActions.track`；不传则不上报。 */
    track: (PaywallEvent) -> Unit = {},
    /**
     * 顶部品牌插画插槽。
     *
     * 注意：iOS 的 legacy iNature 布局（iNature / NatureEar 实际使用）**不渲染 hero**，
     * 只显示文字卡；该参数预留给 standard 布局，当前实现忽略它。
     */
    hero: (@Composable () -> Unit)? = null,
    /**
     * 宿主自定义整套配色；为 null 时用 iNature 固定绿色配色（历史行为）。
     * NatureEar 用它对齐 iOS 的「奶油底 + 锈红」付费页。
     */
    palette: INaturePaywallColors? = null,
) {
    val products = listOfNotNull(lifetimeProduct, starterProduct, popularProduct)
    var selectedProductId by rememberSaveable { mutableStateOf(defaultProductId) }
    LaunchedEffect(products, defaultProductId) {
        if (products.none { it.id == selectedProductId }) {
            selectedProductId = products.firstOrNull { it.id == defaultProductId }?.id
                ?: products.firstOrNull()?.id
                ?: defaultProductId
        }
        onProductSelected(selectedProductId)
    }

    val colors = palette ?: rememberINaturePaywallColors()
    val configuration = LocalConfiguration.current
    val horizontalPadding = if (configuration.screenWidthDp >= 900) 24.dp else 16.dp
    val contentModifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val busy = isLoading || isPurchasing
    LaunchedEffect(Unit) { track(PaywallEvent.Shown(PaywallStyle.INature)) }
    // 底部 CTA 固定悬浮，滚动内容需要按其实测高度留白，否则最后两张额度卡会被遮住。
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // 真机回归用：setprop debug.inature.scroll end 自动滚到底部，检查末尾内容是否被 CTA 遮挡。
    val debugScrollToEnd = remember {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, "debug.inature.scroll") as? String
        }.getOrNull() == "end"
    }
    LaunchedEffect(debugScrollToEnd) {
        if (debugScrollToEnd) {
            kotlinx.coroutines.delay(500)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(modifier.fillMaxSize().background(colors.pageBackground)) {
        if (!messageBody.isNullOrBlank()) {
            BioScanDialog(
                onDismissRequest = onDismissMessage,
                title = messageTitle ?: stringResource(R.string.bioscan_paywall_notice),
                message = messageBody,
                confirmLabel = copy.dialogConfirmTitle ?: stringResource(R.string.bioscan_paywall_ok),
                onConfirm = onDismissMessage,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding)
                .padding(top = 18.dp, bottom = maxOf(bottomBarHeight, INatureBottomBarClearance) + bottomInset + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(contentModifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                PaywallTopBar(
                    restoreTitle = copy.restoreTitle ?: stringResource(R.string.bioscan_paywall_restore),
                    isBusy = busy,
                    onDismiss = onDismiss,
                    onRestore = {
                        track(PaywallEvent.RestoreStarted)
                        onRestore()
                    },
                    closeIcon = {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(R.string.bioscan_paywall_close),
                            tint = colors.secondaryText,
                        )
                    },
                    // iOS legacy 使用 legacyAccent（深绿），不用主题强调色。
                    restoreTint = colors.greenDeep,
                )

                if (isLoading || !storeMessage.isNullOrBlank()) {
                    PaywallStoreStatusBanner(
                        isLoading = isLoading,
                        message = storeMessage,
                        onRetry = onReloadProducts,
                        connectingMessage = copy.connectingMessage,
                        retryTitle = copy.retryTitle,
                        statusIcon = {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.warning,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                PaywallHero(copy = copy.hero, footnoteColor = colors.heroFootnote)

                INatureLifetimeCard(
                    product = lifetimeProduct,
                    copy = copy,
                    colors = colors,
                    isSelected = selectedProductId == lifetimeProduct?.id,
                    enabled = !isLifetimeMember && !busy && lifetimeProduct.isPurchasable(),
                    onSelect = {
                        lifetimeProduct?.let {
                            selectedProductId = it.id
                            track(PaywallEvent.ProductSelected(it.id))
                            onProductSelected(it.id)
                        }
                    },
                )

                INatureCreditSection(
                    starterProduct = starterProduct,
                    popularProduct = popularProduct,
                    copy = copy,
                    colors = colors,
                    selectedProductId = selectedProductId,
                    enabled = !isLifetimeMember && !busy,
                    onSelect = {
                        selectedProductId = it
                        track(PaywallEvent.ProductSelected(it))
                        onProductSelected(it)
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.pageBackground)
                .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
                .padding(horizontal = horizontalPadding, vertical = 8.dp)
                .padding(bottom = bottomInset),
            contentAlignment = Alignment.Center,
        ) {
            INatureBottomCTA(
                modifier = contentModifier,
                product = selectedProduct,
                lifetimeProductId = lifetimeProduct?.id,
                starterProductId = starterProduct?.id,
                popularProductId = popularProduct?.id,
                copy = copy,
                isLifetimeMember = isLifetimeMember,
                isBusy = busy,
                colors = colors,
                onClick = {
                    selectedProduct?.let {
                        track(PaywallEvent.PurchaseStarted(it.id))
                        onPurchase(it.id)
                    }
                },
            )
        }

        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topInset)
                .background(colors.pageBackground),
        )
    }
}

/** mascot 卡片底色，对齐 iOS `Color.paywallMascotCard`。 */
/** 底部固定 CTA 区域高度，滚动内容必须留出这段空间。 */
private val INatureBottomBarClearance = 200.dp

@Composable
private fun INatureLifetimeCard(
    product: INaturePaywallProduct?,
    copy: INaturePaywallCopy,
    colors: INaturePaywallColors,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).graphicsLayer { alpha = if (enabled) 1f else 0.65f }) {
        Box(
            Modifier.fillMaxWidth().shadow(5.dp, shape, clip = false).clip(shape)
                .background(colors.cardBackground)
                .border(if (isSelected) 2.4.dp else 1.6.dp, if (isSelected) colors.green else colors.softGreenBorder, shape)
                .clickable(enabled = enabled, onClick = onSelect).padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(copy.lifetimeTitle, color = colors.primaryText, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                        Text(copy.lifetimeSubtitle, color = colors.green, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    // 与 iOS 一致使用 164dp；价格字号随容器自动缩小，避免长价格被裁掉。
                    Column(
                        Modifier.padding(top = 16.dp).widthIn(min = 148.dp, max = 164.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.softGreenBackground)
                            .border(1.dp, colors.green.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            product?.referencePriceText.orEmpty(),
                            color = colors.secondaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 1,
                        )
                        Text(
                            product?.priceText ?: stringResource(R.string.bioscan_paywall_unavailable),
                            color = colors.green,
                            fontSize = 26.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }

                val benefitIcons = if (copy.lifetimeBenefits.size == 4) {
                    NatureEarBenefitIcons
                } else {
                    INatureBenefitIcons
                }
                copy.lifetimeBenefits.forEachIndexed { index, benefit ->
                    INatureBenefitLine(benefitIcons[index % benefitIcons.size], benefit, colors)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                        drawLine(colors.green, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), size.height, StrokeCap.Round)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(copy.lifetimeCostCaption, color = colors.tertiaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(copy.lifetimeValueCaption, color = colors.green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            copy.lifetimeBadge,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-18).dp, y = (-2).dp)
                .clip(CircleShape).background(colors.green).padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}

private val INatureBenefitIcons: List<ImageVector> = listOf(
    Icons.Rounded.AllInclusive,
    Icons.Rounded.Bolt,
    Icons.Rounded.WifiOff,
    Icons.Rounded.Verified,
    Icons.Rounded.Sync,
)

private val NatureEarBenefitIcons: List<ImageVector> = listOf(
    Icons.Rounded.AllInclusive,
    Icons.Rounded.WifiOff,
            Icons.AutoMirrored.Rounded.MenuBook,
    Icons.Rounded.Sync,
)

@Composable
private fun INatureBenefitLine(icon: ImageVector, text: String, colors: INaturePaywallColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // 对齐 iOS：图标圈用专属浅绿底 + 深色图标，与卡片背景区分更明显。
        Box(Modifier.size(34.dp).clip(CircleShape).background(colors.benefitIconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.benefitIconTint, modifier = Modifier.size(14.dp))
        }
        Text(text, color = colors.primaryText, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun INatureCreditSection(
    starterProduct: INaturePaywallProduct?,
    popularProduct: INaturePaywallProduct?,
    copy: INaturePaywallCopy,
    colors: INaturePaywallColors,
    selectedProductId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // iOS legacy 使用固定文案，不取自 App 文案。
        Text(
            copy.creditSectionEyebrow,
            color = colors.secondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 对齐 iOS：档位标题固定为 STARTER / POPULAR，只有热门档带 SAVE 25% 角标。
            INatureCreditCard(
                Modifier.weight(1f).fillMaxHeight(), starterProduct, copy.starterTierTitle,
                copy.starterUnits, null, selectedProductId == starterProduct?.id,
                enabled && starterProduct.isPurchasable(), colors,
                onClick = { starterProduct?.let { onSelect(it.id) } },
            )
            INatureCreditCard(
                Modifier.weight(1f).fillMaxHeight(), popularProduct, copy.popularTierTitle,
                copy.popularUnits, copy.popularSavingsBadge, selectedProductId == popularProduct?.id,
                enabled && popularProduct.isPurchasable(), colors,
                onClick = { popularProduct?.let { onSelect(it.id) } },
            )
        }
    }
}

@Composable
private fun INatureCreditCard(
    modifier: Modifier,
    product: INaturePaywallProduct?,
    title: String,
    units: String,
    badge: String?,
    selected: Boolean,
    enabled: Boolean,
    colors: INaturePaywallColors,
    onClick: () -> Unit,
) {
    Column(
        modifier.shadow(3.dp, RoundedCornerShape(16.dp), clip = false).clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .border(if (selected) 2.2.dp else 1.2.dp, if (selected) colors.green else colors.cardBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .fillMaxHeight().heightIn(min = 176.dp).padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        // iOS：只有热门的 20 次包显示 SAVE 25% 胶囊，其余不显示角标。
        badge?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                // 负 padding 在 Compose 里非法，改用 offset 实现角标上浮重叠。
                modifier = Modifier
                    .offset(y = (-10).dp)
                    .clip(CircleShape)
                    .background(colors.badgeBlue)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(title, color = colors.secondaryText, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(units, color = colors.primaryText, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(product?.priceText ?: stringResource(R.string.bioscan_paywall_unavailable), color = colors.green, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
        Text(product?.perUnitText.orEmpty(), color = colors.secondaryText, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun INatureBottomCTA(
    modifier: Modifier,
    product: INaturePaywallProduct?,
    lifetimeProductId: String?,
    starterProductId: String?,
    popularProductId: String?,
    copy: INaturePaywallCopy,
    isLifetimeMember: Boolean,
    isBusy: Boolean,
    colors: INaturePaywallColors,
    onClick: () -> Unit,
) {
    val enabled = !isLifetimeMember && !isBusy && product.isPurchasable()
    val title = when {
        isLifetimeMember -> copy.lifetimeActivatedTitle
        product?.id == lifetimeProductId -> if (product.isPurchasable()) copy.lifetimeActionTitle else copy.lifetimeUnavailableTitle
        product?.id == starterProductId -> if (product.isPurchasable()) copy.starterActionTemplate.replace("{price}", product?.priceText.orEmpty()) else copy.starterUnavailableTitle
        product?.id == popularProductId -> if (product.isPurchasable()) copy.popularActionTemplate.replace("{price}", product?.priceText.orEmpty()) else copy.popularUnavailableTitle
        else -> copy.productsUnavailableTitle
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.fillMaxWidth().shadow(10.dp, shape, clip = false).clip(shape)
            .background(if (isLifetimeMember) colors.disabledGreen else colors.green)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (!isLifetimeMember) Modifier.inatureShimmer(shape) else Modifier)
            .border(1.dp, if (isLifetimeMember) colors.disabledGreen else colors.greenDeep.copy(alpha = 0.28f), shape)
            .padding(vertical = 18.dp, horizontal = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
            if (!isLifetimeMember) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun INaturePaywallProduct?.isPurchasable(): Boolean =
    this != null && available && priceText.isNotBlank()

@Composable
private fun rememberINaturePaywallColors(): INaturePaywallColors {
    val shared = BioScanDesign.colors
    val dark = BioScanDesign.isDark
    return remember(shared, dark) {
        // 对齐 iOS `LegacyINaturePaywallView`：该风格使用一套固定配色，
        // 不继承 App 主题（否则 MrRock 的 teal、NatureEar 的品牌色都会渗进来）。
        INaturePaywallColors(
            pageBackground = if (dark) Color(0xFF14191F) else Color(0xFFF2F5F2),
            primaryText = if (dark) Color(0xFFEDF2FA) else Color(0xFF141F33),
            secondaryText = if (dark) Color(0xFF9EAEBF) else Color(0xFF949FBA),
            tertiaryText = if (dark) Color(0xFF9EAEBF).copy(alpha = 0.86f) else Color(0xFF858FA8),
            cardBackground = if (dark) Color(0xFF21262E) else Color.White,
            cardBorder = if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFFD6E0E8),
            softGreenBackground = if (dark) Color(0xFF1E2A20) else Color(0xFFEAF2EA),
            softGreenBorder = if (dark) Color(0xFF4F7A54) else Color(0xFFA9D1A9),
            green = if (dark) Color(0xFF3FAE5A) else Color(0xFF2F8A47),
            greenDeep = if (dark) Color(0xFF1A6B33) else Color(0xFF1A6629),
            disabledGreen = if (dark) Color(0xFF3FAE5A).copy(alpha = 0.72f) else Color(0xFF2F8A47).copy(alpha = 0.72f),
            badgeBlue = if (dark) Color(0xFF3FAE5A) else Color(0xFF2F8A47),
            warning = if (dark) Color(0xFFE6693A) else Color(0xFFE6792A),
            // iOS legacy：图标圈 0.21/0.29/0.22（暗）· 0.86/0.92/0.86（亮）
            benefitIconBackground = if (dark) Color(0xFF364A38) else Color(0xFFDBEBDB),
            // iOS legacy：图标 0.08/0.14/0.26（暗）· 同上深绿（亮）
            benefitIconTint = if (dark) Color(0xFF141F33) else Color(0xFF1A6629),
        )
    }
}

@Immutable
data class INaturePaywallColors(
    val pageBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val softGreenBackground: Color,
    val softGreenBorder: Color,
    val green: Color,
    val greenDeep: Color,
    val disabledGreen: Color,
    val badgeBlue: Color,
    val warning: Color,
    val benefitIconBackground: Color,
    val benefitIconTint: Color,
    /** 顶部卡片第三行文案颜色；为 null 时用主题 success 色。 */
    val heroFootnote: Color? = null,
)

private fun Modifier.inatureShimmer(shape: RoundedCornerShape): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "inature_paywall_cta_shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_350, easing = LinearEasing), RepeatMode.Restart),
        label = "inature_paywall_cta_shimmer_progress",
    )
    val width = with(LocalDensity.current) { 260.dp.toPx() }
    background(
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0f),
            ),
            start = Offset(progress * width - width, 0f),
            end = Offset(progress * width, width / 2f),
        ),
        shape,
    )
}
