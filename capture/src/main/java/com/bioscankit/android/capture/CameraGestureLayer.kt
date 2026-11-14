package com.bioscankit.android.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 相机手势层，与 iOS `CameraPage` 的 `zoomGesture` / `focusGesture` 对齐。
 *
 * 由 [CameraScreenConfiguration] 驱动：
 * - `supportsTapToFocus == false` → 不响应点击对焦
 * - `supportsPinchToZoom == false` → 不响应捏合缩放
 * - `maximumZoomFactor` → 缩放上限（iOS 默认 8，iNature 预设 6）
 *
 * 对焦框与缩放提示的视觉保留在组件内，宿主只需提供 [controller]。
 */
@Composable
fun CameraGestureLayer(
    controller: CameraSessionController?,
    configuration: CameraScreenConfiguration = CameraScreenConfiguration.INature,
    zoomRatio: Float,
    onZoomRatioChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusPoint by remember(controller) { mutableStateOf<Offset?>(null) }
    var zoomHintGeneration by remember(controller) { mutableIntStateOf(0) }
    var zoomHintVisible by remember(controller) { mutableStateOf(false) }
    val density = LocalDensity.current
    val maxZoom = configuration.maximumZoomFactor.coerceAtLeast(1f)

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            kotlinx.coroutines.delay(900)
            focusPoint = null
        }
    }
    LaunchedEffect(zoomHintGeneration) {
        if (zoomHintGeneration > 0) {
            zoomHintVisible = true
            kotlinx.coroutines.delay(900)
            zoomHintVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (configuration.supportsTapToFocus) {
                    Modifier.pointerInput(controller) {
                        detectTapGestures { offset ->
                            val active = controller ?: return@detectTapGestures
                            active.focusAt(offset.x, offset.y)
                            focusPoint = offset
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (configuration.supportsPinchToZoom) {
                    Modifier.pointerInput(controller, maxZoom) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            val active = controller ?: return@detectTransformGestures
                            val target = (active.zoomRatio * gestureZoom).coerceIn(1f, maxZoom)
                            active.applyZoomRatio(target)
                            onZoomRatioChanged(target)
                            zoomHintGeneration += 1
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        focusPoint?.let { point ->
            val halfSizePx = with(density) { 22.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - halfSizePx).roundToInt(),
                            (point.y - halfSizePx).roundToInt(),
                        )
                    }
                    .size(44.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
            )
        }
        if (zoomHintVisible) {
            Text(
                text = String.format(java.util.Locale.US, "%.1fx", zoomRatio),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
