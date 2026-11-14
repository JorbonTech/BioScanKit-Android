package com.bioscankit.android.settings

import androidx.compose.ui.graphics.toArgb
import com.bioscankit.android.design.BioScanTheme
import com.bioscankit.android.design.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 锁定「与 iOS 对齐」的关键视觉常量，避免后续改动悄悄回退。
 *
 * 期望值来自 BioScanKit-iOS 实测：
 * - `BioScanTheme` 系统语义色取自 iOS `.systemBackground` / `.secondarySystemBackground`
 */
class SettingsVisualMetricsTest {
    @Test
    fun themeExposesSystemSemanticBackgroundsMatchingIOS() {
        val theme = BioScanTheme.INature
        // iOS: Color(uiColor: .systemBackground) = 白 / 黑
        assertEquals(0xFFFFFFFF.toInt(), theme.systemBackground.light.toArgb())
        assertEquals(0xFF000000.toInt(), theme.systemBackground.dark.toArgb())
        // iOS: Color(uiColor: .secondarySystemBackground) = #F2F2F7 / #1C1C1E
        assertEquals(0xFFF2F2F7.toInt(), theme.secondarySystemBackground.light.toArgb())
        assertEquals(0xFF1C1C1E.toInt(), theme.secondarySystemBackground.dark.toArgb())
    }

    @Test
    fun settingsPaletteUsesSystemSemanticColorsNotBrandTint() {
        val theme = BioScanTheme.INature
        val colors = theme.resolve(isDark = false)
        // 设置页卡片必须是系统中性灰，不能等于品牌浅色
        assertNotEquals(
            "卡片底色不应等于 elevatedCardBackground（品牌浅色）",
            colors.elevatedCardBackground.toArgb(),
            colors.secondarySystemBackground.toArgb(),
        )
        assertEquals(0xFFF2F2F7.toInt(), colors.secondarySystemBackground.toArgb())
    }
}
