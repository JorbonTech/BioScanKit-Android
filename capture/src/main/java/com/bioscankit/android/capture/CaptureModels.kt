package com.bioscankit.android.capture

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bioscankit.android.design.AdaptiveColor
import com.bioscankit.android.design.BioScanTheme

enum class CameraFinderStyle { CornerMarkers, Scanner }
enum class CameraPageLayoutStyle { INatureLegacy, Adaptive }
enum class CropBorderAnimationStyle { None, Pulse }
enum class CropEditorLayoutStyle { INatureLegacy, Adaptive }
enum class ProcessingScreenLayoutStyle { INatureLegacy, Adaptive }

@Immutable
data class CameraScreenConfiguration(
    val theme: BioScanTheme = BioScanTheme.Neutral,
    /** 传 null 时使用组件内置文案 `R.string.bioscan_capture_title`。 */
    val title: String? = null,
    val instruction: String? = null,
    val permissionTitle: String? = null,
    val permissionMessage: String? = null,
    val deniedMessage: String? = null,
    val supportsPhotoLibrary: Boolean = true,
    val supportsFlash: Boolean = true,
    val supportsTapToFocus: Boolean = true,
    val supportsPinchToZoom: Boolean = true,
    /** 状态胶囊文案；传 null 时不显示。默认值来源 `R.string.bioscan_capture_status_on_device`。 */
    val statusText: String? = null,
    /** 状态胶囊图标名（沿用 iOS SF Symbol 名），对应 iOS `statusSystemImage`。 */
    val statusSystemImage: String = "cpu",
    /** 状态胶囊文字与图标颜色，对应 iOS `statusForegroundColor`。 */
    val statusForegroundColor: AdaptiveColor = AdaptiveColor(
        light = Color.White.copy(alpha = 0.94f),
        dark = Color.White.copy(alpha = 0.94f),
    ),
    val processingInstruction: String? = null,
    val finderCornerRadius: Dp = 24.dp,
    val finderMaximumSize: Dp = 300.dp,
    val finderStyle: CameraFinderStyle = CameraFinderStyle.CornerMarkers,
    val finderColor: Color = theme.accent,
    val layoutStyle: CameraPageLayoutStyle = CameraPageLayoutStyle.INatureLegacy,
    val maximumZoomFactor: Float = 6f,
    // 无障碍文案默认取组件资源；宿主如需覆盖再显式传值。
    val closeContentDescription: String? = null,
    val helpContentDescription: String? = null,
    val choosePhotoContentDescription: String? = null,
    val captureContentDescription: String? = null,
    val flashContentDescription: String? = null,
    /** 闪光灯已开启时的无障碍文案，对应 iOS `flashOnAccessibilityLabel`（表示“关闭闪光灯”）。 */
    val flashOnContentDescription: String? = null,
    /** 闪光灯已关闭时的无障碍文案，对应 iOS `flashOffAccessibilityLabel`（表示“打开闪光灯”）。 */
    val flashOffContentDescription: String? = null,
) {
    companion object {
        val INature = CameraScreenConfiguration()
    }
}

@Immutable
data class CropEditorConfiguration(
    val theme: BioScanTheme = BioScanTheme.Neutral,
    val aspectRatio: Float = 1f,
    val cropScale: Float = 0.78f,
    val cropMaximumSize: Dp? = null,
    val cornerRadius: Dp = 14.dp,
    val showsGrid: Boolean = true,
    val showsPreview: Boolean = true,
    val allowsReset: Boolean = true,
    val guidanceText: String? = null,
    val confirmTitle: String? = null,
    val animationStyle: CropBorderAnimationStyle = CropBorderAnimationStyle.Pulse,
    val layoutStyle: CropEditorLayoutStyle = CropEditorLayoutStyle.INatureLegacy,
    /**
     * iNatureLegacy 风格下裁剪框使用的强调色，对应 iOS `legacyAccent`。
     * 默认取主题强调色。
     */
    val legacyAccent: AdaptiveColor = AdaptiveColor(theme.accent, theme.accent),
    /** 是否隐藏状态栏，对应 iOS `hidesStatusBar`。 */
    val hidesStatusBar: Boolean = false,
) {
    companion object {
        val INature = CropEditorConfiguration()
    }
}

