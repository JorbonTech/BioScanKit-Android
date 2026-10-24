package com.bioscankit.android.analytics

import android.content.Context
import android.os.Build
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.android.replay.PostHogSessionReplayConfig
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android counterpart of BioScanKit-iOS's `BioScanAnalytics`.
 *
 * Keeps canonical event names, paywall funnel context, protected properties and
 * opt-out behaviour in one place. Apps should call this object instead of the
 * PostHog SDK directly.
 */
object BioScanAnalytics {
    private const val PREFERENCES_NAME = "bioscan.analytics"
    private const val FIRST_LAUNCH_KEY = "bioscan.analytics.first_launch_recorded"

    private val lock = Any()
    private val configured = AtomicBoolean(false)
    private val trackingEnabled = AtomicBoolean(true)

    private var disabled = true
    private var paywallSessionId: String? = null
    private var purchaseAttemptId: String? = null
    private var paywallContext: Map<String, Any?> = emptyMap()
    private var paywallCompletedSuccessfully = false

    fun configure(context: Context, configuration: AnalyticsConfiguration) {
        synchronized(lock) {
            if (configured.get()) return
            configureInternal(context, configuration)
        }
    }

    private fun configureInternal(context: Context, configuration: AnalyticsConfiguration) {
        if (configuration.projectToken.isBlank() ||
            configuration.host.isBlank() ||
            configuration.appId.isBlank()
        ) return

        val appContext = context.applicationContext
        disabled = configuration.debug || isEmulator()
        val protectedProperties = protectedProperties(appContext, configuration)
        trackingEnabled.set(
            !disabled && appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean("tracking_enabled", true),
        )

        val postHogConfig = PostHogAndroidConfig(
            projectToken = configuration.projectToken,
            host = configuration.host,
        ).apply {
            debug = configuration.debug
            captureApplicationLifecycleEvents = !disabled
            captureScreenViews = !disabled && configuration.capturesAutomaticScreenViews
            captureDeepLinks = false
            captureElementInteractions = false
            captureRageClicks = false
            captureDeadClicks = false
            capturePushNotificationSubscriptions = false
            capturePushNotificationOpened = false
            sessionReplayConfig = PostHogSessionReplayConfig().apply {
                screenshot = configuration.sessionReplayEnabled && !disabled
            }
        }

        PostHogAndroid.setup(appContext, postHogConfig)
        configured.set(true)
        if (!trackingEnabled.get()) PostHog.optOut()
        protectedProperties.forEach { (key, value) -> PostHog.register(key, value) }
    }

