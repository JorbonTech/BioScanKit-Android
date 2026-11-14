package com.bioscankit.android.capture

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription as semanticsContentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    isProcessing: Boolean = false,
    onFinderBoundsChanged: (Rect) -> Unit = {},
    preview: @Composable BoxScope.() -> Unit,
    topBar: @Composable ColumnScope.() -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
    previewOverlay: @Composable BoxScope.() -> Unit = {},
    extraOverlay: @Composable BoxScope.() -> Unit = {},
) {
    CameraSystemBars()
    val isLegacy = configuration.layoutStyle == CameraPageLayoutStyle.INatureLegacy
    // 与 iOS `vignette` 一致：iNatureLegacy 上 0.45→0.02、下 0.02→0.52；adaptive 略深。
    val topScrim = if (isLegacy) 0.45f else 0.50f
    val bottomScrim = if (isLegacy) 0.52f else 0.58f

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(containerColor),
    ) {
        val availableHeight = maxHeight
        val finderSize = cameraFinderSize(
            availableHeight = availableHeight,
            configuredMaximumFinderSize = configuration.finderMaximumSize,
        )
        preview()
        previewOverlay()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = topScrim),
                            Color.Black.copy(alpha = 0.04f),
                            Color.Black.copy(alpha = bottomScrim),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (isLegacy) 16.dp else 12.dp,
                    bottom = 18.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
            ) {
                topBar()
                Spacer(Modifier.weight(1f))
                // 识别中切换提示文案，对应 iOS `processingInstruction`。
                CameraInstructionPill(
                    text = if (isProcessing) {
                        configuration.processingInstruction
                            ?: stringResource(R.string.bioscan_capture_processing_instruction)
                    } else {
                        configuration.instruction
                            ?: stringResource(R.string.bioscan_capture_instruction)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(if (isLegacy) 22.dp else 12.dp))
                CameraFinder(
                    configuration = configuration,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    finderSize = finderSize,
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

/** 相机帮助浮层的一条提示，对应 iOS `ScanTipLight`。 */
@Immutable
data class CameraTip(
    val icon: ImageVector,
    val text: String,
)

/**
 * 相机帮助浮层。
 *
 * 两种形态，对齐 iOS：
 * - 传 [items]（非空）→ 卡片内「标题 + 带图标的提示列表」，对应 iOS `helpOverlay`
 * - 否则 → 旧的「标题 + 正文」居中卡片（向后兼容）
 */
@Composable
fun CameraTipsOverlay(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    theme: BioScanTheme = BioScanTheme.Neutral,
    items: List<CameraTip> = emptyList(),
) {
    val colors = theme.resolve(isSystemInDarkTheme())
    val useList = items.isNotEmpty()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (useList) 0.55f else 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .padding(horizontal = if (useList) 24.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                // iOS: 圆角 20（列表态）/ 16（旧形态）
                .clip(RoundedCornerShape(if (useList) 20.dp else 16.dp))
                .background(colors.elevatedCardBackground)
                .then(
                    if (useList) {
                        Modifier.shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color.Black.copy(alpha = 0.22f),
                            spotColor = Color.Black.copy(alpha = 0.22f),
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(if (useList) 18.dp else 16.dp),
            horizontalAlignment = if (useList) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (useList) 12.dp else 12.dp),
        ) {
            Text(
                text = title,
                // iOS: 18 bold（列表态）
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (useList) 18.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.primaryText,
                textAlign = if (useList) TextAlign.Start else TextAlign.Center,
            )
            if (useList) {
                items.forEach { tip ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // iOS: 30×30 圆角 10 图标底座
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardBackground)
                                .border(
                                    width = 1.dp,
                                    color = colors.border,
                                    shape = RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = tip.icon,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Text(
                            text = tip.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.secondaryText,
                        )
                    }
                }
            } else {
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
}

/** iNature camera header shared by every BioScanKit host. */
@Composable
fun CameraTopControls(
    onClose: () -> Unit,
    onHelp: () -> Unit,
    closeContentDescription: String? = null,
    helpContentDescription: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showsBackButton: Boolean = false,
    statusText: String? = null,
    statusPrefix: String? = null,
    statusColor: Color = Color.White.copy(alpha = 0.94f),
    /** 状态胶囊左侧图标，对应 iOS `statusSystemImage`；传 null 则不显示图标。 */
    statusIcon: ImageVector? = Icons.Filled.Memory,
    /** 是否显示右上角帮助按钮，对应 iOS `showHelp == nil` 时留空占位。 */
    showsHelpButton: Boolean = true,
    /**
     * 传入相机配置时，[statusIcon] 与状态色自动取自
     * `statusSystemImage` / `statusForegroundColor`，无需再手填。
     */
    configuration: CameraScreenConfiguration? = null,
) {
    val resolvedStatusIcon = configuration?.let { cameraStatusIcon(it.statusSystemImage) } ?: statusIcon
    // 宿主未显式传 statusText 时，回落到配置里的 statusText（与 iOS 同源）。
    val resolvedStatusText = statusText ?: configuration?.statusText
        // 两者都未传时使用组件内置文案，与 iOS `On-device recognition` 一致。
        ?: stringResource(R.string.bioscan_capture_status_on_device)
    val resolvedCloseContentDescription = closeContentDescription
        ?: configuration?.closeContentDescription
        ?: stringResource(R.string.bioscan_capture_close_camera)
    val resolvedHelpContentDescription = helpContentDescription
        ?: configuration?.helpContentDescription
        ?: stringResource(R.string.bioscan_capture_show_camera_tips)
    val resolvedStatusColor = configuration
        ?.statusForegroundColor
        ?.resolve(androidx.compose.foundation.isSystemInDarkTheme())
        ?: statusColor
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
                contentDescription = resolvedCloseContentDescription,
            ) {
                Icon(
                    imageVector = if (showsBackButton) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (showsHelpButton) {
                CameraCircleAction(
                    onClick = onHelp,
                    enabled = enabled,
                    contentDescription = resolvedHelpContentDescription,
                ) {
                    Icon(Icons.Filled.HelpOutline, null, Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.size(42.dp))
            }
        }

        if (!resolvedStatusText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.45f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    resolvedStatusIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = resolvedStatusColor,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                    Text(
                        text = listOfNotNull(statusPrefix?.takeIf(String::isNotBlank), resolvedStatusText)
                            .joinToString("  "),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = resolvedStatusColor,
                    )
                }
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
    choosePhotoContentDescription: String? = null,
    captureContentDescription: String? = null,
    flashContentDescription: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canCapture: Boolean = true,
    flashEnabled: Boolean = false,
    flashAvailable: Boolean = true,
    albumPreview: ImageBitmap? = null,
    /** 快门内圈颜色。iOS 固定为白色；宿主可覆盖（如 MrRock 用品牌青色）。 */
    shutterColor: Color = Color.White,
    /** 是否显示相册按钮，对应 iOS `supportsPhotoLibrary`。 */
    supportsPhotoLibrary: Boolean = true,
    /** 是否显示闪光灯按钮，对应 iOS `supportsFlash`。 */
    supportsFlash: Boolean = true,
    /** 相机页布局风格，决定控件尺寸与内边距，对应 iOS `layoutStyle`。 */
    layoutStyle: CameraPageLayoutStyle = CameraPageLayoutStyle.INatureLegacy,
    /**
     * 传入相机配置时，[supportsPhotoLibrary]、[supportsFlash]、[layoutStyle]
     * 与快门/控件尺寸自动取自配置，无需再手填。
     */
    configuration: CameraScreenConfiguration? = null,
    onShutterBoundsChanged: (Rect) -> Unit = {},
) {
    val resolvedLayoutStyle = configuration?.layoutStyle ?: layoutStyle
    val resolvedSupportsPhotoLibrary = configuration?.supportsPhotoLibrary ?: supportsPhotoLibrary
    val resolvedSupportsFlash = configuration?.supportsFlash ?: supportsFlash
    // 与 iOS 一致：闪光灯按钮的无障碍文案随开关状态切换。
    val resolvedFlashContentDescription = configuration?.let {
        if (flashEnabled) it.flashOnContentDescription else it.flashOffContentDescription
    } ?: flashContentDescription ?: stringResource(
        if (flashEnabled) R.string.bioscan_capture_flash_off else R.string.bioscan_capture_flash_on,
    )
    val resolvedChoosePhotoContentDescription = choosePhotoContentDescription
        ?: configuration?.choosePhotoContentDescription
        ?: stringResource(R.string.bioscan_capture_choose_photo)
    val resolvedCaptureContentDescription = captureContentDescription
        ?: configuration?.captureContentDescription
        ?: stringResource(R.string.bioscan_capture_take_photo)
    // iOS: iNatureLegacy 42 / adaptive 54；快门外圈 84 / 88，内圈 64 / 68。
    val isLegacy = resolvedLayoutStyle == CameraPageLayoutStyle.INatureLegacy
    val sideControlSize = if (isLegacy) 42.dp else 54.dp
    val shutterOuterSize = if (isLegacy) 84.dp else 88.dp
    val shutterInnerSize = if (isLegacy) 64.dp else 68.dp
    val horizontalPadding = if (isLegacy) 0.dp else 10.dp
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resolvedSupportsPhotoLibrary) {
            CameraCircleAction(
                onClick = onChoosePhoto,
                enabled = enabled,
                contentDescription = resolvedChoosePhotoContentDescription,
                size = sideControlSize,
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
        } else {
            Spacer(Modifier.size(sideControlSize))
        }

        Surface(
            modifier = Modifier
                .testTag("recognition-shutter")
                .semantics { semanticsContentDescription = resolvedCaptureContentDescription }
                .onGloballyPositioned { onShutterBoundsChanged(it.boundsInRoot()) },
            onClick = onCapture,
            enabled = enabled && canCapture,            shape = CircleShape,
            color = Color.White.copy(alpha = if (canCapture) 0.10f else 0.06f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(Modifier.size(shutterOuterSize), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(shutterInnerSize)
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

        if (resolvedSupportsFlash) {
            CameraCircleAction(
                onClick = onToggleFlash,
                enabled = enabled && canCapture && flashAvailable,
                contentDescription = resolvedFlashContentDescription,
                size = sideControlSize,
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(sideControlSize))
        }
    }
}

@Composable
private fun CameraCircleAction(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    size: Dp = 42.dp,
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
        modifier = Modifier
            .testTag(contentDescription)
            .semantics { semanticsContentDescription = contentDescription },
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { content() }
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

/**
 * 取景框。与 iOS `CameraPage.cameraFinder(size:)` 对齐：
 *
 * - `finderStyle == CornerMarkers`：四角角标（臂长 12%，线宽 4dp，平头/尖角）
 * - `finderStyle == Scanner`：圆角框 + 渐变填充 + 上下往复的扫描线
 * - 尺寸由 [cameraFinderSize] 按可用高度推导，不再固定 78% 宽
 */
@Composable
fun CameraFinder(
    configuration: CameraScreenConfiguration = CameraScreenConfiguration.INature,
    modifier: Modifier = Modifier,
    finderSize: Dp = configuration.finderMaximumSize,
    onBoundsChanged: (Rect) -> Unit = {},
) {
    val cornerRadius = configuration.finderCornerRadius
    val color = configuration.finderColor
    val isScanner = configuration.finderStyle == CameraFinderStyle.Scanner

    val sweep = if (isScanner) {
        val transition = rememberInfiniteTransition(label = "camera-finder-sweep")
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "camera-finder-sweep-offset",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(finderSize)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .testTag("recognition-guide-frame"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (isScanner) {
                // 渐变底 + 描边，对应 iOS `.scanner` 分支
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
                // 扫描线：宽 70%，高 5dp，随动画上下移动
                val lineHeight = 5.dp.toPx()
                val lineY = size.height / 2f + sweep * size.height * 0.31f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            color.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.95f),
                            color.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset(size.width * 0.15f, lineY - lineHeight / 2f),
                    size = Size(size.width * 0.70f, lineHeight),
                    cornerRadius = CornerRadius(lineHeight / 2f),
                )
            } else {
                // 四角角标：臂长 12%、线宽 4dp、butt/miter（iOS rounded = false 时同款）
                val stroke = 4.dp.toPx()
                val arm = size.minDimension * 0.12f
                drawLine(color, Offset(0f, arm), Offset.Zero, strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset.Zero, Offset(arm, 0f), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset(size.width - arm, 0f), Offset(size.width, 0f), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, arm), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset(size.width, size.height - arm), Offset(size.width, size.height), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset(size.width, size.height), Offset(size.width - arm, size.height), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset(arm, size.height), Offset(0f, size.height), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawLine(color, Offset(0f, size.height), Offset(0f, size.height - arm), strokeWidth = stroke, cap = StrokeCap.Butt)
                drawRect(Color.White.copy(alpha = 0.14f), style = Stroke(width = 1.dp.toPx()))
            }
        }
    }
}

/**
 * 取景框尺寸推导，与 iOS `CameraPageLayout.metrics` 逐行对齐：
 *
 * ```
 * areaHeight = clamp(min(availableHeight * 0.40, availableHeight - 250), 120, 380)
 * maximumFinderSize = min(configured, max(areaHeight, 96))
 * ```
 */
fun cameraFinderSize(
    availableHeight: Dp,
    configuredMaximumFinderSize: Dp,
): Dp {
    val available = availableHeight.value
    val proportional = available * 0.40f
    val afterChrome = available - 250f
    val areaHeight = minOf(maxOf(minOf(proportional, afterChrome), 120f), 380f)
    val maximum = minOf(configuredMaximumFinderSize.value, maxOf(areaHeight, 96f))
    return maximum.dp
}

/**
 * 把 iOS 的 SF Symbol 名（`statusSystemImage`）映射成 Compose 图标，
 * 让 Android 侧也能消费同一个配置字段。
 */
private fun cameraStatusIcon(systemImage: String): ImageVector? = when (systemImage) {
    "cpu", "cpu.fill" -> Icons.Filled.Memory
    "camera.fill" -> Icons.Filled.PhotoCamera
    "bolt.fill" -> Icons.Filled.FlashOn
    "wifi.slash" -> Icons.Filled.CloudOff
    else -> Icons.Filled.Memory
}
