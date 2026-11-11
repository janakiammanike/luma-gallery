package com.luma.gallery.web

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.luma.gallery.data.AlbumBuilder
import com.luma.gallery.data.MediaItem
import com.luma.gallery.data.MediaRepository
import com.luma.gallery.data.SmartAlbum
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Media access for browsers. Browsers cannot render HEIC and cannot stream arbitrary
 * MediaStore Uris, so everything is converted / proxied here.
 */
class WebMedia(private val context: Context) {

    private val repository = MediaRepository(context)
    private val resolver = context.contentResolver
    private val lock = Any()

    @Volatile
    private var cached: List<MediaItem> = emptyList()

    @Volatile
    private var byId: Map<Long, MediaItem> = emptyMap()

    @Volatile
    private var cachedAt = 0L

    // ----- library ---------------------------------------------------------

    fun items(force: Boolean = false): List<MediaItem> {
        if (!force && cachedAt != 0L && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS) {
            return cached
        }
        synchronized(lock) {
            if (!force && cachedAt != 0L && SystemClock.elapsedRealtime() - cachedAt < CACHE_MS) {
                return cached
            }
            val fresh = runCatching { runBlocking { repository.loadAll() } }.getOrDefault(cached)
            cached = fresh
            byId = fresh.associateBy { it.id }
            cachedAt = SystemClock.elapsedRealtime()
            return fresh
        }
    }

    fun invalidate() {
        cachedAt = 0L
    }

    /** Library size as a person counts it: paired Live Photo clips are folded into their still. */
    fun visibleCount(): Int {
        val all = items()
        val clipIds = all.mapNotNullTo(HashSet()) { it.liveVideoId }
        return all.size - clipIds.size
    }

    fun item(id: Long): MediaItem? {
        items()
        return byId[id]
    }

    fun filtered(
        smart: String?,
        bucket: Long?,
        type: String?,
        search: String?
    ): List<MediaItem> {
        var list = items().let { all ->
            val clipIds = all.mapNotNullTo(HashSet()) { it.liveVideoId }
            if (clipIds.isEmpty()) all else all.filter { it.id !in clipIds }
        }
        if (bucket != null) list = list.filter { it.bucketId == bucket }
        if (!smart.isNullOrBlank()) {
            val album = runCatching { SmartAlbum.valueOf(smart) }.getOrNull()
            if (album != null) {
                val favourites = favourites()
                list = list.filter { album.matches(it, favourites) }
            }
        }
        when (type) {
            "image" -> list = list.filter { !it.isVideo }
            "video" -> list = list.filter { it.isVideo }
            "live" -> list = list.filter { it.isLivePhoto }
        }
        val query = search?.trim().orEmpty()
        if (query.isNotEmpty()) {
            val needle = query.lowercase(Locale.ROOT)
            list = list.filter {
                it.name.lowercase(Locale.ROOT).contains(needle) ||
                    it.bucketName.lowercase(Locale.ROOT).contains(needle)
            }
        }
        return list.sortedByDescending { it.dateMillis }
    }

