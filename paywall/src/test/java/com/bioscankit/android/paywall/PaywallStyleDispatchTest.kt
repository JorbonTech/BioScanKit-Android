package com.bioscankit.android.paywall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paywall 风格分发测试。
 *
 * 对齐 iOS `PaywallScreen` 的 `switch configuration.style`：
 * `.iNature` 渲染 iNature 页面，`.cardSelection` 渲染卡片选择页面。
 * 两种风格的参数与视觉完全不同，之前 Android 没有分发层，导致
 * `PaywallStyle` 只用于埋点而无法影响渲染。
 */
class PaywallStyleDispatchTest {

    @Test
    fun iNatureStyleResolvesToINatureLayout() {
        assertTrue(PaywallStyle.INature.resolvesToINatureLayout())
    }

    @Test
    fun cardSelectionStyleResolvesToCardLayout() {
        assertFalse(PaywallStyle.CardSelection.resolvesToINatureLayout())
    }

    @Test
    fun mrRockUsesCardSelectionLikeIOS() {
        // iOS MrRock 的 PaywallConfiguration 使用 .cardSelection
        val configuration = PaywallConfiguration(
            style = PaywallStyle.CardSelection,
            catalog = PurchaseCatalog(
                lifetime = PaywallProduct(
                    id = "lifetime",
                    kind = PaywallProductKind.Lifetime,
                    title = "Lifetime",
                    subtitle = "",
                ),
            ),
            copy = PaywallCopy(heroTitle = "", heroSubtitle = "", heroFootnote = ""),
        )
        assertFalse(configuration.style.resolvesToINatureLayout())
    }

    @Test
    fun iNatureStyleConfigurationUsesINatureLayout() {
        val configuration = PaywallConfiguration(
            style = PaywallStyle.INature,
            catalog = PurchaseCatalog(
                lifetime = PaywallProduct(
                    id = "lifetime",
                    kind = PaywallProductKind.Lifetime,
                    title = "Lifetime Access",
                    subtitle = "",
                ),
            ),
            copy = PaywallCopy(heroTitle = "", heroSubtitle = "", heroFootnote = ""),
        )
        assertTrue(configuration.style.resolvesToINatureLayout())
    }
}