@Immutable
data class ProcessingScreenConfiguration(
    val theme: BioScanTheme = BioScanTheme.Neutral,
    /** adaptive 布局的标题，对应 iOS `ProcessingScreenConfiguration.title`；null 时取组件资源。 */
    val title: String? = null,
    /** adaptive 布局的副标题，对应 iOS `ProcessingScreenConfiguration.subtitle`；null 时取组件资源。 */
    val subtitle: String? = null,
    val eyebrow: String? = null,
    /** 传 null 时使用组件内置的 5 条状态文案（见 `bioscan_capture_processing_*`）。 */
    val statuses: List<String>? = null,
    val waitingText: String? = null,
    val progressTargets: List<Float> = listOf(0.18f, 0.42f, 0.67f, 0.86f, 0.96f),
    val layoutStyle: ProcessingScreenLayoutStyle = ProcessingScreenLayoutStyle.INatureLegacy,
) {
    init {
        require(statuses == null || statuses.isNotEmpty()) { "Processing statuses cannot be empty." }
        require(progressTargets.size == (statuses?.size ?: DEFAULT_STATUS_COUNT)) {
            "Each processing status must have one progress target."
        }
        require(progressTargets.all { it in 0f..0.99f }) {
            "Progress targets must stay below 1.0 until recognition actually completes."
        }
    }

    companion object {
        /** 内置状态文案条数，与 `progressTargets` 默认值一一对应。 */
        const val DEFAULT_STATUS_COUNT = 5

        val INature = ProcessingScreenConfiguration()
    }
}

/**
 * 一次已被接受的拍摄/导入结果。
 *
 * [imagePath] 指向已落盘、方向已归一的图片；[context] 携带来源等识别上下文。
 */
@Immutable
data class CapturedPhoto(
    val imagePath: String,
    val source: PhotoSource,
    val context: RecognitionContext = RecognitionContext(source = source),
)

enum class PhotoSource { Camera, PhotoLibrary, Demo }

@Immutable
data class RecognitionContext(
    val source: PhotoSource,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * 识别额度状态，对齐 iOS `RecognitionAccess`。
 *
 * 旧版是 `Allowed / RequiresPurchase / Denied`，与 iOS 的
 * `unlimited / credits(Int) / unavailable` 语义不完全对应，这里改为后者。
 */
sealed interface RecognitionAccess {
    data object Unlimited : RecognitionAccess
    data class Credits(val remaining: Int) : RecognitionAccess
    data object Unavailable : RecognitionAccess

    /** 是否允许直接开始识别。 */
    val canRecognize: Boolean
        get() = when (this) {
            Unlimited -> true
            is Credits -> remaining > 0
            Unavailable -> false
        }
}

/**
 * 识别流程阶段，对齐 iOS `PhotoRecognitionPhase`。
 *
 * iOS 的 `.camera` 与 `.idle` 同一含义，这里保留 `Camera` 命名以方便对照。
 */
sealed interface PhotoRecognitionPhase<out Result> {
    data object Camera : PhotoRecognitionPhase<Nothing>
    data class Cropping(val photo: CapturedPhoto) : PhotoRecognitionPhase<Nothing>
    data class Recognizing(val photo: CapturedPhoto) : PhotoRecognitionPhase<Nothing>
    data class Failed(
        val message: String,
        val retryPhoto: CapturedPhoto? = null,
    ) : PhotoRecognitionPhase<Nothing>
    data class Completed<Result>(val result: Result) : PhotoRecognitionPhase<Result>
}

/**
 * 识别前的照片校验（App 特有策略，例如模糊检测）。
 *
 * iOS 基准流程没有这一步，MrRock 需要它；做成策略后组件主干保持不变，
 * 未提供时直接放行。
 */
fun interface PhotoRecognitionValidator {
    /** 返回 null 表示通过；返回文案表示拒绝并展示该原因。 */
    suspend fun validate(imagePath: String): String?
}

/**
 * 识别结果分流（App 特有策略，例如按置信度分高/中/低）。
 *
 * 组件只负责「跑到结果、回调宿主」，具体如何分层与路由由宿主决定。
 * 未提供时所有结果都走 `onResult`。
 */
fun interface RecognitionResultRouter<Result> {
    /**
     * @return true 表示已自行处理（例如打开候选页），组件不再调用 `onResult`。
     */
    fun route(result: Result): Boolean
}

/** 识别客户端，对齐 iOS `PhotoRecognitionClient`。 */
fun interface PhotoRecognitionClient<Result> {
    suspend fun recognize(imagePath: String, context: RecognitionContext): Result
}

/** 额度控制器，对齐 iOS `RecognitionAccessControlling`。 */
interface RecognitionAccessControlling {
    suspend fun currentAccess(): RecognitionAccess

    /** 识别成功后消费一次额度。 */
    suspend fun consumeAfterSuccessfulRecognition()
}
