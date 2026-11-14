package com.bioscankit.android.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 识别流程状态机，对齐 iOS `PhotoRecognitionStore`。
 *
 * 与 iOS 一致的要点：
 * 1. 阶段流转 `Camera → Cropping → Recognizing → Completed / Failed`。
 * 2. 识别**前**检查额度，识别**成功后**才消费额度。
 * 3. 额度不足时把照片存进 [RecognitionContinuation]，并回调 `onRequiresPaywall`。
 * 4. 购买完成调用 [resumeAfterPurchase] 继续跑同一张照片。
 * 5. 取消识别回到 `Camera`；失败可通过 [retry] 用同一张照片重试。
 *
 * 线程模型：所有方法应在主线程调用（Compose 状态读取需要）。
 */
class PhotoRecognitionStore<Result>(
    private val client: PhotoRecognitionClient<Result>,
    private val accessController: RecognitionAccessControlling,
    private val scope: CoroutineScope,
    private val onRequiresPaywall: () -> Unit,
    private val onResult: (Result) -> Unit,
    /** 识别前校验（例如模糊检测）；为 null 时直接放行。 */
    private val validator: PhotoRecognitionValidator? = null,
    /** 结果分流（例如按置信度分层）；为 null 时全部走 [onResult]。 */
    private val resultRouter: RecognitionResultRouter<Result>? = null,
) {
    var phase: PhotoRecognitionPhase<Result> by mutableStateOf(PhotoRecognitionPhase.Camera)
        private set

    private val continuation = RecognitionContinuation<CapturedPhoto>()
    private var recognitionJob: Job? = null

    val hasPendingPhoto: Boolean get() = continuation.hasPendingInput

    /** 接收相册/拍照得到的照片，进入裁剪阶段。 */
    fun receive(photo: CapturedPhoto) {
        phase = PhotoRecognitionPhase.Cropping(photo)
    }

    /** 取消裁剪，回到相机（对应 iOS `cancelCrop`）。 */
    fun cancelCrop() {
        phase = PhotoRecognitionPhase.Camera
    }

    /** 裁剪确认后开始识别（对应 iOS `confirmCrop`）。 */
    fun confirmCrop(photo: CapturedPhoto) {
        startRecognitionIfAllowed(photo)
    }

    /** 失败后重试同一张照片（对应 iOS `retry`）。 */
    fun retry() {
        val failed = phase as? PhotoRecognitionPhase.Failed
        val photo = failed?.retryPhoto
        if (photo == null) {
            phase = PhotoRecognitionPhase.Camera
            return
        }
        startRecognitionIfAllowed(photo)
    }

    /** 购买完成后继续识别暂存的照片（对应 iOS `resumeAfterPurchase`）。 */
    fun resumeAfterPurchase() {
        val resolution = continuation.resolveAfterPaywall(hasRecognitionAccess = true)
        if (resolution is RecognitionContinuationResolution.Resume) {
            startRecognitionIfAllowed(resolution.payload)
        }
    }

    /**
     * 在流程外发生的失败（例如拍照落盘失败）也要进失败态，
     * 与 iOS 把 `errorMessage` 呈现在同一层的方式一致。
     */
    fun notifyFailure(message: String) {
        phase = PhotoRecognitionPhase.Failed(message = message, retryPhoto = null)
    }

    /** 重置到初始状态并取消进行中的识别。 */
    fun reset() {
        recognitionJob?.cancel()
        recognitionJob = null
        continuation.clear()
        phase = PhotoRecognitionPhase.Camera
    }

    private fun startRecognitionIfAllowed(photo: CapturedPhoto) {
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            val access = accessController.currentAccess()
            if (!access.canRecognize) {
                // 额度不足：暂存照片，交给宿主打开 Paywall。
                continuation.enqueue(photo)
                phase = PhotoRecognitionPhase.Cropping(photo)
                onRequiresPaywall()
                return@launch
            }

            // 识别前校验（模糊等）。失败时进失败态，可按同一张照片重试。
            val rejection = runCatching { validator?.validate(photo.imagePath) }.getOrNull()
            if (rejection != null) {
                phase = PhotoRecognitionPhase.Failed(message = rejection, retryPhoto = photo)
                return@launch
            }

            phase = PhotoRecognitionPhase.Recognizing(photo)

            runCatching {
                client.recognize(photo.imagePath, photo.context)
            }.onSuccess { result ->
                // 识别成功后才消费额度（与 iOS 顺序一致）。
                runCatching { accessController.consumeAfterSuccessfulRecognition() }
                phase = PhotoRecognitionPhase.Completed(result)
                // 宿主可自行分流（如低置信度走候选页），否则统一回调结果。
                val handled = resultRouter?.route(result) == true
                if (!handled) onResult(result)
            }.onFailure { error ->
                phase = PhotoRecognitionPhase.Failed(
                    message = error.message.orEmpty(),
                    retryPhoto = photo,
                )
            }
        }
    }
}
