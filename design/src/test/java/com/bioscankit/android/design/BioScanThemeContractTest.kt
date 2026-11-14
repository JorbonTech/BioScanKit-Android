package com.bioscankit.android.design

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定「默认主题必须是中性」这一契约。
 *
 * 背景：默认值曾长期是 [BioScanTheme.INature]，导致 iNature 忘记传主题也不会报错，
 * 只是静默套用了别人的品牌色（且色值与 iNature 真实品牌绿并不相同）。
 * 这些测试确保该问题不会回退。
 */
class BioScanThemeContractTest {

    @Test
    fun neutralThemeIsNotAnyHostBrand() {
        val neutral = BioScanTheme.Neutral
        val iNature = BioScanTheme.INature

        // 中性主题的主色必须与 iNature 品牌绿明显不同
        assertNotEquals(
            "Neutral.accent 不应等于 iNature 的品牌绿",
            iNature.accent.toArgb(),
            neutral.accent.toArgb(),
        )
        // 且必须是低饱和的灰蓝（R/G/B 三通道接近），而非绿色
        val accent = neutral.accent
        val spread = maxOf(accent.red, accent.green, accent.blue) -
            minOf(accent.red, accent.green, accent.blue)
        assertTrue(
            "Neutral.accent 应为低饱和灰蓝，实际通道差=$spread",
            spread < 0.15f,
        )
    }

    @Test
    fun neutralThemeKeepsSystemSemanticBackgrounds() {
        // 中性主题也应复用 iOS 系统语义色，保证设置页观感一致
        val neutral = BioScanTheme.Neutral
        assertEquals(0xFFFFFFFF.toInt(), neutral.systemBackground.light.toArgb())
        assertEquals(0xFFF2F2F7.toInt(), neutral.secondarySystemBackground.light.toArgb())
    }

    @Test
    fun iNatureThemeStillAvailableForExplicitOptIn() {
        // 中性化默认值后，iNature 仍必须可作为显式主题使用
        assertEquals(0xFF248533.toInt(), BioScanTheme.INature.accent.toArgb())
    }
}
