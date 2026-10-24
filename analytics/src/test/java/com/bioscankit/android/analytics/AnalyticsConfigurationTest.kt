package com.bioscankit.android.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 Android 与 iOS 的 PostHog 事件可区分约定。
 *
 * iOS 已上架多时，Android 必须向 iOS 的取值对齐：
 * - `app_id` 表示"哪个 App"，两端完全一致（不带平台后缀）。
 * - `app_name` 用 iOS 的短名（如 "MrRock"），不是 Android 显示名（"Mr.Rock"）。
 * - `platform` 由公共库写入，业务参数不可覆盖。
 */
class AnalyticsConfigurationTest {
    @Test
    fun appIdMatchesIosAndHasNoPlatformSuffix() {
        val expectations = mapOf(
            "mr_rock" to "MrRock",
            "inature" to "iNature",
            "nature_ear" to "NatureEar",
        )
        expectations.forEach { (appId, appName) ->
            val configuration = AnalyticsConfiguration.postHogDefault(
                appId = appId,
                appName = appName,
            )
            assertEquals(appId, configuration.appId)
            assertFalse(
                "app_id 不应带平台后缀，平台由 platform 属性表达",
                configuration.appId.endsWith("_android"),
            )
            assertEquals(appName, configuration.appName)
        }
    }

    @Test
    fun environmentDefaultsToStoreForReleaseBuilds() {
        val release = AnalyticsConfiguration.postHogDefault(appId = "mr_rock", appName = "MrRock")
        assertEquals("app_store", release.environment)

        val debug = AnalyticsConfiguration.postHogDefault(
            appId = "mr_rock",
            appName = "MrRock",
            debug = true,
        )
        assertEquals("debug", debug.environment)
        assertTrue(debug.debug)
    }

    @Test
    fun screenViewCaptureIsOptIn() {
        val configuration = AnalyticsConfiguration.postHogDefault(
            appId = "inature",
            appName = "iNature",
        )
        assertFalse(
            "与 iOS 一致：自动 screen view 默认关闭，由业务显式上报",
            configuration.capturesAutomaticScreenViews,
        )
    }
}
