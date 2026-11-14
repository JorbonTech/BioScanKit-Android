package com.bioscankit.android.paywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Paywall 页面入口，对齐 iOS `PaywallScreen`。
 *
 * iOS 的 `PaywallScreen` 按 `configuration.style` 分发到两种完全不同的页面：
 * - `.iNature`      → `INaturePaywallView`（终身卡 + 两侧额度卡，iNature 视觉）
 * - `.cardSelection` → `CardSelectionPaywallView`（可选中卡片列表，MrRock 视觉）
 *
 * Android 之前没有这层分发：两个 Screen 各自被 App 直接调用，`PaywallStyle`
 * 只用于埋点。这里补上统一入口，让「风格」真正决定渲染哪个页面。
 *
 * 两种风格的参数形态差异很大（iNature 需要 benefits/badge/cost captions，
 * cardSelection 需要 actionTitle/highlight），因此通过 [PaywallScreenContent]
 * 让宿主按风格提供内容，组件只负责分发与共享骨架。
 */
@Composable
fun PaywallScreen(
    style: PaywallStyle,
    content: PaywallScreenContent,
    modifier: Modifier = Modifier,
) {
    when (style) {
        PaywallStyle.INature -> content.INature(modifier)
        PaywallStyle.CardSelection -> content.CardSelection(modifier)
    }
}

/**
 * 两种 Paywall 风格的渲染内容。
 *
 * 宿主只需实现自己使用的那个分支；未使用的一侧提供空实现即可
 * （例如 MrRock 只实现 [CardSelection]，iNature 只实现 [INature]）。
 */
data class PaywallScreenContent(
    val INature: @Composable (Modifier) -> Unit = {},
    val CardSelection: @Composable (Modifier) -> Unit = {},
)

/**
 * 纯函数式分发，便于在无 Compose 环境下测试「风格 → 页面」的映射。
 */
internal fun PaywallStyle.resolvesToINatureLayout(): Boolean = when (this) {
    PaywallStyle.INature -> true
    PaywallStyle.CardSelection -> false
}
