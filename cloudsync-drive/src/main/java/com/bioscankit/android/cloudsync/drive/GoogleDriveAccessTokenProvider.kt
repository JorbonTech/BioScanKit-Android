package com.bioscankit.android.cloudsync.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Drive 需要的 OAuth 作用域：只能看到本 App 自己的 App Data 目录。 */
const val GOOGLE_DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/**
 * 提供 Drive REST 调用所需的 OAuth access token。
 *
 * 抽成接口是为了让同步逻辑可测试，也方便以后换成自建 OAuth（PKCE）实现。
 */
interface GoogleDriveAccessTokenProvider {
    /** 返回可用 token；null 表示当前没有已登录账号或需要用户重新授权。 */
    suspend fun accessToken(forceRefresh: Boolean = false): String?

    /** 已登录账号邮箱，未登录返回 null（设置页展示用）。 */
    fun signedInAccount(): String?

    fun signOut()
}

/**
 * 基于 Google Play 服务登录的 token 提供者。
 *
 * 选它而不是自建 PKCE 流程的原因：token 刷新由 Play 服务托管，后台同步不需要拉起浏览器。
 * 代价是设备必须有 Google Play 服务；没有 GMS 的设备会返回 null，调用方按“不可用”处理。
 */
class PlayServicesGoogleDriveAuth(
    private val context: Context,
    /**
     * 可选：Web OAuth Client ID。
     *
     * Drive 的 access token 走 `GoogleAuthUtil.getToken`，只需要在 Google Cloud 里
     * 注册 **Android** OAuth 客户端（包名 + SHA-1），不依赖这个值。
     * 留空即不请求 ID Token，功能不受影响；将来接后端校验账号时再填。
     */
    private val webClientId: String = "",
) : GoogleDriveAccessTokenProvider {

    private val scope = Scope(GOOGLE_DRIVE_APPDATA_SCOPE)

    private val options: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .apply { if (webClientId.isNotBlank()) requestIdToken(webClientId) }
            .requestScopes(scope)
            .build()

    fun signInClient(): GoogleSignInClient = GoogleSignIn.getClient(context, options)

    /** 拉起账号选择界面；结果需要交给 [completeSignIn]。 */
    fun signInIntent(): Intent = signInClient().signInIntent

    /**
     * 处理 [signInIntent] 的结果。
     *
     * @return true 表示已登录；false 表示用户取消或授权失败。
     */
    suspend fun completeSignIn(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnCompleteListener { task ->
                    continuation.resume(task.isSuccessful && task.result != null)
                }
        }
    }

    override fun signedInAccount(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    override fun signOut() {
        signInClient().signOut()
    }

    override suspend fun accessToken(forceRefresh: Boolean): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: silentSignIn()?.account
            ?: return@withContext null
        val scopeString = "oauth2:$GOOGLE_DRIVE_APPDATA_SCOPE"
        runCatching {
            val existing = GoogleAuthUtil.getToken(context, account, scopeString)
            if (forceRefresh) {
                GoogleAuthUtil.clearToken(context, existing)
                GoogleAuthUtil.getToken(context, account, scopeString)
            } else {
                existing
            }
        }.getOrNull()
    }

    private suspend fun silentSignIn(): GoogleSignInAccount? =
        suspendCancellableCoroutine { continuation ->
            signInClient().silentSignIn()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        val code = (task.exception as? ApiException)?.statusCode
                        // 需要用户交互（没有已授权账号 / 需要重新授权）时按未登录处理，
                        // 由设置页引导用户点“连接 Google Drive”。
                        if (code == GoogleSignInStatusCodes.SIGN_IN_REQUIRED) {
                            continuation.resume(null)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
        }
}
