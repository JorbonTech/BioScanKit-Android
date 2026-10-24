package com.bioscankit.android.analytics

data class AnalyticsConfiguration(
    val projectToken: String,
    val host: String,
    val appId: String,
    val appName: String,
    val environment: String,
    val debug: Boolean = false,
    val capturesAutomaticScreenViews: Boolean = false,
    val sessionReplayEnabled: Boolean = false,
) {
    companion object {
        fun postHogDefault(
            appId: String,
            appName: String,
            debug: Boolean = false,
            capturesAutomaticScreenViews: Boolean = false,
            sessionReplayEnabled: Boolean = false,
        ) = AnalyticsConfiguration(
            projectToken = "phc_y4fqG9tbNmvjrbUvXRQuigkMuiaqWkFothJJM7MWFPAz",
            host = "https://us.i.posthog.com",
            appId = appId,
            appName = appName,
            environment = if (debug) "debug" else "app_store",
            debug = debug,
            capturesAutomaticScreenViews = capturesAutomaticScreenViews,
            sessionReplayEnabled = sessionReplayEnabled,
        )
    }
}
