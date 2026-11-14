package com.bioscankit.android.cloudsync.drive

import com.bioscankit.android.cloudsync.CloudFavoriteSnapshot
import com.bioscankit.android.cloudsync.CloudFavoritesRemoteStore
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用 Google Drive 的 `appDataFolder` 存收藏快照。
 *
 * 为什么用 App Data 而不是普通 Drive 目录：App Data 是应用私有空间，用户在 Drive 里看不到、
 * 也不能误删；作用域只有 `drive.appdata`，不需要“查看全部云端硬盘”的敏感权限，过审更简单。
 *
 * 存储布局：一条收藏一个文件，文件名是 `[namespace]-[sha1(id)].json`，
 * 元数据里带 `snapshotId` 等字段。这样 upsert/delete 都是单文件操作，不需要读改写整份清单。
 *
 * 可靠性策略：
 * - 429 / 5xx / 网络错误按 [retryDelaysMillis] 退避重试，尊重 `Retry-After`
 * - 401 时强制刷新 token 重试一次
 * - 拉取时并发下载（[downloadConcurrency]），按 `md5Checksum` 跳过未变化的文件
 * - 同名文件（创建竞态）只取最新的一份，不再让整次同步失败
 */
class GoogleDriveCloudFavoritesRemoteStore(
    private val tokenProvider: GoogleDriveAccessTokenProvider,
    private val namespace: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    // 默认指向 Google 正式环境；单测里换成 MockWebServer 的地址。
    private val apiBase: String = "https://www.googleapis.com/drive/v3",
    private val uploadBase: String = "https://www.googleapis.com/upload/drive/v3",
    // 单测里换成纯 JVM 实现，生产默认 android.util.Base64。
    private val coder: ByteArrayTextCoder = AndroidBase64Coder,
    // 重试间隔；传空列表即关闭重试（单测用）。
    private val retryDelaysMillis: List<Long> = listOf(400L, 1_200L),
    private val downloadConcurrency: Int = 4,
) : CloudFavoritesRemoteStore {

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        tokenProvider.accessToken() != null
    }

    /**
     * 一次同步会连续 upsert 多条记录，每次都重新 list 会退化成 O(n²) 次请求。
     * 这里缓存一次目录结果（60 秒内有效），upsert/delete 同步维护它。
     */
    private var cachedFiles: MutableMap<String, String>? = null
    private var cachedAt: Long = 0L

    /** fileId → (md5, 已解码快照)，同一进程内重复同步时跳过下载。 */
    private val decodedCache = mutableMapOf<String, CachedSnapshot>()

    private data class CachedSnapshot(val md5: String, val snapshot: CloudFavoriteSnapshot)

    override suspend fun fetchAll(): List<CloudFavoriteSnapshot> = withContext(Dispatchers.IO) {
        val files = listFiles()
        // 同名文件意味着同一个 snapshotId 出现了重复文件（多设备同时创建）。
        // 取 modifiedTime 最新的一份即可，其余保留在 Drive 上，不在这里做删除。
        val selected = files
            .groupBy { it.name }
            .map { (_, group) -> group.maxByOrNull { it.modifiedTime } ?: group.first() }

        cachedFiles = selected.associateTo(mutableMapOf()) { it.name to it.id }
        cachedAt = System.currentTimeMillis()

        val semaphore = Semaphore(downloadConcurrency.coerceAtLeast(1))
        coroutineScope {
            selected.map { file ->
                async { semaphore.withPermit { downloadSnapshot(file) } }
            }.awaitAll()
        }
    }

    override suspend fun upsert(snapshot: CloudFavoriteSnapshot) = withContext(Dispatchers.IO) {
        val body = CloudFavoriteSnapshotCodec.encodeToString(snapshot, coder)
        val existing = findFile(snapshot.id)
        if (existing == null) {
            createFile(fileName(snapshot.id), body, snapshot)
        } else {
            updateFile(existing.id, body, snapshot)
            // 内容变了，md5 缓存作废。
            decodedCache.remove(existing.id)
        }
        Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val existing = findFile(id) ?: return@withContext
        authorized("DELETE", "$apiBase/files/${existing.id}").use { response ->
            // 已经不存在视为删除成功（缓存过期 / 其他设备先删了）。
            if (response.code != 404) ensureSuccess(response)
        }
        cachedFiles?.remove(existing.name)
        decodedCache.remove(existing.id)
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        listFiles().forEach { file ->
            authorized("DELETE", "$apiBase/files/${file.id}").use { response ->
                if (response.code != 404) ensureSuccess(response)
            }
        }
        cachedFiles?.clear()
        decodedCache.clear()
        Unit
    }

    // ---- Drive 调用 ----

    private data class RemoteFile(
        val id: String,
        val name: String,
        val modifiedTime: String = "",
        val md5: String = "",
    )

    private suspend fun listFiles(): List<RemoteFile> {
        val files = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        val visitedTokens = mutableSetOf<String>()
        do {
            val url = buildString {
                append(apiBase)
                append("/files?spaces=appDataFolder")
                append("&pageSize=1000")
                append("&fields=nextPageToken,files(id,name,modifiedTime,md5Checksum)")
                append("&q=")
                append(urlEncode("'appDataFolder' in parents and trashed = false"))
                pageToken?.let { append("&pageToken=").append(urlEncode(it)) }
            }
            val json = JSONObject(executeString(authorized("GET", url)))
            files += parseFiles(json).filter { it.name.startsWith("$namespace-") }
            val next = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
            // 防御异常分页：Drive 返回重复 token 时不要死循环。
            pageToken = next?.takeIf { visitedTokens.add(it) }
        } while (pageToken != null)
        return files
    }

    private fun parseFiles(json: JSONObject): List<RemoteFile> {
        val array = json.optJSONArray("files") ?: return emptyList()
        val result = mutableListOf<RemoteFile>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isEmpty() || name.isEmpty()) continue
            result += RemoteFile(
                id = id,
                name = name,
                modifiedTime = item.optString("modifiedTime"),
                md5 = item.optString("md5Checksum"),
            )
        }
        return result
    }

    private suspend fun findFile(snapshotId: String): RemoteFile? {
        val name = fileName(snapshotId)
        cachedFiles
            ?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_TTL_MILLIS }
            ?.let { cache -> return cache[name]?.let { RemoteFile(it, name) } }

        // 没有目录缓存时按文件名精确查询，避免为了找一条记录拉全量目录。
        val url = buildString {
            append(apiBase)
            append("/files?spaces=appDataFolder&pageSize=10")
            append("&fields=files(id,name,modifiedTime,md5Checksum)")
            append("&q=")
            append(urlEncode("name = '$name' and 'appDataFolder' in parents and trashed = false"))
        }
        val matches = parseFiles(JSONObject(executeString(authorized("GET", url))))
        return matches.maxByOrNull { it.modifiedTime } ?: matches.firstOrNull()
    }

    private suspend fun downloadSnapshot(file: RemoteFile): CloudFavoriteSnapshot {
        decodedCache[file.id]
            ?.takeIf { file.md5.isNotEmpty() && it.md5 == file.md5 }
            ?.let { return it.snapshot }

        val raw = download(file.id)
        val snapshot = CloudFavoriteSnapshotCodec.decode(raw, coder)
            ?: throw IOException("Invalid cloud favorite snapshot: ${file.id}")
        if (file.md5.isNotEmpty()) {
            decodedCache[file.id] = CachedSnapshot(file.md5, snapshot)
        }
        return snapshot
    }

    private suspend fun download(fileId: String): String =
        executeString(authorized("GET", "$apiBase/files/$fileId?alt=media"))

    private suspend fun createFile(name: String, body: String, snapshot: CloudFavoriteSnapshot) {
        val metadata = metadataFor(name, snapshot, includeParents = true)
        val (boundary, multipart) = buildMultipart(metadata.toString(), body)
        val response = authorized(
            "POST",
            "$uploadBase/files?uploadType=multipart&fields=id",
            multipart,
            "multipart/related; boundary=$boundary",
        )
        val fileId = executeString(response).let { payload ->
            runCatching { JSONObject(payload).optString("id") }.getOrNull()
        }
        // 记下新建文件的 id，否则同一轮同步里后续 upsert 会重复创建同名文件。
        if (!fileId.isNullOrEmpty()) cachedFiles?.put(name, fileId)
    }

    private suspend fun updateFile(fileId: String, body: String, snapshot: CloudFavoriteSnapshot) {
        // 更新时不带 parents：Drive 不允许把已有文件“移动”到 appDataFolder。
        val metadata = metadataFor(fileName(snapshot.id), snapshot, includeParents = false)
        val (boundary, multipart) = buildMultipart(metadata.toString(), body)
        val response = authorized(
            "PATCH",
            "$uploadBase/files/$fileId?uploadType=multipart&fields=id",
            multipart,
            "multipart/related; boundary=$boundary",
        )
        if (response.code == 404) {
            // 目录缓存过期、文件已被其他设备删除：回退成新建。
            response.close()
            cachedFiles?.remove(fileName(snapshot.id))
            createFile(fileName(snapshot.id), body, snapshot)
            return
        }
        response.use { ensureSuccess(it) }
    }

    private fun metadataFor(
        name: String,
        snapshot: CloudFavoriteSnapshot,
        includeParents: Boolean,
    ): JSONObject =
        JSONObject().apply {
            put("name", name)
            if (includeParents) {
                // 新文件必须声明父目录为 appDataFolder，否则会落到用户可见的“我的云端硬盘”。
                put("parents", JSONArray().put("appDataFolder"))
            }
            put(
                "appProperties",
                JSONObject().apply {
                    put("snapshotId", snapshot.id)
                    put("favoriteModifiedAt", snapshot.favoriteModifiedAt.toString())
                    put("sourceCreatedAt", snapshot.sourceCreatedAt.toString())
                    put("isFavorite", snapshot.isFavorite.toString())
                    put("schemaVersion", snapshot.schemaVersion.toString())
                },
            )
        }

    private fun buildMultipart(metadata: String, body: String): Pair<String, ByteArray> {
        val boundary = "bioscan-${System.nanoTime()}"
        val prefix = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(body).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
        return boundary to prefix.toByteArray(Charsets.UTF_8)
    }

    /**
     * 带重试的 Drive 请求。
     *
     * - 401：强制刷新 token 后重试一次
     * - 429 / 5xx / IO 异常：按退避间隔重试，`Retry-After` 优先
     */
    private suspend fun authorized(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String = JSON,
    ): Response {
        var authRetried = false
        var retries = 0
        var forceRefresh = false

        while (true) {
            val token = tokenProvider.accessToken(forceRefresh = forceRefresh)
                ?: throw IOException("Google Drive 未登录")
            forceRefresh = false

            val builder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                "POST", "PATCH" -> builder.method(
                    method,
                    (body ?: ByteArray(0)).toRequestBody(contentType.toMediaType()),
                )
            }

            val response = try {
                httpClient.newCall(builder.build()).execute()
            } catch (io: IOException) {
                if (retries < retryDelaysMillis.size) {
                    delay(retryDelaysMillis[retries++])
                    continue
                }
                throw io
            }

            if (response.code == 401 && !authRetried) {
                response.close()
                authRetried = true
                forceRefresh = true
                continue
            }

            if (isTransient(response.code) && retries < retryDelaysMillis.size) {
                val retryAfter = response.header("Retry-After")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.times(1_000L)
                response.close()
                delay(retryAfter?.takeIf { it > 0 } ?: retryDelaysMillis[retries])
                retries++
                continue
            }

            return response
        }
    }

    private fun isTransient(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    private fun executeString(response: Response): String =
        response.use {
            ensureSuccess(it)
            it.body?.string().orEmpty()
        }

    private fun ensureSuccess(response: Response) {
        if (response.isSuccessful) return
        val detail = response.body?.string().orEmpty()
        throw GoogleDriveException(response.code, detail)
    }

    private fun fileName(snapshotId: String): String =
        "$namespace-${sha1(snapshotId)}.json"

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val JSON = "application/json; charset=UTF-8"
        private const val CACHE_TTL_MILLIS = 60_000L

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/** Drive 返回非 2xx 时抛出，带上 HTTP 状态码方便上层区分鉴权失败与临时错误。 */
class GoogleDriveException(
    val code: Int,
    detail: String,
) : IOException("Drive $code: ${detail.take(300)}")
