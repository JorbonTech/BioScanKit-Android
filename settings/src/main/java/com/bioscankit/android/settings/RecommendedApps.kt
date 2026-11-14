package com.bioscankit.android.settings

import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import com.bioscankit.android.design.BioScanDesign
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Immutable
data class RecommendedApp(
    val id: String,
    val title: String,
    val description: String,
    val destinationUrl: String,
    val imageName: String?,
    val backgroundColors: List<Color>,
    val fallbackMessage: String?,
)

object RecommendedAppsLoader {
    /**
     * Decodes the shared AppRecommendations document without assuming that an
     * iOS App Store URL is valid on Android. Hosts select their platform key.
     */
    fun load(
        json: String,
        excludingCurrentAppId: String,
        destinationUrlKey: String,
    ): List<RecommendedApp> = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        root["apps"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val visible = item["isVisible"]?.jsonPrimitive?.booleanOrNull ?: true
            if (!visible || id.equals(excludingCurrentAppId, ignoreCase = true)) return@mapNotNull null
            val destination = item[destinationUrlKey]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            RecommendedApp(
                id = id,
                title = item["title"]?.jsonPrimitive?.contentOrNull ?: id,
                description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                destinationUrl = destination,
                imageName = item["imageName"]?.jsonPrimitive?.contentOrNull,
                backgroundColors = item["backgroundColors"]?.jsonArray.orEmpty().mapNotNull {
                    parseHexColor(it.jsonPrimitive.contentOrNull)
                },
                fallbackMessage = item["fallbackMessage"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }.getOrDefault(emptyList())

    /**
     * 从库内共享 assets 读取推荐应用，对应 iOS 的
     * `RecommendedAppsLoader.load(bundle:)`。
     *
     * 清单与图标由 BioScanKit-iOS/Shared 在构建时同步进来，双端共用同一份数据；
     * 语言文件缺失时回退英文清单。
     */
    fun loadFromAssets(
        context: Context,
        excludingCurrentAppId: String,
        destinationUrlKey: String = "googlePlayURL",
        languageIdentifier: String = "",
    ): List<RecommendedApp> {
        val json = readSharedAsset(context, localizedAssetName(languageIdentifier))
            ?: readSharedAsset(context, "RecommendedApps.json")
            ?: return emptyList()
        return load(
            json = json,
            excludingCurrentAppId = excludingCurrentAppId,
            destinationUrlKey = destinationUrlKey,
        )
    }

    /** 返回推荐应用图标的 assets 路径，供 Image/painter 直接读取。 */
    fun iconAssetPath(app: RecommendedApp): String? =
        app.imageName?.takeIf(String::isNotBlank)?.let { "$ASSET_DIRECTORY/Icons/$it.png" }

    private fun localizedAssetName(languageIdentifier: String): String =
        when (languageIdentifier.substringBefore('-')) {
            "zh" -> "RecommendedApps_zh-Hans.json"
            "de" -> "RecommendedApps_de.json"
            "es" -> "RecommendedApps_es.json"
            "fr" -> "RecommendedApps_fr.json"
            "it" -> "RecommendedApps_it.json"
            "ja" -> "RecommendedApps_ja.json"
            "nl" -> "RecommendedApps_nl.json"
            else -> "RecommendedApps.json"
        }

    private fun readSharedAsset(context: Context, name: String): String? = runCatching {
        context.assets.open("$ASSET_DIRECTORY/$name").bufferedReader().use { it.readText() }
    }.getOrNull()

    private const val ASSET_DIRECTORY = "bioscan_shared"

    private fun parseHexColor(value: String?): Color? {
        val hex = value?.removePrefix("#") ?: return null
        if (hex.length != 6) return null
        return hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
    }
}

/** 未提供图标 composable 时的占位图标（首字母圆角方块）。 */
@Composable
fun RecommendedAppIconFallback(app: RecommendedApp) {
    val colors = BioScanDesign.colors
    Box(
        modifier = Modifier
            // 与 iOS 一致：78dp 容器内 74dp 图标。
            .size(74.dp)
            .background(colors.accent.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = app.title.take(1).uppercase(),
            color = colors.accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
        )
    }
}

/**
 * 推荐应用海报卡，与 iOS `SettingsRecommendedAppCardView` 逐项对齐：
 *
 * | 项 | iOS | 这里 |
 * | --- | --- | --- |
 * | 圆角 | 24 | 24.dp |
 * | 图标容器 | 78×78，圆角 20 | 78.dp，20.dp |
 * | 最小高度 | 168 | 168.dp |
 * | 标题 | 31 black | 31.sp Black |
 * | 眉标 | RECOMMENDED 11 black，字距 1.1 | 同 |
 * | 副标题 | 15 bold，白色 84% | 同 |
 * | 装饰 | 右下 180×34 白 12%，-18° | 同 |
 * | 描边 | 白色 20%，1pt | 同 |
 */
@Composable
fun SettingsRecommendedAppCard(
    app: RecommendedApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit,
) {
    val colors = BioScanDesign.colors
    val gradient = app.backgroundColors.ifEmpty {
        listOf(colors.accent, colors.success)
    }
    val primaryAccent = gradient.first()

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = primaryAccent.copy(alpha = 0.22f),
                spotColor = primaryAccent.copy(alpha = 0.22f),
            ),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .background(
                    Brush.linearGradient(gradient),
                    RoundedCornerShape(24.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(24.dp),
                ),
        ) {
            // iOS 用 31pt + lineLimit(2) + minimumScaleFactor；窄屏（<360dp）下
            // 31sp 会在单词中间断行，这里按可用宽度降一档，仍保留两行。
            val compact = maxWidth < 360.dp
            val titleSize = if (compact) 24.sp else 31.sp
            val titleLineHeight = if (compact) 28.sp else 34.sp
            // 右下角装饰斜条，对应 iOS `.overlay(alignment: .bottomTrailing)`
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 26.dp, y = (-8).dp)
                    .size(width = 180.dp, height = 34.dp)
                    .rotate(-18f)
                    .background(Color.White.copy(alpha = 0.12f)),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 168.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .shadow(
                                elevation = 14.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = primaryAccent.copy(alpha = 0.30f),
                                spotColor = primaryAccent.copy(alpha = 0.30f),
                            )
                            .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                        content = icon,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bioscan_settings_recommended),
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                        Text(
                            text = app.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = titleSize,
                                lineHeight = titleLineHeight,
                                fontWeight = FontWeight.Black,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.92f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Text(
                    text = app.description,
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
