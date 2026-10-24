package com.bioscankit.android.analytics

internal fun canonicalEventName(name: String): String = when (name) {
    "paywall_show", "paywall_shown" -> "paywall_viewed"
    "paywall_product_selected", "paywall_product_select" -> "product_selected"
    "purchase_succeeded", "purchase_success" -> "purchase_success"
    "restore_purchases_started" -> "restore_started"
    "restore_succeeded", "restore_purchases_succeeded", "restore_success" -> "restore_success"
    "restore_purchases_failed" -> "restore_failed"
    "restore_purchases_nothing_found" -> "restore_nothing_found"
    else -> name
}

internal fun normalizedPaywallSource(
    source: String,
    properties: Map<String, Any?>,
): String {
    val remainingScans = (properties["remaining_scans"] as? Number)?.toInt()
    val hasQuotaReason = listOf("no_credits", "quota").contains(properties["reason"] as? String) ||
        listOf("no_credits", "quota").contains(properties["trigger"] as? String)
    val isQuotaEntry = remainingScans?.let { it == 0 } ?: hasQuotaReason
    return when (source) {
        "quota_exhausted" -> if (isQuotaEntry) "quota_exhausted" else "scanner"
        "scanner" -> if (isQuotaEntry) "quota_exhausted" else "scanner"
        "recognition_gate", "recognition_quota", "home_import_quota",
        "home_quota_label", "home_quota_alert", "no_credits", "quota" ->
            if (isQuotaEntry) "quota_exhausted" else "scanner"
        "post_onboarding", "onboarding_complete" -> "onboarding"
        "profile", "membership", "upgrade" -> "premium_feature"
        "home_cta", "home_remaining_scans" -> "home"
        "guide_demo_result" -> "result"
        "purchase_page", "guide_demo" -> "premium_feature"
        "onboarding", "home", "result", "settings", "premium_feature",
        "history", "favorites", "other" -> source
        else -> "other"
    }
}

internal fun Map<String, Any?>.withoutNullValues(): Map<String, Any> =
    entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
