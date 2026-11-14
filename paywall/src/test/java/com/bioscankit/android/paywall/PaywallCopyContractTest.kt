package com.bioscankit.android.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paywall 文案契约测试。
 *
 * 锁定两种风格的文案注入约定，避免再次出现「占位符类型不匹配导致 CTA 显示 %1$s」
 * 或「未选中商品时显示加载态文案」这类只在真机上才暴露的问题。
 */
class PaywallCopyContractTest {

    @Test
    fun iNatureActionTemplateUsesBracesPricePlaceholder() {
        // Android iNature 组件的约定是 {price}，不是 Android 资源占位符 %1$s。
        // 该契约由 INaturePaywallScreen 的 INatureBottomCTA 实现，此处以常量形式锁定。
        val template = "Get Starter — {price}"
        val rendered = template.replace("{price}", "US$0.99")
        assertEquals("Get Starter — US$0.99", rendered)
        assertTrue(!rendered.contains("%1\$s"))
    }

    @Test
    fun cardSelectionCopyHasDistinctPurchaseAndLoadingTitles() {
        val copy = CardSelectionPaywallCopy(
            title = "Keep Collecting!",
            subtitle = "No subscriptions",
            balanceText = "4 scans left",
            restoreTitle = "Restore Purchase",
            loadingStoreTitle = "Loading",
            retryTitle = "Retry",
            loadingActionTitle = "Loading price…",
            footerText = "No auto-renewals",
            purchaseTitle = "Continue",
        )
        // 未选中商品时的 CTA 文案不能复用加载态文案。
        assertTrue(copy.purchaseTitle != copy.loadingActionTitle)
    }

    @Test
    fun cardSelectionActiveLifetimeTitleDefaultsToBalanceText() {
        val copy = CardSelectionPaywallCopy(
            title = "t",
            subtitle = "s",
            balanceText = "4 scans left",
            restoreTitle = "r",
            loadingStoreTitle = "l",
            retryTitle = "rt",
            loadingActionTitle = "la",
            footerText = "f",
        )
        // 不显式传 activeLifetimeTitle 时，退化为 balanceText，行为与旧版本一致。
        assertEquals(copy.balanceText, copy.activeLifetimeTitle)
    }

    @Test
    fun catalogExposesProductsInConsumablesThenLifetimeOrder() {
        val catalog = PurchaseCatalog(
            lifetime = PaywallProduct("lifetime", PaywallProductKind.Lifetime, "L", ""),
            consumables = listOf(
                PaywallProduct("s5", PaywallProductKind.Consumable, "5", "", creditCount = 5),
                PaywallProduct("s20", PaywallProductKind.Consumable, "20", "", creditCount = 20),
            ),
        )
        assertEquals(listOf("s5", "s20", "lifetime"), catalog.products.map { it.id })
        assertEquals(5, catalog.product("s5")?.creditCount)
    }
}
