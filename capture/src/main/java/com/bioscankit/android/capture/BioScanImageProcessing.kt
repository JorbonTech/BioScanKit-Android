package com.bioscankit.android.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 图片处理工具，对应 iOS `BioScanImageProcessing`。
 *
 * iOS 用 UIImage 自带方向信息，Android 需要显式读 EXIF 并旋转，
 * 因此这里多出 [decodeFromUri] / [decodeFromFile] 两个入口。
 */
object BioScanImageProcessing {

    /** 识别用短边上限，与 iOS 的识别前缩放保持同一量级。 */
    const val DEFAULT_MAX_SHORT_EDGE = 1024

    /**
     * 从 content Uri 解码并把 EXIF 方向归一（对应 iOS `normalized(_:)`）。
     *
     * @param maxShortEdge 短边上限，超出则等比例缩小；传 null 表示不缩放。
     */
    suspend fun decodeFromUri(
        context: Context,
        uri: Uri,
        maxShortEdge: Int? = DEFAULT_MAX_SHORT_EDGE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    maxShortEdge?.let { limit ->
                        val (targetWidth, targetHeight) =
                            scaledSize(info.size.width, info.size.height, limit)
                        decoder.setTargetSize(targetWidth, targetHeight)
                    }
                }
            } else {
                decodeSampled(context, uri, maxShortEdge)
            }
            applyExifOrientation(context, uri, decoded)
        }.getOrNull()
    }

    /** 从文件解码并归一方向（相机拍照落盘后用）。 */
    suspend fun decodeFromFile(
        file: File,
        maxShortEdge: Int? = DEFAULT_MAX_SHORT_EDGE,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxShortEdge)
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: return@runCatching null
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            rotateByOrientation(decoded, orientation)
        }.getOrNull()
    }

    /**
     * 按归一化矩形裁切（对应 iOS `crop(_:normalizedRect:)`）。
     * 矩形坐标取值 0..1，超出范围会被裁剪到图片内。
     */
    fun crop(image: Bitmap, normalizedRect: android.graphics.RectF): Bitmap? {
        val left = normalizedRect.left.coerceIn(0f, 1f)
        val top = normalizedRect.top.coerceIn(0f, 1f)
        val right = normalizedRect.right.coerceIn(0f, 1f)
        val bottom = normalizedRect.bottom.coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return null

        val x = (left * image.width).roundToInt().coerceIn(0, image.width - 1)
        val y = (top * image.height).roundToInt().coerceIn(0, image.height - 1)
        val width = ((right - left) * image.width).roundToInt()
            .coerceIn(1, image.width - x)
        val height = ((bottom - top) * image.height).roundToInt()
            .coerceIn(1, image.height - y)
        return runCatching { Bitmap.createBitmap(image, x, y, width, height) }.getOrNull()
    }

    /**
     * 裁切「预览里实际可见」的区域，对应 iOS `cropAspectFillPreview`。
     *
     * @param previewRect 取景框在预览控件中的位置（像素）
     * @param previewSize 预览控件尺寸（像素）
     */
    fun cropAspectFillPreview(
        image: Bitmap,
        previewRect: androidx.compose.ui.geometry.Rect,
        previewSize: androidx.compose.ui.geometry.Size,
    ): Bitmap? {
        val normalized = CropGeometry.normalizedSourceRectForAspectFill(
            previewRect = previewRect,
            previewSize = previewSize,
            sourceSize = androidx.compose.ui.geometry.Size(
                image.width.toFloat(),
                image.height.toFloat(),
            ),
        )
        if (normalized.width <= 0f || normalized.height <= 0f) return null
        return crop(
            image,
            android.graphics.RectF(
                normalized.left,
                normalized.top,
                normalized.right,
                normalized.bottom,
            ),
        )
    }

    /** 等比缩放到短边不超过 [maxShortEdge]。 */
    fun downscaleToShortEdge(image: Bitmap, maxShortEdge: Int): Bitmap {
        if (maxShortEdge <= 0) return image
        val shortEdge = min(image.width, image.height)
        if (shortEdge <= maxShortEdge) return image
        val (width, height) = scaledSize(image.width, image.height, maxShortEdge)
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    /** 把 Bitmap 写入 JPEG 文件，供裁剪流程与分享复用。 */
    suspend fun saveToJpeg(
        bitmap: Bitmap,
        file: File,
        quality: Int = 92,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    "Unable to encode bitmap."
                }
            }
            file
        }.getOrNull()
    }

    private fun scaledSize(width: Int, height: Int, maxShortEdge: Int): Pair<Int, Int> {
        val shortEdge = min(width, height)
        if (shortEdge <= maxShortEdge) return width to height
        val scale = maxShortEdge.toFloat() / shortEdge
        return max((width * scale).roundToInt(), 1) to max((height * scale).roundToInt(), 1)
    }

    private fun sampleSize(width: Int, height: Int, maxShortEdge: Int?): Int {
        if (maxShortEdge == null || maxShortEdge <= 0) return 1
        var sampleSize = 1
        while (min(width, height) / (sampleSize * 2) >= maxShortEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeSampled(context: Context, uri: Uri, maxShortEdge: Int?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            checkNotNull(stream)
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read image bounds." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxShortEdge)
        }
        return context.contentResolver.openInputStream(uri).use { stream ->
            checkNotNull(stream)
            checkNotNull(BitmapFactory.decodeStream(stream, null, options))
        }
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri).use { stream ->
                checkNotNull(stream)
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return rotateByOrientation(bitmap, orientation)
    }

    private fun rotateByOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }
}
