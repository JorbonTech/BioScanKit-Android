package com.bioscankit.android.capture

/**
 * 付费后续跑缓存，对应 iOS `RecognitionContinuation`。
 *
 * 额度不足时把当前照片暂存，等用户完成购买后继续识别；
 * 若购买仍未获得额度，则丢弃暂存内容。
 */
sealed interface RecognitionContinuationResolution<out Payload> {
    data object NoPendingInput : RecognitionContinuationResolution<Nothing>
    data class Resume<Payload>(val payload: Payload) : RecognitionContinuationResolution<Payload>
    data object Discard : RecognitionContinuationResolution<Nothing>
}

class RecognitionContinuation<Payload> {
    private var pendingInput: Payload? = null

    val hasPendingInput: Boolean get() = pendingInput != null

    fun enqueue(input: Payload) {
        pendingInput = input
    }

    fun resolveAfterPaywall(hasRecognitionAccess: Boolean): RecognitionContinuationResolution<Payload> {
        val payload = pendingInput ?: return RecognitionContinuationResolution.NoPendingInput
        pendingInput = null
        return if (hasRecognitionAccess) {
            RecognitionContinuationResolution.Resume(payload)
        } else {
            RecognitionContinuationResolution.Discard
        }
    }

    fun clear() {
        pendingInput = null
    }
}
