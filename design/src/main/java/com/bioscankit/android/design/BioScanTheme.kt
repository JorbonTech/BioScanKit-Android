package com.bioscankit.android.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AdaptiveColor(
    val light: Color,
    val dark: Color,
) {
    fun resolve(isDark: Boolean): Color = if (isDark) dark else light
}

@Immutable
data class BioScanTheme(
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val pageBackground: AdaptiveColor,
    val cardBackground: AdaptiveColor,
    val elevatedCardBackground: AdaptiveColor,
    val primaryText: AdaptiveColor,
    val secondaryText: AdaptiveColor,
    val border: AdaptiveColor,
    /**
     * 系统语义底色，对应 iOS 的 `Color(uiColor: .systemBackground)`。
     *
     * 设置页、语言页等「系统风格」页面在 iOS 上不使用品牌色，只使用系统中性色；
     * 这里默认取 iOS 亮/暗两套系统底色，宿主可覆盖。
     */
    val systemBackground: AdaptiveColor = AdaptiveColor(
        light = Color(0xFFFFFFFF),
        dark = Color(0xFF000000),
    ),
    /**
     * 系统语义次级底色，对应 iOS 的 `Color(uiColor: .secondarySystemBackground)`。
     *
     * 设置页卡片、会员卡、弹窗容器都用它，默认值取 iOS 实测色值。
     */
    val secondarySystemBackground: AdaptiveColor = AdaptiveColor(
        light = Color(0xFFF2F2F7),
        dark = Color(0xFF1C1C1E),
    ),
    val cardCornerRadius: Dp = 18.dp,
    val buttonCornerRadius: Dp = 14.dp,
    val horizontalPadding: Dp = 18.dp,
    val sectionSpacing: Dp = 20.dp,
) {
    companion object {
        /**
         * 中性兜底主题，**不绑定任何宿主品牌色**。
         *
         * 用途：当宿主忘记调用 `BioScanDesignSystem(theme = ...)` 时，
         * 组件会落到这里，呈现无品牌的中性灰蓝，从而让「漏传主题」
         * 变成肉眼可见的问题，而不是悄悄套用别的 App 的品牌色。
         *
         * 历史背景：默认值曾是 [INature]，导致 iNature 长期依赖默认值、
         * 色值漂移也无法察觉。改为中性后，漏传即暴露。
         */
        val Neutral = BioScanTheme(
            accent = Color(0xFF5A6B7A),
            success = Color(0xFF4C8C5A),
            warning = Color(0xFFB8863B),
            danger = Color(0xFFB4534F),
            pageBackground = AdaptiveColor(
                light = Color(0xFFF6F7F8),
                dark = Color(0xFF14171A),
            ),
            cardBackground = AdaptiveColor(
                light = Color.White,
                dark = Color(0xFF1F2429),
            ),
            elevatedCardBackground = AdaptiveColor(
                light = Color(0xFFEFF1F3),
                dark = Color(0xFF272D33),
            ),
            primaryText = AdaptiveColor(
                light = Color(0xFF1C2226),
                dark = Color(0xFFEDF1F4),
            ),
            secondaryText = AdaptiveColor(
                light = Color(0xFF6B767F),
                dark = Color(0xFF9AA6B0),
            ),
            border = AdaptiveColor(
                light = Color(0xFFDCE1E6),
                dark = Color.White.copy(alpha = 0.12f),
            ),
        )

        /**
         * iNature 的品牌主题。
         *
         * 注意：**不再是默认值**。宿主必须显式传入，
         * 例如 `BioScanDesignSystem(theme = BioScanTheme.INature)`。
         */
        val INature = BioScanTheme(
            accent = Color(0xFF248533),
            success = Color(0xFF299E54),
            warning = Color(0xFFED941F),
            danger = Color(0xFFD74747),
            pageBackground = AdaptiveColor(
                light = Color(0xFFF2F5F2),
                dark = Color(0xFF14191F),
            ),
            cardBackground = AdaptiveColor(
                light = Color.White,
                dark = Color(0xFF21262E),
            ),
            elevatedCardBackground = AdaptiveColor(
                light = Color(0xFFE3EDE3),
                dark = Color(0xFF263129),
            ),
            primaryText = AdaptiveColor(
                light = Color(0xFF141F33),
                dark = Color(0xFFEDF2FA),
            ),
            secondaryText = AdaptiveColor(
                light = Color(0xFF8594A8),
                dark = Color(0xFF9EADC2),
            ),
            border = AdaptiveColor(
                light = Color(0xFFD6E0E8),
                dark = Color.White.copy(alpha = 0.14f),
            ),
        )
    }
}

@Immutable
data class ResolvedBioScanColors(
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val pageBackground: Color,
    val cardBackground: Color,
    val elevatedCardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val border: Color,
    val systemBackground: Color,
    val secondarySystemBackground: Color,
)

private val LocalBioScanTheme = staticCompositionLocalOf { BioScanTheme.Neutral }
private val LocalBioScanIsDark = staticCompositionLocalOf<Boolean?> { null }

object BioScanDesign {
    val theme: BioScanTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalBioScanTheme.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalBioScanIsDark.current ?: isSystemInDarkTheme()

    val colors: ResolvedBioScanColors
        @Composable
        @ReadOnlyComposable
        get() = theme.resolve(isDark)
}

fun BioScanTheme.resolve(isDark: Boolean): ResolvedBioScanColors = ResolvedBioScanColors(
    accent = accent,
    success = success,
    warning = warning,
    danger = danger,
    pageBackground = pageBackground.resolve(isDark),
    cardBackground = cardBackground.resolve(isDark),
    elevatedCardBackground = elevatedCardBackground.resolve(isDark),
    primaryText = primaryText.resolve(isDark),
    secondaryText = secondaryText.resolve(isDark),
    border = border.resolve(isDark),
    systemBackground = systemBackground.resolve(isDark),
    secondarySystemBackground = secondarySystemBackground.resolve(isDark),
)

@Composable
fun BioScanDesignSystem(
    theme: BioScanTheme = BioScanTheme.Neutral,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBioScanTheme provides theme,
        LocalBioScanIsDark provides darkTheme,
    ) {
        MaterialTheme(content = content)
    }
}
