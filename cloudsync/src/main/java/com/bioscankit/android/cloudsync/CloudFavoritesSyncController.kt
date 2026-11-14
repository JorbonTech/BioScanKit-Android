package com.bioscankit.android.cloudsync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 收藏云同步控制器，对应 iOS `CloudFavoritesSyncController`。
 *
 * 同步策略与 iOS 保持一致：以 `favoriteModifiedAt` 较新的一方为准，
 * `isFavorite = false` 的快照视为删除。
 */
class CloudFavoritesSyncController(
    private val localStore: CloudFavoritesStore,
    private val remoteStore: CloudFavoritesRemoteStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _status = MutableStateFlow<CloudFavoritesSyncStatus>(CloudFavoritesSyncStatus.Disabled)
    val status: StateFlow<CloudFavoritesSyncStatus> = _status.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val mutex = Mutex()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (enabled) {
            sync()
        } else {
            _status.value = CloudFavoritesSyncStatus.Disabled
        }
    }

    fun retry() = sync()

    /** 本地收藏发生变化时推送到远端。 */
    fun favoriteChanged(snapshot: CloudFavoriteSnapshot) {
        if (!_enabled.value) return
        scope.launch { push(snapshot) }
    }

    /** 只清本地收藏（不改变同步开关）。 */
    fun clearLocalFavoritesOnly(onComplete: (() -> Unit)? = null) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { localStore.clearLocalFavorites() } }
                .onFailure {
                    _status.value = CloudFavoritesSyncStatus.Failed(it.message.orEmpty())
                }
            onComplete?.invoke()
        }
    }

    /** 与 iOS 一致：关闭同步后清空本地收藏，避免下次同步又拉回来。 */
    fun clearFavoritesOnThisDevice(onComplete: (() -> Unit)? = null) {
        setEnabled(false)
        clearLocalFavoritesOnly(onComplete)
    }

    /** 清空云端收藏（本地收藏由调用方决定是否一并清掉）。 */
    fun deleteFavoritesFromCloud(onComplete: (() -> Unit)? = null) {
        scope.launch {
            runCatching { remoteStore.clearAll() }
                .onSuccess {
                    _status.value = CloudFavoritesSyncStatus.Synced(System.currentTimeMillis())
                    onComplete?.invoke()
                }
                .onFailure { _status.value = CloudFavoritesSyncStatus.Failed(it.message.orEmpty()) }
        }
    }

    fun sync() {
        if (!_enabled.value) {
            _status.value = CloudFavoritesSyncStatus.Disabled
            return
        }
        scope.launch {
            mutex.withLock {
                // 排队期间用户可能已经关掉同步：不要再把状态改成 Unavailable/Synced。
                if (!_enabled.value) {
                    _status.value = CloudFavoritesSyncStatus.Disabled
                    return@withLock
                }
                _status.value = CloudFavoritesSyncStatus.CheckingAccount
                if (!runCatching { remoteStore.isAvailable() }.getOrDefault(false)) {
                    _status.value = if (_enabled.value) {
                        CloudFavoritesSyncStatus.Unavailable
                    } else {
                        CloudFavoritesSyncStatus.Disabled
                    }
                    return@withLock
                }
                _status.value = CloudFavoritesSyncStatus.Syncing
                runCatching { merge() }
                    .onSuccess { _status.value = CloudFavoritesSyncStatus.Synced(System.currentTimeMillis()) }
                    .onFailure {
                        _status.value = CloudFavoritesSyncStatus.Failed(it.message ?: "Sync failed.")
                    }
            }
        }
    }

    /**
     * 双向合并：远端较新则落地到本地，本地较新则推送到远端。
     */
    private suspend fun merge() = withContext(Dispatchers.IO) {
        val local = localStore.localFavoriteSnapshots().associateBy { it.id }
        val remote = remoteStore.fetchAll().associateBy { it.id }

        (local.keys + remote.keys).forEach { id ->
            val localSnapshot = local[id]
            val remoteSnapshot = remote[id]
            when {
                remoteSnapshot == null && localSnapshot != null -> remoteStore.upsert(localSnapshot)
                localSnapshot == null && remoteSnapshot != null -> applyRemote(remoteSnapshot)
                localSnapshot != null && remoteSnapshot != null -> {
                    when {
                        remoteSnapshot.favoriteModifiedAt > localSnapshot.favoriteModifiedAt ->
                            applyRemote(remoteSnapshot)
                        localSnapshot.favoriteModifiedAt > remoteSnapshot.favoriteModifiedAt ->
                            remoteStore.upsert(localSnapshot)
                    }
                }
            }
        }
    }

    private suspend fun applyRemote(snapshot: CloudFavoriteSnapshot) {
        if (snapshot.isFavorite) {
            localStore.applyRemoteFavorite(snapshot)
        } else {
            localStore.applyRemoteRemoval(snapshot.id, snapshot.favoriteModifiedAt)
        }
    }

    private suspend fun push(snapshot: CloudFavoriteSnapshot) {
        mutex.withLock {
            if (!runCatching { remoteStore.isAvailable() }.getOrDefault(false)) {
                _status.value = CloudFavoritesSyncStatus.Unavailable
                return@withLock
            }
            runCatching { remoteStore.upsert(snapshot) }
                .onSuccess { _status.value = CloudFavoritesSyncStatus.Synced(System.currentTimeMillis()) }
                .onFailure { _status.value = CloudFavoritesSyncStatus.Failed(it.message ?: "Sync failed.") }
        }
    }
}
