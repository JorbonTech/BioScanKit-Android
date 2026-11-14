package com.bioscankit.android.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bioscankit.android.design.BioScanDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** 识别流程页配置，对应 iOS `PhotoRecognitionConfiguration`。 */
data class PhotoRecognitionConfiguration(
    val camera: CameraScreenConfiguration = CameraScreenConfiguration.INature,
    val crop: CropEditorConfiguration = CropEditorConfiguration.INature,
    val processing: ProcessingScreenConfiguration = ProcessingScreenConfiguration.INature,
    val supportsPhotoLibrary: Boolean = true,
) {
    companion object {
        val INature = PhotoRecognitionConfiguration()
    }
}

/**
 * 识别流程容器，对应 iOS `PhotoRecognitionFlow`。
 *
 * 只负责按 [PhotoRecognitionStore.phase] 切换页面，不持有业务逻辑。
 * 相机预览通过 [cameraPreview] 插槽注入，保留各 App 已有的相机能力
 * （与 iOS 的泛型 View Slot 策略一致）。
 */
@Composable
fun <Result> PhotoRecognitionFlow(
    store: PhotoRecognitionStore<Result>,
    configuration: PhotoRecognitionConfiguration,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** 相机预览插槽；为 null 时使用组件自带的 CameraX 预览。 */
    cameraPreview: (@Composable (CameraSessionController) -> Unit)? = null,
    /** 自定义相机页外层（例如叠加新手引导）。 */
    cameraPage: (@Composable (CameraSessionController, @Composable () -> Unit) -> Unit)? = null,
    onPhotoConfirmed: (CapturedPhoto) -> Unit = {},
) {
    when (val phase = store.phase) {
        is PhotoRecognitionPhase.Camera -> {
            CameraRecognitionStage(
                store = store,
                configuration = configuration,
                onCancel = onCancel,
                modifier = modifier,
                cameraPreview = cameraPreview,
                cameraPage = cameraPage,
            )
        }

        is PhotoRecognitionPhase.Cropping -> {
            // 整图解码放到 IO 线程，避免主线程卡顿。
            val bitmap by produceState<android.graphics.Bitmap?>(
                initialValue = null,
                phase.photo.imagePath,
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(phase.photo.imagePath) }.getOrNull()
                }
            }
            val decoded = bitmap
            if (decoded == null) {
                RecognitionFailureScreen(
                    message = "Unable to load the cropped photo.",
                    onRetry = store::retry,
                    onCancel = store::reset,
                    modifier = modifier,
                )
            } else {
                ImageCropEditor(
                    bitmap = decoded,
                    configuration = configuration.crop,
                    modifier = modifier,
                    onCancel = store::cancelCrop,
                    onConfirm = { cropped ->
                        val saved = saveCroppedPhoto(cropped, phase.photo)
                        if (saved != null) {
                            onPhotoConfirmed(phase.photo)
                            store.confirmCrop(saved)
                        }
                    },
                )
            }
        }

        is PhotoRecognitionPhase.Recognizing -> {
            val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null,
                phase.photo.imagePath,
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        BitmapFactory.decodeFile(phase.photo.imagePath)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            RecognitionProcessingScreen(
                preview = preview,
                configuration = configuration.processing,
                modifier = modifier,
            )
        }

        is PhotoRecognitionPhase.Failed -> {
            RecognitionFailureScreen(
                message = phase.message,
                onRetry = store::retry,
                onCancel = store::reset,
                modifier = modifier,
            )
        }

        is PhotoRecognitionPhase.Completed -> {
            // 结果由宿主路由到自己的结果页，这里只占位。
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

/**
 * 相机阶段：把 [CameraPage] 的插槽与 [CameraSessionController] 组装起来。
 *
 * 页面骨架、取景框、上下控制条都来自公共组件；相机驱动由
 * [CameraSessionController] 提供，宿主的自定义预览可通过 [cameraPreview] 覆盖。
 */
@Composable
private fun <Result> CameraRecognitionStage(
    store: PhotoRecognitionStore<Result>,
    configuration: PhotoRecognitionConfiguration,
    onCancel: () -> Unit,
    modifier: Modifier,
    cameraPreview: (@Composable (CameraSessionController) -> Unit)?,
    cameraPage: (@Composable (CameraSessionController, @Composable () -> Unit) -> Unit)?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { CameraSessionController(context, lifecycleOwner) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(controller, lifecycleOwner) {
        controller.refreshPermission()
        if (controller.permission == CameraPermissionState.Authorized) {
            controller.start()
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    // 拍照入口：内部用 isCapturing 防重入，避免连点触发多次拍照。
    val captureAction: () -> Unit = {
        if (!isCapturing) {
        isCapturing = true
        scope.launch {
            try {
                val rawFile = File(
                    context.cacheDir,
                    "bioscan-captures/${System.currentTimeMillis()}.jpg",
                )
                when (val result = controller.capturePhoto(rawFile)) {
                    is CameraCaptureResult.Success -> {
                        val normalizedFile = File(
                            context.cacheDir,
                            "bioscan-captures/normalized-${System.currentTimeMillis()}.jpg",
                        )
                        val bitmap = BioScanImageProcessing.decodeFromFile(result.file)
                        val saved = bitmap?.let { BioScanImageProcessing.saveToJpeg(it, normalizedFile) }
                        result.file.delete()
                        if (saved != null) {
                            controller.applyFlash(false)
                            store.receive(
                                CapturedPhoto(
                                    imagePath = saved.absolutePath,
                                    source = PhotoSource.Camera,
                                )
                            )
                        }
                    }

                    is CameraCaptureResult.Failure -> {
                        // 拍照失败要让用户看见，不能静默吞掉。
                        store.notifyFailure(
                            message = result.message.ifBlank { "Unable to take the photo." },
                        )
                    }
                }
            } finally {
                isCapturing = false
            }
        }
        }
    }

    val previewSlot: @Composable BoxScope.() -> Unit = {
        if (cameraPreview != null) {
            cameraPreview(controller)
        } else {
            AndroidView(
                factory = { controller.previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // 宿主可完全接管页面（例如叠加新手引导层）。
    if (cameraPage != null) {
        cameraPage(controller) {
            CameraPage(
                configuration = configuration.camera,
                modifier = modifier,
                preview = previewSlot,
                topBar = { DefaultCameraTopBar(configuration, onCancel) },
                bottomBar = { DefaultCameraBottomBar(configuration, controller) { captureAction() } },
            )
        }
        return
    }

    CameraPage(
        configuration = configuration.camera,
        modifier = modifier,
        preview = previewSlot,
        topBar = { DefaultCameraTopBar(configuration, onCancel) },
        bottomBar = { DefaultCameraBottomBar(configuration, controller) { captureAction() } },
    )
}

@Composable
private fun ColumnScope.DefaultCameraTopBar(
    configuration: PhotoRecognitionConfiguration,
    onCancel: () -> Unit,
) {
    CameraTopControls(
        onClose = onCancel,
        onHelp = {},
        closeContentDescription = configuration.camera.closeContentDescription,
        helpContentDescription = configuration.camera.helpContentDescription,
        statusText = configuration.camera.statusText,
    )
}

@Composable
private fun ColumnScope.DefaultCameraBottomBar(
    configuration: PhotoRecognitionConfiguration,
    controller: CameraSessionController,
    onCapture: () -> Unit,
) {
    CameraBottomControls(
        onChoosePhoto = {},
        onCapture = onCapture,
        onToggleFlash = controller::toggleFlash,
        choosePhotoContentDescription = configuration.camera.choosePhotoContentDescription,
        captureContentDescription = configuration.camera.captureContentDescription,
        flashContentDescription = configuration.camera.flashContentDescription,
        canCapture = controller.isRunning,
        flashEnabled = controller.flashEnabled,
        flashAvailable = controller.hasFlashUnit,
        enabled = configuration.supportsPhotoLibrary, // 占位：由宿主覆盖时替换
    )
}

/** 失败页，对应 iOS `RecognitionFailureView`。 */
@Composable
fun RecognitionFailureScreen(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recognition Failed",
    retryTitle: String = "Try Again",
    cancelTitle: String = "Back to Camera",
) {
    val colors = BioScanDesign.colors
    // 与 iOS `RecognitionFailureView` 对齐：
    // 警告图标（theme.warning / largeTitle）→ 标题 → 说明 → 主按钮 → 纯文字次按钮。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = colors.secondaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            RecognitionActionButton(
                title = retryTitle,
                filled = true,
                onClick = onRetry,
            )
            // iOS 的 "Back to Camera" 是无边框纯文字按钮
            Text(
                text = cancelTitle,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RecognitionActionButton(
    title: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val colors = BioScanDesign.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (filled) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (filled) Color.White else colors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun saveCroppedPhoto(
    cropped: android.graphics.Bitmap,
    photo: CapturedPhoto,
): CapturedPhoto? {
    val target = java.io.File(photo.imagePath.replace(".jpg", "-cropped.jpg"))
    val output = java.io.FileOutputStream(target)
    return runCatching {
        cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output)
        output.close()
        photo.copy(imagePath = target.absolutePath)
    }.getOrNull()
}

private fun android.graphics.Bitmap.asImageBitmapOrNull(): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching { this.asImageBitmap() }.getOrNull()