    private fun favourites(): Set<Long> =
        context.getSharedPreferences("luma_prefs", Context.MODE_PRIVATE)
            .getStringSet("favourites", emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    // ----- JSON ------------------------------------------------------------

    fun mediaJson(item: MediaItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("name", item.name)
        put("mime", item.mimeType)
        put("video", item.isVideo)
        put("date", item.dateMillis)
        put("size", item.sizeBytes)
        put("width", item.displayWidth)
        put("height", item.displayHeight)
        put("duration", item.durationMs)
        put("bucket", item.bucketName)
        put("folder", item.relativePath.trimEnd('/').substringAfterLast('/'))
        put("live", item.isLivePhoto)
        put("liveVideoId", item.liveVideoId ?: 0L)
        put("thumb", "/api/thumb/${item.id}")
        put("image", "/api/image/${item.id}")
        put("videoUrl", if (item.isVideo) "/api/video/${item.id}" else JSONObject.NULL)
        put("download", "/api/download/${item.id}")
    }

    fun mediaArray(list: List<MediaItem>): JSONArray = JSONArray().apply {
        list.forEach { put(mediaJson(it)) }
    }

    fun albumArray(): JSONArray {
        val all = items().let { loaded ->
            val clipIds = loaded.mapNotNullTo(HashSet()) { it.liveVideoId }
            if (clipIds.isEmpty()) loaded else loaded.filter { it.id !in clipIds }
        }
        val favourites = favourites()
        val array = JSONArray()

        AlbumBuilder.smartAlbums(all, favourites).forEach { album ->
            val smart = album.smart ?: return@forEach
            array.put(
                JSONObject().apply {
                    put("key", "smart:${smart.name}")
                    put("smart", smart.name)
                    put("title", context.getString(smart.titleRes))
                    put("count", album.count)
                    put("type", "smart")
                    put("cover", album.cover?.let { "/api/thumb/${it.id}" } ?: JSONObject.NULL)
                }
            )
        }

        AlbumBuilder.folderAlbums(all).forEach { album ->
            array.put(
                JSONObject().apply {
                    put("key", "bucket:${album.bucketId}")
                    put("bucket", album.bucketId ?: 0L)
                    put("title", album.title)
                    put("count", album.count)
                    put("type", "folder")
                    put("cover", album.cover?.let { "/api/thumb/${it.id}" } ?: JSONObject.NULL)
                }
            )
        }
        return array
    }

    // ----- thumbnails & stills --------------------------------------------

    fun thumbnail(item: MediaItem, target: Int): ByteArray? {
        val size = target.coerceIn(96, 640)
        val bitmap = if (item.isVideo) videoFrame(item, size) else imageThumb(item, size)
        return bitmap?.let { compressJpeg(it, 82) }
    }

    fun fullImage(item: MediaItem): ByteArray? {
        if (item.mimeType.equals("image/gif", true)) {
            return runCatching { readAll(item, 40L * 1024 * 1024) }.getOrNull()
        }
        val bitmap = decodeSampled(item.uri, 2600, 2600) ?: return null
        return compressJpeg(bitmap, 90)
    }

    fun stillMime(item: MediaItem): String =
        if (item.mimeType.equals("image/gif", true)) "image/gif" else "image/jpeg"

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val target = if (bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flat ->
                val canvas = Canvas(flat)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        } else {
            bitmap
        }
        target.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (target !== bitmap) target.recycle()
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun imageThumb(item: MediaItem, target: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = runCatching {
                resolver.loadThumbnail(item.uri, Size(target, target), null)
            }.getOrNull()
            if (loaded != null) return loaded
        }
        return decodeSampled(item.uri, target, target)
    }

