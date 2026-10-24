package com.bioscankit.android.cloudsync

/**
 * 一条可同步的收藏快照，对应 iOS `CloudFavoriteSnapshot`。
 *
 * [payload] 由宿主 App 决定编码格式（JSON 等），本库只负责搬运与冲突判定。
 */
data class CloudFavoriteSnapshot(
    val id: String,
    val sourceCreatedAt: Long,
    val favoriteModifiedAt: Long,
    val isFavorite: Boolean = true,
    val payload: ByteArray,
    val thumbnailData: ByteArray? = null,
    val schemaVersion: Int = 1,
) {
    // ByteArray 是引用类型，data class 需要手动实现相等性。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CloudFavoriteSnapshot) return false
        return id == other.id &&
            sourceCreatedAt == other.sourceCreatedAt &&
            favoriteModifiedAt == other.favoriteModifiedAt &&
            isFavorite == other.isFavorite &&
            payload.contentEquals(other.payload) &&
            (thumbnailData?.contentEquals(other.thumbnailData ?: ByteArray(0))
                ?: (other.thumbnailData == null)) &&
            schemaVersion == other.schemaVersion
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sourceCreatedAt.hashCode()
        result = 31 * result + favoriteModifiedAt.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (thumbnailData?.contentHashCode() ?: 0)
        result = 31 * result + schemaVersion
        return result
    }
}

/**
 * 宿主 App 提供本地收藏读写，对应 iOS `CloudFavoritesStore`。
 *
 * 实现方负责把远端快照落到本地存储，冲突合并策略由同步引擎调用方决定。
 */
interface CloudFavoritesStore {
    suspend fun localFavoriteSnapshots(): List<CloudFavoriteSnapshot>

    suspend fun applyRemoteFavorite(snapshot: CloudFavoriteSnapshot)

    suspend fun applyRemoteRemoval(id: String, modifiedAt: Long)

    suspend fun clearLocalFavorites()
}

/** 远端存储抽象。Android 没有 CloudKit，由宿主注入具体实现（Firebase、自建后端等）。 */
interface CloudFavoritesRemoteStore {
    /** 检查账号可用性；返回 false 表示未登录或不可用。 */
    suspend fun isAvailable(): Boolean

    /** 拉取远端全部收藏快照。 */
    suspend fun fetchAll(): List<CloudFavoriteSnapshot>

    /** 上传或更新单条快照。 */
    suspend fun upsert(snapshot: CloudFavoriteSnapshot)

    /** 删除远端单条快照。 */
    suspend fun delete(id: String)

    /** 清空远端收藏。 */
    suspend fun clearAll()
}

/** 同步状态，对应 iOS `CloudFavoritesSyncStatus`。 */
sealed interface CloudFavoritesSyncStatus {
    data object Disabled : CloudFavoritesSyncStatus
    data object CheckingAccount : CloudFavoritesSyncStatus
    data object Syncing : CloudFavoritesSyncStatus
    data class Synced(val timestamp: Long) : CloudFavoritesSyncStatus
    data object Unavailable : CloudFavoritesSyncStatus
    data class Failed(val message: String) : CloudFavoritesSyncStatus
}

/** 状态说明文案的 key，由宿主 App 决定最终显示文本。 */
val CloudFavoritesSyncStatus.messageKey: String
    get() = when (this) {
        CloudFavoritesSyncStatus.Disabled -> "cloudsync.status.disabled"
        CloudFavoritesSyncStatus.CheckingAccount -> "cloudsync.status.checking"
        CloudFavoritesSyncStatus.Syncing -> "cloudsync.status.syncing"
        is CloudFavoritesSyncStatus.Synced -> "cloudsync.status.synced"
        CloudFavoritesSyncStatus.Unavailable -> "cloudsync.status.unavailable"
        is CloudFavoritesSyncStatus.Failed -> "cloudsync.status.failed"
    }
