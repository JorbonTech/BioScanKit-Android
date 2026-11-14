package com.bioscankit.android.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/** 相机权限状态，对齐 iOS `CameraPermissionState`。 */
enum class CameraPermissionState {
    NotDetermined,
    Authorized,
    Denied,
    Restricted;

    companion object {
        fun from(context: Context): CameraPermissionState =
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Authorized
            } else {
                NotDetermined
            }
    }
}

/** 拍照结果：成功返回文件，失败返回错误信息。 */
sealed interface CameraCaptureResult {
    data class Success(val file: File) : CameraCaptureResult
    data class Failure(val message: String) : CameraCaptureResult
}

/**
 * CameraX 相机会话封装，对应 iOS `CameraSessionController`。
 *
 * 职责与 iOS 一致：权限、启停、拍照落盘、变焦、对焦、闪光灯。
 * **不含**页面 UI，也不含识别编排——那些由 `CameraPage` 与 `PhotoRecognitionStore` 负责。
 *
 * 配置刻意与 MrRock 既有 `CameraPreview` 保持一致
 * （`CAPTURE_MODE_MINIMIZE_LATENCY` + `PreviewView.COMPATIBLE`），避免迁移时损失设备兼容性。
 */
class CameraSessionController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    var permission: CameraPermissionState by mutableStateOf(CameraPermissionState.from(context))
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    var zoomRatio: Float by mutableStateOf(1f)
        private set

    var isRunning: Boolean by mutableStateOf(false)
        private set

    var flashEnabled: Boolean by mutableStateOf(false)
        private set

    /** 供 `AndroidView` 挂载的预览控件；由调用方持有并放进 `preview` 插槽。 */
    val previewView: PreviewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null

    val hasFlashUnit: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun refreshPermission() {
        permission = CameraPermissionState.from(context)
    }

    /** 绑定相机到 lifecycle；重复调用会先解绑，避免多实例抢占。 */
    suspend fun start(): Boolean {
        if (permission != CameraPermissionState.Authorized) {
            errorMessage = "Camera permission is not granted."
            return false
        }
        return runCatching {
            val cameraProvider = awaitProvider()
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            imageCapture = capture

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
            camera?.cameraInfo?.zoomState?.value?.let { zoomRatio = it.zoomRatio }
            isRunning = true
            errorMessage = null
            true
        }.getOrElse { error ->
            errorMessage = error.message
            isRunning = false
            false
        }
    }

    fun stop() {
        provider?.unbindAll()
        provider = null
        camera = null
        imageCapture = null
        isRunning = false
    }

    /**
     * 拍照并落盘到 [outputFile]。
     *
     * 失败时不抛出，统一返回 [CameraCaptureResult.Failure]，与 iOS 的
     * `errorMessage` 上报方式一致，便于 UI 统一处理。
     */
    suspend fun capturePhoto(outputFile: File): CameraCaptureResult {
        val capture = imageCapture
            ?: return CameraCaptureResult.Failure("The camera is not ready yet.")
        outputFile.parentFile?.mkdirs()
        outputFile.delete()

        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) {
                            continuation.resume(CameraCaptureResult.Success(outputFile))
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        outputFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(
                                CameraCaptureResult.Failure(
                                    exception.message ?: "Unable to save the photo.",
                                )
                            )
                        }
                    }
                },
            )
        }
    }

    fun applyZoomRatio(ratio: Float) {
        val activeCamera = camera ?: return
        val state = activeCamera.cameraInfo.zoomState.value ?: return
        val target = ratio.coerceIn(state.minZoomRatio, min(state.maxZoomRatio, MAX_ZOOM))
        activeCamera.cameraControl.setZoomRatio(target)
        zoomRatio = target
    }

    /** 按预览控件坐标对焦（对应 iOS `focus(at devicePoint:)`）。 */
    fun focusAt(previewX: Float, previewY: Float) {
        val activeCamera = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(previewX, previewY)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        activeCamera.cameraControl.startFocusAndMetering(action)
    }

    fun toggleFlash() {
        val capture = imageCapture ?: return
        if (!hasFlashUnit) return
        flashEnabled = !flashEnabled
        capture.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        camera?.cameraControl?.enableTorch(flashEnabled)
    }

    fun applyFlash(enabled: Boolean) {
        val capture = imageCapture ?: return
        if (!hasFlashUnit) return
        flashEnabled = enabled
        capture.flashMode = if (enabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        camera?.cameraControl?.enableTorch(enabled)
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { if (continuation.isActive) continuation.resume(it) }
                        .onFailure { if (continuation.isActive) continuation.cancel(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    companion object {
        /** 常见手机 8x 已是数码插值极限，与 iOS 的 `min(videoMaxZoomFactor, 8)` 对齐。 */
        const val MAX_ZOOM = 8f
    }
}
