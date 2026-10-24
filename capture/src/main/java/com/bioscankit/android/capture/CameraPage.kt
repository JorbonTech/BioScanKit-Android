package com.bioscankit.android.capture

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bioscankit.android.design.BioScanTheme
import com.bioscankit.android.design.resolve

data class CameraPageActions(
    val close: () -> Unit,
    val showHelp: (() -> Unit)? = null,
    val choosePhoto: () -> Unit,
    val capture: () -> Unit,
    val focusFrameChanged: (Rect) -> Unit = {},
)

/**
 * Native Compose camera page skeleton matching BioScanKit's iNature hierarchy.
 * CameraX ownership and gesture handling stay in the host app through slots.
 */
@Composable
fun CameraPage(
    configuration: CameraScreenConfiguration = CameraScreenConfiguration.INature,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black,
    onFinderBoundsChanged: (Rect) -> Unit = {},
    preview: @Composable BoxScope.() -> Unit,
    topBar: @Composable ColumnScope.() -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
    previewOverlay: @Composable BoxScope.() -> Unit = {},
    extraOverlay: @Composable BoxScope.() -> Unit = {},
) {
    CameraSystemBars()
    Box(
        modifier = modifier.fillMaxSize().background(containerColor),
    ) {
        preview()
        previewOverlay()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = 0.54f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
            ) {
                topBar()
                Spacer(Modifier.weight(1f))
                CameraInstructionPill(
                    text = configuration.instruction,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(22.dp))
                CameraFinder(
                    configuration = configuration,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onBoundsChanged = onFinderBoundsChanged,
                )
                Spacer(Modifier.weight(1f))
                bottomBar()
            }
        }
        extraOverlay()
    }
}

/** Keeps camera chrome continuous through both system-bar safe areas. */
@Composable
private fun CameraSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)

    DisposableEffect(window, view) {
        val previousStatusColor = window.statusBarColor
        val previousNavigationColor = window.navigationBarColor
        val previousStatusIcons = controller.isAppearanceLightStatusBars
        val previousNavigationIcons = controller.isAppearanceLightNavigationBars
        val previousContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }
        onDispose {
            window.statusBarColor = previousStatusColor
            window.navigationBarColor = previousNavigationColor
            controller.isAppearanceLightStatusBars = previousStatusIcons
            controller.isAppearanceLightNavigationBars = previousNavigationIcons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrast != null) {
                window.isNavigationBarContrastEnforced = previousContrast
            }
        }
    }

    SideEffect {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
}

/** Compact camera Tips card matching the shared iNature/iOS presentation. */
@Composable
fun CameraTipsOverlay(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    theme: BioScanTheme = BioScanTheme.INature,
) {
    val colors = theme.resolve(isSystemInDarkTheme())
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.elevatedCardBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.primaryText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** iNature camera header shared by every BioScanKit host. */
@Composable
fun CameraTopControls(
    onClose: () -> Unit,
    onHelp: () -> Unit,
    closeContentDescription: String,
    helpContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showsBackButton: Boolean = false,
    statusText: String? = null,
    statusPrefix: String? = null,
    statusColor: Color = Color.White.copy(alpha = 0.94f),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraCircleAction(
                onClick = onClose,
                enabled = enabled,
                contentDescription = closeContentDescription,
            ) {
                Icon(
                    imageVector = if (showsBackButton) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            CameraCircleAction(
                onClick = onHelp,
                enabled = enabled,
                contentDescription = helpContentDescription,
            ) {
                Icon(Icons.Filled.HelpOutline, null, Modifier.size(20.dp))
            }
        }

        if (!statusText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.45f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = listOfNotNull(statusPrefix?.takeIf(String::isNotBlank), statusText)
                        .joinToString("  "),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                )
            }
        }
    }
}

/** iNature camera shutter row shared by every BioScanKit host. */
@Composable
fun CameraBottomControls(
    onChoosePhoto: () -> Unit,
    onCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    choosePhotoContentDescription: String,
    captureContentDescription: String,
    flashContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canCapture: Boolean = true,
    flashEnabled: Boolean = false,
    flashAvailable: Boolean = true,
    albumPreview: ImageBitmap? = null,
    shutterColor: Color = Color(0xFFF8ED9E),
    onShutterBoundsChanged: (Rect) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraCircleAction(
            onClick = onChoosePhoto,
            enabled = enabled,
            contentDescription = choosePhotoContentDescription,
        ) {
            if (albumPreview != null) {
                androidx.compose.foundation.Image(
                    bitmap = albumPreview,
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.12f)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(20.dp))
            }
        }

        Surface(
            modifier = Modifier
                .testTag("recognition-shutter")
                .onGloballyPositioned { onShutterBoundsChanged(it.boundsInRoot()) },
            onClick = onCapture,
            enabled = enabled && canCapture,
            shape = CircleShape,
            color = Color.White.copy(alpha = if (canCapture) 0.10f else 0.06f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .background(
                            if (canCapture) shutterColor else Color.White.copy(alpha = 0.35f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!enabled) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }

        CameraCircleAction(
            onClick = onToggleFlash,
            enabled = enabled && canCapture && flashAvailable,
            contentDescription = flashContentDescription,
        ) {
            Icon(
                imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = null,
                tint = if (flashEnabled) shutterColor else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CameraCircleAction(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.testTag(contentDescription),
    ) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun CameraInstructionPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.90f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CameraFinder(
    configuration: CameraScreenConfiguration = CameraScreenConfiguration.INature,
    modifier: Modifier = Modifier,
    onBoundsChanged: (Rect) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.78f)
            .widthIn(max = configuration.finderMaximumSize)
            .aspectRatio(1f)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .testTag("recognition-guide-frame"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val edge = size.minDimension * 0.18f
            val color = configuration.finderColor
            drawLine(color, Offset.Zero, Offset(edge, 0f), strokeWidth = stroke)
            drawLine(color, Offset.Zero, Offset(0f, edge), strokeWidth = stroke)
            drawLine(color, Offset(size.width, 0f), Offset(size.width - edge, 0f), strokeWidth = stroke)
            drawLine(color, Offset(size.width, 0f), Offset(size.width, edge), strokeWidth = stroke)
            drawLine(color, Offset(0f, size.height), Offset(edge, size.height), strokeWidth = stroke)
            drawLine(color, Offset(0f, size.height), Offset(0f, size.height - edge), strokeWidth = stroke)
            drawLine(color, Offset(size.width, size.height), Offset(size.width - edge, size.height), strokeWidth = stroke)
            drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - edge), strokeWidth = stroke)
            drawRect(Color.White.copy(alpha = 0.14f), style = Stroke(width = 1.dp.toPx()))
        }
    }
}
