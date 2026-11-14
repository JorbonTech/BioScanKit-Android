package com.bioscankit.android.settings

import android.content.Context
import org.json.JSONObject

/** `RelatedRoutes.json` 里的一条路由。 */
data class RelatedRoute(
    val trigger: String,
    val match: List<String>,
    val apps: List<RelatedToolEntry>,
    val minConfidence: Double?,
    val maxConfidence: Double?,
    val isEnabled: Boolean,
)

data class RelatedToolEntry(
    val id: String,
    val actionKey: String?,
    val detailKey: String?,
)

/** 解析完成、可直接展示的推荐工具。 */
data class RelatedTool(
    val id: String,
    val name: String,
    val action: String,
    val detail: String,
    val destinationUrl: String,
    val imageName: String?,
    val accentHex: String?,
    val backgroundHexes: List<String>,
)

data class RelatedToolsRecommendation(
    val variant: Variant,
    val confidence: Double,
    val taxon: RelatedTaxon,
    val tools: List<RelatedTool>,
) {
    enum class Variant {
        /** 识别可信：面板作为结果页的脚注，放在物种概览之后。 */
        Related,

        /** 识别不可信：面板移动到物种标题之前，增加标题与重拍动作。 */
        Caution,
    }

    val hasTools: Boolean get() = tools.isNotEmpty()
}

/**
 * 与 iOS `RelatedToolsRecommender` 对齐：按分类和置信度解析跨 App 推荐。
 *
 * 公共库只负责“推荐什么”，实际打开 URL、埋点由宿主完成。
 */
object RelatedToolsRecommender {
    /** 所有宿主共用同一条“可信 / 需谨慎”分界线。 */
    const val lowConfidenceThreshold = 0.5
    const val maxRelatedTools = 2
    const val maxCautionTools = 1

    fun recommend(
        context: Context,
        hostAppId: String,
        taxon: RelatedTaxon,
        confidence: Double,
        suppressedAppIds: Set<String> = emptySet(),
        languageIdentifier: String = "",
    ): RelatedToolsRecommendation? {
        val catalogue = RecommendedAppsLoader.loadFromAssets(
            context = context,
            excludingCurrentAppId = hostAppId,
            languageIdentifier = languageIdentifier,
        )
        return resolve(
            context = context,
            routes = routes(context)[hostAppId].orEmpty(),
            catalogue = catalogue,
            taxon = taxon,
            confidence = confidence,
            suppressedAppIds = suppressedAppIds,
        )
    }

    fun resolve(
        context: Context,
        routes: List<RelatedRoute>,
        catalogue: List<RecommendedApp>,
        taxon: RelatedTaxon,
        confidence: Double,
        suppressedAppIds: Set<String> = emptySet(),
    ): RelatedToolsRecommendation? {
        val clamped = confidence.coerceIn(0.0, 1.0)
        val variant = if (clamped < lowConfidenceThreshold) {
            RelatedToolsRecommendation.Variant.Caution
        } else {
            RelatedToolsRecommendation.Variant.Related
        }

        val appsById = catalogue.associateBy(RecommendedApp::id)
        val seen = mutableSetOf<String>()
        val resolved = mutableListOf<RelatedTool>()

        for (route in routes) {
            if (!route.isEnabled) continue
            if (route.match.none { it.equals(taxon.rawValue, ignoreCase = true) }) continue
            if (route.maxConfidence != null && clamped >= route.maxConfidence) continue
            if (route.minConfidence != null && clamped < route.minConfidence) continue

            for (entry in route.apps) {
                if (!seen.add(entry.id)) continue
                if (entry.id in suppressedAppIds) continue
                val app = appsById[entry.id] ?: continue
                resolved += RelatedTool(
                    id = app.id,
                    name = app.title,
                    action = localizedCopy(context, entry.actionKey) ?: app.title,
                    detail = localizedCopy(context, entry.detailKey) ?: app.description,
                    destinationUrl = app.destinationUrl,
                    imageName = app.imageName,
                    accentHex = null,
                    backgroundHexes = emptyList(),
                )
            }
        }

        val limit = if (variant == RelatedToolsRecommendation.Variant.Caution) {
            maxCautionTools
        } else {
            maxRelatedTools
        }
        val tools = resolved.take(limit)
        if (tools.isEmpty() && variant != RelatedToolsRecommendation.Variant.Caution) return null
        return RelatedToolsRecommendation(
            variant = variant,
            confidence = clamped,
            taxon = taxon,
            tools = tools,
        )
    }

    /** 读取 `bioscan_shared/RelatedRoutes.json`；路由与语言无关，只有文案本地化。 */
    fun routes(context: Context): Map<String, List<RelatedRoute>> = runCatching {
        val json = context.assets
            .open("$ASSET_DIRECTORY/RelatedRoutes.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(json)
        val routes = root.optJSONObject("routes") ?: return@runCatching emptyMap()
        buildMap {
            routes.keys().forEach { hostId ->
                val list = routes.optJSONArray(hostId) ?: return@forEach
                put(hostId, List(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    RelatedRoute(
                        trigger = item.optString("trigger", "taxon"),
                        match = item.optJSONArray("match").toStringList(),
                        apps = item.optJSONArray("apps").toToolEntries(),
                        minConfidence = item.optNullableDouble("min_confidence"),
                        maxConfidence = item.optNullableDouble("max_confidence"),
                        isEnabled = item.optBoolean("is_enabled", true),
                    )
                })
            }
        }
    }.getOrDefault(emptyMap())

    private fun localizedCopy(context: Context, key: String?, fallback: String? = null): String? {
        if (key.isNullOrBlank()) return fallback
        val resourceName = key.replace('.', '_')
        val id = context.resources.getIdentifier(resourceName, "string", context.packageName)
        if (id == 0) return fallback
        return context.getString(id)
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            optString(index)
        }.filter(String::isNotBlank)
    }

    private fun org.json.JSONArray?.toToolEntries(): List<RelatedToolEntry> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            val item = getJSONObject(index)
            RelatedToolEntry(
                id = item.optString("id"),
                actionKey = item.optString("action_key").takeIf(String::isNotBlank),
                detailKey = item.optString("detail_key").takeIf(String::isNotBlank),
            )
        }.filter { it.id.isNotBlank() }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private const val ASSET_DIRECTORY = "bioscan_shared"
}