    private fun videoFrame(item: MediaItem, target: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val timeUs = if (item.durationMs > 1_500L) 1_000_000L else 0L
            val ratio = item.aspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: 1f
            val width: Int
            val height: Int
            if (ratio >= 1f) {
                width = target
                height = (target / ratio).toInt().coerceAtLeast(1)
            } else {
                height = target
                width = (target * ratio).toInt().coerceAtLeast(1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampled(
        uri: android.net.Uri,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth ||
                bounds.outHeight / (sample * 2) >= maxHeight
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val rotated = applyExifRotation(uri, bitmap)
            if (rotated.width > maxWidth || rotated.height > maxHeight) {
                val scaled = scaleToFit(rotated, maxWidth, maxHeight)
                if (scaled !== rotated) rotated.recycle()
                scaled
            } else {
                rotated
            }
        }.getOrNull()
    }

    private fun applyExifRotation(uri: android.net.Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )
        if (ratio >= 1f) return bitmap
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    // ----- streaming -------------------------------------------------------

    fun totalBytes(item: MediaItem): Long {
        if (item.sizeBytes > 0) return item.sizeBytes
        return runCatching {
            resolver.openAssetFileDescriptor(item.uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0 } ?: -1L
    }

    fun open(item: MediaItem): InputStream? =
        runCatching { resolver.openInputStream(item.uri) }.getOrNull()

    /** Pretty filename for Content-Disposition. */
    fun downloadName(item: MediaItem): String =
        item.name.ifBlank { "luma_${item.id}" }

    // ----- live photo zip --------------------------------------------------

    fun liveZip(item: MediaItem): ByteArray? {
        val clipId = item.liveVideoId ?: return null
        val clip = item(clipId) ?: return null
        val output = ByteArrayOutputStream()
        runCatching {
            ZipOutputStream(output).use { zip ->
                addEntry(zip, item)
                addEntry(zip, clip)
            }
        }.onFailure { return null }
        return output.toByteArray()
    }

    private fun addEntry(zip: ZipOutputStream, item: MediaItem) {
        val name = downloadName(item)
        zip.putNextEntry(ZipEntry(name))
        resolver.openInputStream(item.uri)?.use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    // ----- uploads ---------------------------------------------------------

    data class Uploaded(val id: Long, val name: String, val video: Boolean)

    fun upload(tempFile: File, rawName: String): Uploaded? {
        val name = sanitize(rawName)
        val mime = mimeFor(name)
        val video = mime.startsWith("video/")
        val relative = if (video) "Movies/Luma/" else "Pictures/Luma/"

        return runCatching {
            val finalName = uniqueName(relative, name)
            val collection = if (video) videoCollection() else imageCollection()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    val root = Environment.getExternalStoragePublicDirectory(
                        if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    )
                    val folder = File(root, "Luma").apply { mkdirs() }
                    put(MediaStore.MediaColumns.DATA, File(folder, finalName).absolutePath)
                }
            }
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            val id = ContentUris.parseId(uri)
            invalidate()
            WebBridge.notifyLibraryChanged()
            Uploaded(id, finalName, video)
        }.getOrNull()
    }

    private fun imageCollection(): android.net.Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun videoCollection(): android.net.Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun uniqueName(relative: String, name: String): String {
        if (!nameExists(relative, name)) return name
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (index < 5_000) {
            val candidate = if (extension.isBlank()) {
                "$base ($index)"
            } else {
                "$base ($index).$extension"
            }
            if (!nameExists(relative, candidate)) return candidate
            index++
        }
        return "${base}_${System.currentTimeMillis()}" +
            if (extension.isBlank()) "" else ".$extension"
    }

    private fun nameExists(relative: String, name: String): Boolean {
        return runCatching {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection: String
            val args: Array<String>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
                args = arrayOf(relative, name)
            } else {
                val root = Environment.getExternalStoragePublicDirectory(
                    if (relative.startsWith("Movies")) Environment.DIRECTORY_MOVIES
                    else Environment.DIRECTORY_PICTURES
                )
                selection = "${MediaStore.MediaColumns.DATA}=?"
                args = arrayOf(File(File(root, "Luma"), name).absolutePath)
            }
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun sanitize(raw: String): String {
        val trimmed = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = trimmed
            .filter { it.code >= 32 && it != ':' && it != '*' && it != '?' && it != '"' &&
                it != '<' && it != '>' && it != '|' }
            .take(120)
        return when {
            cleaned.isBlank() -> "luma_${System.currentTimeMillis()}.jpg"
            !cleaned.contains('.') -> "$cleaned.jpg"
            else -> cleaned
        }
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "dng" -> "image/x-adobe-dng"
        "mp4" -> "video/mp4"
        "m4v" -> "video/x-m4v"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        else -> "application/octet-stream"
    }

    private fun readAll(item: MediaItem, limit: Long): ByteArray? {
        val input = resolver.openInputStream(item.uri) ?: return null
        return input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read <= 0) break
                total += read
                if (total > limit) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    companion object {
        private const val CACHE_MS = 4_000L
    }
}
