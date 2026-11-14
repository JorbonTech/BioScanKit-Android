package com.bioscankit.android.cloudsync.drive

import android.util.Base64
import com.bioscankit.android.cloudsync.CloudFavoriteSnapshot
import org.json.JSONObject

/**
 * 二进制字段与文本之间的编解码。
 *
 * 抽出来是因为 `android.util.Base64` 在 JVM 单测里不可用；测试可以注入自己的实现，
 * 生产代码默认走 [AndroidBase64Coder]。
 */
interface ByteArrayTextCoder {
    fun encode(bytes: ByteArray): String
    fun decode(text: String): ByteArray
}

/** 生产实现：标准 Base64，不换行。 */
object AndroidBase64Coder : ByteArrayTextCoder {
    override fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    override fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}

/**
 * 收藏快照与 JSON 之间的编解码，对应 iOS `CloudFavoriteSnapshot` 的 Codable 实现。
 *
 * 字段名与 iOS 保持一致，方便以后两端互换存储后端（iCloud ↔ Drive）。
 */
object CloudFavoriteSnapshotCodec {
    private const val KEY_ID = "id"
    private const val KEY_SOURCE_CREATED_AT = "sourceCreatedAt"
    private const val KEY_FAVORITE_MODIFIED_AT = "favoriteModifiedAt"
    private const val KEY_IS_FAVORITE = "isFavorite"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_THUMBNAIL = "thumbnailData"
    private const val KEY_SCHEMA_VERSION = "schemaVersion"

    fun encode(
        snapshot: CloudFavoriteSnapshot,
        coder: ByteArrayTextCoder = AndroidBase64Coder,
    ): JSONObject = JSONObject().apply {
        put(KEY_ID, snapshot.id)
        put(KEY_SOURCE_CREATED_AT, snapshot.sourceCreatedAt)
        put(KEY_FAVORITE_MODIFIED_AT, snapshot.favoriteModifiedAt)
        put(KEY_IS_FAVORITE, snapshot.isFavorite)
        put(KEY_PAYLOAD, coder.encode(snapshot.payload))
        snapshot.thumbnailData?.let { put(KEY_THUMBNAIL, coder.encode(it)) }
        put(KEY_SCHEMA_VERSION, snapshot.schemaVersion)
    }

    fun encodeToString(
        snapshot: CloudFavoriteSnapshot,
        coder: ByteArrayTextCoder = AndroidBase64Coder,
    ): String = encode(snapshot, coder).toString()

    fun decode(
        raw: String,
        coder: ByteArrayTextCoder = AndroidBase64Coder,
    ): CloudFavoriteSnapshot? = runCatching { decode(JSONObject(raw), coder) }.getOrNull()

    fun decode(
        json: JSONObject,
        coder: ByteArrayTextCoder = AndroidBase64Coder,
    ): CloudFavoriteSnapshot = CloudFavoriteSnapshot(
        id = json.getString(KEY_ID),
        sourceCreatedAt = json.optLong(KEY_SOURCE_CREATED_AT, 0L),
        favoriteModifiedAt = json.optLong(KEY_FAVORITE_MODIFIED_AT, 0L),
        isFavorite = json.optBoolean(KEY_IS_FAVORITE, true),
        payload = coder.decode(json.getString(KEY_PAYLOAD)),
        thumbnailData = json.optString(KEY_THUMBNAIL).takeIf { it.isNotEmpty() }
            ?.let { coder.decode(it) },
        schemaVersion = json.optInt(KEY_SCHEMA_VERSION, 1),
    )
}
