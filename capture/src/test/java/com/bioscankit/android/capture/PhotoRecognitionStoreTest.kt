package com.bioscankit.android.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhotoRecognitionStore 状态机测试。
 *
 * 重点验证与 iOS `PhotoRecognitionStore` 一致的顺序语义：
 * 1. 识别前检查额度、成功后消费额度
 * 2. 额度不足时暂存照片并回调打开 Paywall
 * 3. 购买后续跑同一张照片
 * 4. 失败可重试、取消回相机
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoRecognitionStoreTest {

    private val photo = CapturedPhoto(imagePath = "/tmp/a.jpg", source = PhotoSource.Camera)

    @Test
    fun receiveMovesToCropping() = runTest {
        val store = store()
        store.receive(photo)
        assertTrue(store.phase is PhotoRecognitionPhase.Cropping)
    }

    @Test
    fun cancelCropReturnsToCamera() = runTest {
        val store = store()
        store.receive(photo)
        store.cancelCrop()
        assertEquals(PhotoRecognitionPhase.Camera, store.phase)
    }

    @Test
    fun successfulRecognitionConsumesCreditAfterSuccess() = runTest {
        val access = FakeAccess(RecognitionAccess.Credits(2))
        val store = store(access = access)
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertTrue(store.phase is PhotoRecognitionPhase.Completed)
        // 只有成功后才消费，且只消费一次。
        assertEquals(1, access.consumeCount)
        assertEquals(listOf("check", "consume"), access.trace)
    }

    @Test
    fun noCreditEnqueuesPhotoAndRequestsPaywall() = runTest {
        var paywallRequested = false
        val access = FakeAccess(RecognitionAccess.Credits(0))
        val store = store(access = access, onRequiresPaywall = { paywallRequested = true })
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertTrue(paywallRequested)
        assertTrue(store.hasPendingPhoto)
        assertTrue(store.phase is PhotoRecognitionPhase.Cropping)
        assertEquals(0, access.consumeCount)
    }

    @Test
    fun unlimitedAccessNeverConsumes() = runTest {
        val access = FakeAccess(RecognitionAccess.Unlimited)
        val store = store(access = access)
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertTrue(store.phase is PhotoRecognitionPhase.Completed)
        assertEquals(1, access.consumeCount)
    }

    @Test
    fun resumeAfterPurchaseRecognizesPendingPhoto() = runTest {
        var paywallCount = 0
        val access = FakeAccess(RecognitionAccess.Credits(0))
        val client = FakeClient()
        val store = PhotoRecognitionStore(
            client = client,
            accessController = access,
            scope = TestScope(UnconfinedTestDispatcher()),
            onRequiresPaywall = { paywallCount++ },
            onResult = {},
        )
        store.receive(photo)
        store.confirmCrop(photo)
        assertEquals(1, paywallCount)

        // 用户购买成功，额度可用后再续跑。
        access.setAccess(RecognitionAccess.Credits(5))
        store.resumeAfterPurchase()

        assertTrue(store.phase is PhotoRecognitionPhase.Completed)
        assertEquals(1, client.callCount)
        assertFalse(store.hasPendingPhoto)
    }

    @Test
    fun failureKeepsRetryPhoto() = runTest {
        val client = FakeClient(error = IllegalStateException("boom"))
        val store = store(client = client)
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        val failed = store.phase as PhotoRecognitionPhase.Failed
        assertEquals("boom", failed.message)
        assertEquals(photo, failed.retryPhoto)
    }

    @Test
    fun retryReusesSamePhoto() = runTest {
        val client = FakeClient(error = IllegalStateException("boom"))
        val store = store(client = client)
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        client.error = null
        store.retry()
        advanceUntilIdle()

        assertTrue(store.phase is PhotoRecognitionPhase.Completed)
        assertEquals(2, client.callCount)
    }

    @Test
    fun resetClearsStateAndPendingPhoto() = runTest {
        val access = FakeAccess(RecognitionAccess.Credits(0))
        val store = store(access = access)
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()
        assertTrue(store.hasPendingPhoto)

        store.reset()
        assertEquals(PhotoRecognitionPhase.Camera, store.phase)
        assertFalse(store.hasPendingPhoto)
    }

    @Test
    fun unavailableAccessNeverConsumes() = runTest {
        val access = FakeAccess(RecognitionAccess.Unavailable)
        val store = store(access = access)
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertEquals(0, access.consumeCount)
        assertNull((store.phase as? PhotoRecognitionPhase.Completed)?.result)
    }

    @Test
    fun validatorRejectionSkipsRecognitionAndKeepsRetryPhoto() = runTest {
        val client = FakeClient()
        val access = FakeAccess(RecognitionAccess.Unlimited)
        val store = PhotoRecognitionStore(
            client = client,
            accessController = access,
            scope = this,
            onRequiresPaywall = {},
            onResult = {},
            validator = { "Image is too blurry" },
        )
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        val failed = store.phase as PhotoRecognitionPhase.Failed
        assertEquals("Image is too blurry", failed.message)
        assertEquals(photo, failed.retryPhoto)
        // 校验失败不应触发识别，也不应消费额度。
        assertEquals(0, client.callCount)
        assertEquals(0, access.consumeCount)
    }

    @Test
    fun validatorPassingProceedsToRecognition() = runTest {
        val store = PhotoRecognitionStore(
            client = FakeClient(),
            accessController = FakeAccess(RecognitionAccess.Unlimited),
            scope = this,
            onRequiresPaywall = {},
            onResult = {},
            validator = { null },
        )
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertTrue(store.phase is PhotoRecognitionPhase.Completed)
    }

    @Test
    fun resultRouterCanTakeOverResultDelivery() = runTest {
        var onResultCalls = 0
        var routed = 0
        val store = PhotoRecognitionStore(
            client = FakeClient(),
            accessController = FakeAccess(RecognitionAccess.Unlimited),
            scope = this,
            onRequiresPaywall = {},
            onResult = { onResultCalls++ },
            resultRouter = { routed++; true },
        )
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertEquals(1, routed)
        // 宿主已接管分流，组件不应再回调 onResult。
        assertEquals(0, onResultCalls)
    }

    @Test
    fun resultRouterDecliningFallsBackToOnResult() = runTest {
        var onResultCalls = 0
        val store = PhotoRecognitionStore(
            client = FakeClient(),
            accessController = FakeAccess(RecognitionAccess.Unlimited),
            scope = this,
            onRequiresPaywall = {},
            onResult = { onResultCalls++ },
            resultRouter = { false },
        )
        store.receive(photo)
        store.confirmCrop(photo)
        advanceUntilIdle()

        assertEquals(1, onResultCalls)
    }

    private fun TestScope.store(
        access: FakeAccess = FakeAccess(RecognitionAccess.Unlimited),
        client: FakeClient = FakeClient(),
        onRequiresPaywall: () -> Unit = {},
    ) = PhotoRecognitionStore(
        client = client,
        accessController = access,
        scope = this,
        onRequiresPaywall = onRequiresPaywall,
        onResult = {},
    )

    private class FakeClient(var error: Throwable? = null) : PhotoRecognitionClient<String> {
        var callCount = 0
        override suspend fun recognize(imagePath: String, context: RecognitionContext): String {
            callCount++
            error?.let { throw it }
            return "rock"
        }
    }

    private class FakeAccess(initial: RecognitionAccess) : RecognitionAccessControlling {
        private var access = initial
        var consumeCount = 0
        val trace = mutableListOf<String>()

        fun setAccess(value: RecognitionAccess) {
            access = value
        }

        override suspend fun currentAccess(): RecognitionAccess {
            trace += "check"
            return access
        }

        override suspend fun consumeAfterSuccessfulRecognition() {
            trace += "consume"
            consumeCount++
        }
    }
}