    fun trackFirstLaunch(context: Context) {
        if (!isEnabled()) return
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(FIRST_LAUNCH_KEY, false)) return
        preferences.edit().putBoolean(FIRST_LAUNCH_KEY, true).apply()
        track("first_launch")
    }

    fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        if (!isEnabled() || eventName.isBlank()) return

        val canonicalName = canonicalEventName(eventName)
        val normalized = normalizeProperties(canonicalName, properties)
        if (normalized == null) return

        PostHog.capture(canonicalName, properties = normalized)

        val remainingScans = (normalized["remaining_scans"] as? Number)?.toInt()
        val hasQuotaBlock = normalized["reason"] == "no_credits" ||
            normalized["error_type"] == "quota" ||
            normalized["error_type"] == "no_credits"
        val quotaExhausted = hasQuotaBlock && (remainingScans == null || remainingScans == 0)

        when (canonicalName) {
            "recognition_started", "sound_identify_start" -> PostHog.capture("core_action_started", properties = normalized)
            "recognition_succeeded", "sound_identify_success" -> PostHog.capture("core_action_completed", properties = normalized)
            "recognition_blocked" -> if (quotaExhausted) {
                PostHog.capture("quota_exhausted", properties = normalized)
            }
        }
    }

    fun screen(name: String, properties: Map<String, Any?> = emptyMap()) {
        if (!isEnabled() || name.isBlank()) return
        PostHog.screen(name, properties = properties.withoutNullValues())
    }

    fun identify(userId: String, properties: Map<String, Any?> = emptyMap()) {
        if (!isEnabled() || userId.isBlank()) return
        PostHog.identify(userId, userProperties = properties.withoutNullValues())
    }

    fun reset() {
        if (configured.get() && !disabled) PostHog.reset()
    }

    fun setTrackingEnabled(context: Context, enabled: Boolean) {
        trackingEnabled.set(enabled && !disabled)
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("tracking_enabled", enabled).apply()
        if (enabled && !disabled) {
            PostHog.optIn()
        } else {
            PostHog.optOut()
        }
    }

    fun flush() {
        if (configured.get() && !disabled) PostHog.flush()
    }

    private fun isEnabled(): Boolean = configured.get() && !disabled && trackingEnabled.get()

    private fun normalizeProperties(
        canonicalName: String,
        properties: Map<String, Any?>,
    ): Map<String, Any>? {
        val normalized = properties.withoutNullValues().toMutableMap()
        val isPaywallEvent = (canonicalName.startsWith("paywall_") && canonicalName != "paywall_requested") ||
            canonicalName == "product_selected" ||
            canonicalName.startsWith("purchase_") ||
            canonicalName.startsWith("restore_")
        if (isPaywallEvent && normalized["product_id"] == null && normalized["product"] != null) {
            normalized["product_id"] = normalized["product"] as Any
        }
        if (canonicalName in setOf(
                "recognition_started", "recognition_succeeded", "recognition_failed", "recognition_blocked",
            ) && normalized["feature"] == null
        ) {
            normalized["feature"] = normalized["entity_type"]
                ?: normalized["recognition_mode"]
                ?: "identification"
        }


        synchronized(lock) {
            when (canonicalName) {
                "paywall_viewed" -> {
                    paywallSessionId = UUID.randomUUID().toString().lowercase(Locale.US)
                    purchaseAttemptId = null
                    paywallCompletedSuccessfully = false
                    paywallContext = normalized.filterKeys {
                        it in setOf(
                            "paywall_id", "variant", "placement", "trigger", "source",
                            "paywall_source", "remaining_scans", "reason",
                        )
                    }
                }

                "purchase_started" -> {
                    if (paywallSessionId == null) {
                        paywallSessionId = UUID.randomUUID().toString().lowercase(Locale.US)
                    }
                    purchaseAttemptId = UUID.randomUUID().toString().lowercase(Locale.US)
                }

                "purchase_success" -> {
                    paywallCompletedSuccessfully = true
                    if (purchaseAttemptId == null) return null
                }
            }

            if (isPaywallEvent) {
                paywallContext.forEach { (key, value) ->
                    if (normalized[key] == null && value != null) normalized[key] = value
                }
                val source = (normalized["paywall_source"] as? String)
                    ?: (normalized["source"] as? String)
                normalized["paywall_source"] = source?.let {
                    normalizedPaywallSource(it, normalized)
                } ?: "other"
                paywallSessionId?.let { normalized["paywall_session_id"] = it }
                purchaseAttemptId?.let { normalized["purchase_attempt_id"] = it }
                if (canonicalName == "paywall_dismissed") {
                    normalized["did_convert"] = paywallCompletedSuccessfully
                }
            }

            if (canonicalName in setOf(
                    "paywall_dismissed",
                    "purchase_success",
                    "purchase_cancelled",
                    "purchase_failed",
                ) &&
                (canonicalName != "purchase_success" || purchaseAttemptId != null)
            ) {
                purchaseAttemptId = null
            }

            if (canonicalName == "paywall_dismissed") {
                paywallSessionId = null
                paywallContext = emptyMap()
                paywallCompletedSuccessfully = false
            }
        }

        return normalized
    }

    private fun protectedProperties(
        context: Context,
        configuration: AnalyticsConfiguration,
    ): Map<String, Any> {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 0L
        }
        return mapOf(
            "app_id" to configuration.appId,
            "app_name" to configuration.appName,
            "package_name" to context.packageName,
            "app_version" to versionName,
            "build_number" to versionCode,
            "environment" to if (disabled) "debug" else configuration.environment,
            "device_locale" to Locale.getDefault().toLanguageTag(),
            "platform" to "android",
        )
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
}
