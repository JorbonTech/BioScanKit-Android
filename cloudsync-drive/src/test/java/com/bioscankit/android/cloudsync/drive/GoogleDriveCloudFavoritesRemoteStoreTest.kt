package com.bioscankit.android.cloudsync.drive

import com.bioscankit.android.cloudsync.CloudFavoriteSnapshot
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 用 MockWebServer 验证 Drive 调用形态：创建 / 更新 / 拉取 / 删除。
 * 不需要真实 Google 账号，也不会访问网络。
 */
class GoogleDriveCloudFavoritesRemoteStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var store: GoogleDriveCloudFavoritesRemoteStore
    private lateinit var apiBase: String
    private lateinit var uploadBase: String
    private val requests = mutableListOf<RecordedRequest>()

    private val tokenProvider = object : GoogleDriveAccessTokenProvider {
        override suspend fun accessToken(forceRefresh: Boolean): String = "test-token"
        override fun signedInAccount(): String = "tester@example.com"
        override fun signOut() = Unit
    }

    private val hexCoder = object : ByteArrayTextCoder {
        override fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
        override fun decode(text: String): ByteArray =
            text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiBase = server.url("/drive/v3").toString().trimEnd('/')
        uploadBase = server.url("/upload/drive/v3").toString().trimEnd('/')
        store = GoogleDriveCloudFavoritesRemoteStore(
            tokenProvider = tokenProvider,
            namespace = "natureear",
            apiBase = apiBase,
            uploadBase = uploadBase,
            coder = hexCoder,
            // 失败用例里不等退避，重试行为由专门的用例覆盖。
            retryDelaysMillis = emptyList(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `upsert creates a file when none exists`() = runTest {
        enqueueList(emptyList())
        server.enqueue(MockResponse().setBody("""{"id":"file-1"}"""))

        store.upsert(snapshot("fav-1"))

        val upload = lastRequest("POST")
        assertTrue(upload.path!!.contains("uploadType=multipart"))
        assertTrue(upload.body.readUtf8().contains("\"snapshotId\""))
        assertTrue(upload.getHeader("Authorization") == "Bearer test-token")
    }

    @Test
    fun `upsert patches the existing file`() = runTest {
        val name = fileNameFor("fav-1")
        enqueueList(listOf(RemoteFileJson("file-1", name)))
        server.enqueue(MockResponse().setBody("""{"id":"file-1"}"""))

        store.upsert(snapshot("fav-1"))

        val patch = lastRequest("PATCH")
        assertTrue(patch.path!!.contains("/files/file-1"))
        // 更新不能带 parents，否则 Drive 会拒绝“移动到 appDataFolder”。
        assertTrue(!patch.body.readUtf8().contains("\"parents\""))
    }

    @Test
    fun `fetchAll downloads and decodes snapshots`() = runTest {
        val name = fileNameFor("fav-2")
        enqueueList(listOf(RemoteFileJson("file-2", name)))
        val body = CloudFavoriteSnapshotCodec.encodeToString(snapshot("fav-2"), hexCoder)
        server.enqueue(MockResponse().setBody(body))

        val all = store.fetchAll()

        assertEquals(1, all.size)
        assertEquals("fav-2", all.first().id)
        assertTrue(lastRequest("GET").path!!.contains("alt=media"))
    }

    @Test
    fun `files outside the namespace are ignored`() = runTest {
        enqueueList(
            listOf(
                RemoteFileJson("file-9", "other-app-abc.json"),
                RemoteFileJson("file-10", fileNameFor("fav-3")),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                CloudFavoriteSnapshotCodec.encodeToString(snapshot("fav-3"), hexCoder),
            ),
        )

        val all = store.fetchAll()

        assertEquals(1, all.size)
        assertEquals("fav-3", all.first().id)
    }

    @Test
    fun `delete removes the matching file`() = runTest {
        enqueueList(listOf(RemoteFileJson("file-4", fileNameFor("fav-4"))))
        server.enqueue(MockResponse().setBody(""))

        store.delete("fav-4")

        assertTrue(lastRequest("DELETE").path!!.contains("/files/file-4"))
    }

    @Test
    fun `failed download must not appear as successful empty sync`() = runTest {
        enqueueList(listOf(RemoteFileJson("file-1", fileNameFor("fav-1"))))
        server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
        try {
            store.fetchAll()
            org.junit.Assert.fail("Download failure must propagate")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message.orEmpty().contains("503"))
        }
    }

    @Test
    fun `failed deletion must propagate`() = runTest {
        enqueueList(listOf(RemoteFileJson("file-1", fileNameFor("fav-1"))))
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        try {
            store.delete("fav-1")
            org.junit.Assert.fail("Deletion failure must propagate")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message.orEmpty().contains("403"))
        }
    }

    @Test
    fun `duplicate files keep the newest snapshot`() = runTest {
        val name = fileNameFor("fav-1")
        enqueueList(
            listOf(
                RemoteFileJson("file-1", name, modifiedTime = "2026-01-01T00:00:00Z"),
                RemoteFileJson("file-2", name, modifiedTime = "2026-02-01T00:00:00Z"),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                CloudFavoriteSnapshotCodec.encodeToString(snapshot("fav-1"), hexCoder),
            ),
        )

        val all = store.fetchAll()

        assertEquals(1, all.size)
        // 只下载 modifiedTime 最新的那一份。
        assertTrue(lastRequest("GET").path!!.contains("/files/file-2"))
    }

    @Test
    fun `transient failures are retried`() = runTest {
        val retryingStore = GoogleDriveCloudFavoritesRemoteStore(
            tokenProvider = tokenProvider,
            namespace = "natureear",
            apiBase = apiBase,
            uploadBase = uploadBase,
            coder = hexCoder,
            retryDelaysMillis = listOf(0L),
            downloadConcurrency = 1,
        )
        enqueueList(listOf(RemoteFileJson("file-1", fileNameFor("fav-1"))))
        server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
        server.enqueue(
            MockResponse().setBody(
                CloudFavoriteSnapshotCodec.encodeToString(snapshot("fav-1"), hexCoder),
            ),
        )

        val all = retryingStore.fetchAll()

        assertEquals(1, all.size)
    }

    @Test
    fun `expired token is refreshed and the request retried`() = runTest {
        var forceRefreshed = false
        val refreshingProvider = object : GoogleDriveAccessTokenProvider {
            override suspend fun accessToken(forceRefresh: Boolean): String {
                if (forceRefresh) forceRefreshed = true
                return if (forceRefreshed) "fresh-token" else "stale-token"
            }

            override fun signedInAccount(): String = "tester@example.com"
            override fun signOut() = Unit
        }
        val refreshingStore = GoogleDriveCloudFavoritesRemoteStore(
            tokenProvider = refreshingProvider,
            namespace = "natureear",
            apiBase = apiBase,
            uploadBase = uploadBase,
            coder = hexCoder,
            retryDelaysMillis = emptyList(),
        )
        server.enqueue(MockResponse().setResponseCode(401).setBody("expired"))
        enqueueList(emptyList())

        assertEquals(0, refreshingStore.fetchAll().size)
        assertEquals("Bearer fresh-token", lastRequest("GET").getHeader("Authorization"))
    }

    @Test
    fun `patch 404 falls back to creating the file`() = runTest {
        val name = fileNameFor("fav-1")
        enqueueList(listOf(RemoteFileJson("file-1", name)))
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(MockResponse().setBody("""{"id":"file-new"}"""))

        store.upsert(snapshot("fav-1"))

        assertTrue(lastRequest("POST").path!!.contains("uploadType=multipart"))
    }

    @Test
    fun `delete tolerates an already missing file`() = runTest {
        enqueueList(listOf(RemoteFileJson("file-1", fileNameFor("fav-1"))))
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        // 不应抛异常：文件已经被其他设备删掉了。
        store.delete("fav-1")
    }

    // ---- helpers ----

    private fun snapshot(id: String) = CloudFavoriteSnapshot(
        id = id,
        sourceCreatedAt = 100,
        favoriteModifiedAt = 200,
        payload = """{"species":"robin"}""".toByteArray(),
    )

    private data class RemoteFileJson(
        val id: String,
        val name: String,
        val modifiedTime: String = "",
        val md5: String = "",
    )

    private fun enqueueList(files: List<RemoteFileJson>) {
        val array = files.joinToString(",") {
            JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("modifiedTime", it.modifiedTime)
                put("md5Checksum", it.md5)
            }.toString()
        }
        server.enqueue(MockResponse().setBody("""{"files":[$array]}"""))
    }

    private fun lastRequest(method: String): RecordedRequest {
        while (server.requestCount > requests.size) {
            requests += server.takeRequest()
        }
        return requests.last { it.method == method }
    }

    /** 与 store 内部命名规则一致：namespace + sha1(id) + .json。 */
    private fun fileNameFor(id: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "natureear-$digest.json"
    }
}
