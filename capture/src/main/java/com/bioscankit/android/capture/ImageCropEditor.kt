package com.bioscankit.android.capture

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bioscankit.android.design.resolve
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Complete interactive crop page shared by BioScanKit hosts. */
@Composable
fun ImageCropEditor(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
    configuration: CropEditorConfiguration = CropEditorConfiguration.INature,
    enabled: Boolean = true,
    onConfirmBoundsChanged: (Rect) -> Unit = {},
    extraOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val colors = configuration.theme.resolve(androidx.compose.foundation.isSystemInDarkTheme())
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var minimumZoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var translation by remember(bitmap) { mutableStateOf(Offset.Zero) }

    fun cropRect(): Rect {
        val availableWidth = (canvasSize.width - with(density) { 40.dp.toPx() }).coerceAtLeast(1f)
        val side = min(availableWidth * configuration.cropScale, canvasSize.height * configuration.cropScale)
        return Rect(
            (canvasSize.width - side) / 2f,
            (canvasSize.height - side) / 2f,
            (canvasSize.width + side) / 2f,
            (canvasSize.height + side) / 2f,
        )
    }

    fun baseScale(): Float = min(
        canvasSize.width / bitmap.width.toFloat(),
        canvasSize.height / bitmap.height.toFloat(),
    )

    fun imageRect(targetZoom: Float = zoom, targetTranslation: Offset = translation): Rect {
        val scale = baseScale() * targetZoom
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f) + targetTranslation
        return Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f)
    }

    fun clampedTranslation(targetZoom: Float, proposed: Offset): Offset {
        val rect = imageRect(targetZoom, proposed)
        return CropGeometry.clampedOffset(proposed, rect, cropRect())
    }

    fun croppedBitmap(): Bitmap? {
        val normalized = CropGeometry.normalizedCropRect(cropRect(), imageRect())
        val left = (normalized.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (normalized.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (normalized.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (normalized.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        return runCatching { Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top) }.getOrNull()
    }

    LaunchedEffect(bitmap, canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        val crop = cropRect()
        val scale = baseScale()
        minimumZoom = max(
            crop.width / max(bitmap.width * scale, 1f),
            crop.height / max(bitmap.height * scale, 1f),
        )
        zoom = minimumZoom
        translation = Offset.Zero
    }

    val preview = remember(bitmap, canvasSize, zoom, translation) {
        croppedBitmap()?.let { crop ->
            Bitmap.createScaledBitmap(crop, 112, 112, true).also {
                // Bitmap.createBitmap may return the source itself when the crop covers
                // the complete image. The editor still owns and displays that bitmap, so
                // recycling it here crashes the next Compose draw frame.
                if (it !== crop && crop !== bitmap) crop.recycle()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF171A1E))
            .onSizeChanged { canvasSize = it },
    ) {
        if (canvasSize != IntSize.Zero) {
            val scale = baseScale()
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        with(density) { (bitmap.width * scale).toDp() },
                        with(density) { (bitmap.height * scale).toDp() },
                    )
                    .graphicsLayer { scaleX = zoom; scaleY = zoom }
                    .offset(
                        with(density) { translation.x.toDp() },
                        with(density) { translation.y.toDp() },
                    )
                    .pointerInput(bitmap, minimumZoom, enabled) {
                        if (!enabled) return@pointerInput
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val nextZoom = (zoom * gestureZoom).coerceIn(minimumZoom, 8f)
                            zoom = nextZoom
                            translation = clampedTranslation(nextZoom, translation + pan)
                        }
                    },
                contentScale = ContentScale.FillBounds,
            )
            CropMask(cropRect(), colors.accent, configuration.showsGrid)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp),
        ) {
            Surface(
                onClick = onCancel,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 20.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f),
                contentColor = Color.White,
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.48f),
            ) {
                Text(
                    text = "⌖  ${configuration.guidanceText}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.align(Alignment.End).size(56.dp)
                    .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp)),
            ) {
                preview?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CropButton("Reset", false, enabled, {
                    zoom = minimumZoom
                    translation = Offset.Zero
                }, Modifier.weight(1f), colors.accent)
                CropButton(configuration.confirmTitle, true, enabled, {
                    croppedBitmap()?.let(onConfirm)
                }, Modifier.weight(1f), colors.accent, onConfirmBoundsChanged)
            }
        }
        extraOverlay()
    }
}

@Composable
private fun BoxScope.CropMask(cropRect: Rect, accent: Color, showsGrid: Boolean) {
    Canvas(Modifier.fillMaxSize().testTag("recognition-crop-frame")) {
        drawRect(Color.Black.copy(alpha = 0.36f), size = Size(size.width, cropRect.top))
        drawRect(Color.Black.copy(alpha = 0.36f), Offset(0f, cropRect.bottom), Size(size.width, size.height - cropRect.bottom))
        drawRect(Color.Black.copy(alpha = 0.36f), Offset(0f, cropRect.top), Size(cropRect.left, cropRect.height))
        drawRect(Color.Black.copy(alpha = 0.36f), Offset(cropRect.right, cropRect.top), Size(size.width - cropRect.right, cropRect.height))
        drawRoundRect(accent, cropRect.topLeft, cropRect.size, CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
        if (showsGrid) {
            val line = Color.White.copy(alpha = 0.18f)
            repeat(2) { index ->
                val fraction = (index + 1) / 3f
                drawLine(line, Offset(cropRect.left + cropRect.width * fraction, cropRect.top), Offset(cropRect.left + cropRect.width * fraction, cropRect.bottom), 1.dp.toPx())
                drawLine(line, Offset(cropRect.left, cropRect.top + cropRect.height * fraction), Offset(cropRect.right, cropRect.top + cropRect.height * fraction), 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun CropButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    accent: Color,
    onBoundsChanged: (Rect) -> Unit = {},
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
        shape = RoundedCornerShape(12.dp),
        color = if (filled) accent else Color.White.copy(alpha = 0.20f),
        contentColor = Color.White,
    ) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}
