package com.bioscankit.android.capture

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁定 `cameraFinderSize` 与 iOS `CameraPageLayout.metrics` 的逐行等价。
 *
 * iOS 公式：
 * ```
 * areaHeight = clamp(min(h * 0.40, h - 250), 120, 380)
 * maximumFinderSize = min(configured, max(areaHeight, 96))
 * ```
 */
class CameraPageLayoutTest {
    @Test
    fun matchesIOSMetrics() {
        val cases = listOf(
            // availableHeight, configured, expected
            Triple(600f, 300f, 240f), // min(240, 350)=240 → min(300,240)=240
            Triple(900f, 300f, 300f), // min(360, 650)=360 → min(300,360)=300
            Triple(400f, 300f, 150f), // min(160, 150)=150
            Triple(200f, 300f, 120f), // min(80,-50) → clamp 120
            Triple(1600f, 400f, 380f), // min(640,1350)=640 → clamp 380
            Triple(1600f, 200f, 200f), // min(200, 380)=200
        )
        cases.forEach { (available, configured, expected) ->
            assertEquals(
                "available=$available configured=$configured",
                expected,
                cameraFinderSize(available.dp, configured.dp).value,
                0.01f,
            )
        }
    }
}
