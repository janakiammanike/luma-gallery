package com.luma.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class EditorFilter { ORIGINAL, WARM, COOL, MONO, VIVID, FADE }

/** Crop window in normalised coordinates (0..1) of the rotated/flipped image. */
data class CropWindow(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

data class ImageEdits(
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val filter: EditorFilter = EditorFilter.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val crop: CropWindow? = null
)

/**
 * Decodes originals (EXIF-corrected) and renders edits. Kept separate from the UI so the
 * heavy bitmap work always runs on a background thread.
 */
object ImageEditor {

    private const val PREVIEW_MAX = 1440

    suspend fun loadPreview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri, PREVIEW_MAX) }.getOrNull()
    }

    /** Rotates/flips the already decoded preview so the crop overlay matches the screen. */
    fun applyTransform(
        source: Bitmap,
        quarterTurns: Int,
        flipH: Boolean,
        flipV: Boolean
    ): Bitmap {
        val turns = quarterTurns.mod(4)
        if (turns == 0 && !flipH && !flipV) return source

        val matrix = Matrix()
        if (turns != 0) matrix.postRotate(turns * 90f)
        if (flipH) matrix.postScale(-1f, 1f)
        if (flipV) matrix.postScale(1f, -1f)

        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun transformedSize(width: Int, height: Int, quarterTurns: Int): Pair<Int, Int> {
        return if (quarterTurns.mod(4) % 2 == 1) height to width else width to height
    }

    fun colorMatrix(edits: ImageEdits): ColorMatrix {
        val matrix = ColorMatrix()

        when (edits.filter) {
            EditorFilter.ORIGINAL -> Unit
            EditorFilter.WARM -> matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.10f, 0f, 0f, 0f, 10f,
                        0f, 1.02f, 0f, 0f, 2f,
                        0f, 0f, 0.88f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            EditorFilter.COOL -> matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.88f, 0f, 0f, 0f, -8f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 1.12f, 0f, 12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            EditorFilter.MONO -> matrix.postConcat(
                ColorMatrix().apply { setSaturation(0f) }
            )
            EditorFilter.VIVID -> matrix.postConcat(
                ColorMatrix().apply { setSaturation(1.55f) }
            )
            EditorFilter.FADE -> {
                matrix.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.84f, 0f, 0f, 0f, 36f,
                            0f, 0.84f, 0f, 0f, 36f,
                            0f, 0f, 0.84f, 0f, 36f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
                matrix.postConcat(ColorMatrix().apply { setSaturation(0.78f) })
            }
        }

        if (edits.filter != EditorFilter.MONO) {
            matrix.postConcat(
                ColorMatrix().apply {
                    setSaturation((1f + edits.saturation).coerceIn(0f, 2.5f))
                }
            )
        }

        val contrast = (1f + edits.contrast).coerceIn(0.4f, 1.9f)
        val lift = edits.brightness.coerceIn(-1f, 1f) * 90f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, lift,
                    0f, contrast, 0f, 0f, lift,
                    0f, 0f, contrast, 0f, lift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        return matrix
    }

    /** Decodes the full-resolution original, applies every edit and writes a gallery copy. */
    suspend fun saveCopy(
        context: Context,
        uri: Uri,
        edits: ImageEdits,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val source = decode(context, uri, maxDimension = null) ?: return@runCatching null
            val rendered = render(source, edits)
            val saved = MediaExporter.saveImage(context, rendered, displayName)
            rendered.recycle()
            saved
        }.getOrNull()
    }

    fun render(source: Bitmap, edits: ImageEdits): Bitmap {
        val turns = edits.quarterTurns.mod(4)
        val matrix = Matrix()
        if (turns != 0) matrix.postRotate(turns * 90f)
        if (edits.flipH) matrix.postScale(-1f, 1f)
        if (edits.flipV) matrix.postScale(1f, -1f)

        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)

        val crop = edits.crop ?: CropWindow()
        val fullWidth = bounds.width()
        val fullHeight = bounds.height()

        val left = (crop.left.coerceIn(0f, 1f) * fullWidth)
        val top = (crop.top.coerceIn(0f, 1f) * fullHeight)
        val right = (crop.right.coerceIn(0f, 1f) * fullWidth).coerceAtLeast(left + 1f)
        val bottom = (crop.bottom.coerceIn(0f, 1f) * fullHeight).coerceAtLeast(top + 1f)

        matrix.postTranslate(-left, -top)

        val outputWidth = (right - left).roundToInt().coerceAtLeast(1)
        val outputHeight = (bottom - top).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix(edits))
        }

        Canvas(output).drawBitmap(source, matrix, paint)
        return output
    }

    private fun decode(context: Context, uri: Uri, maxDimension: Int?): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        if (maxDimension != null && maxDimension > 0) {
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / (sample * 2) >= maxDimension) sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return applyExifOrientation(context, uri, raw)
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

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
            else -> return bitmap
        }

        val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (corrected !== bitmap) bitmap.recycle()
        return corrected
    }
}
