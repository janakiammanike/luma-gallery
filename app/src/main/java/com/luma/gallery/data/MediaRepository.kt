package com.luma.gallery.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reads photos and videos from MediaStore. MediaStore indexes most folders on the device, but
 * files written directly to storage (LocalSend, ADB pushes, file managers) are sometimes left
 * out of its index. When All Files Access is granted, Luma walks storage once in a while and
 * asks the media scanner to index anything it finds, so those files show up too.
 */
class MediaRepository(private val context: Context) {

    private val resolver = context.contentResolver

    /** Absolute paths of the rows MediaStore returned during this load. */
    private val indexedPaths = HashSet<String>()

    /** Throttles the storage walk so pull-to-refresh doesn't crawl the whole device. */
    private var lastScanAt = 0L

    suspend fun loadAll(includeUnindexed: Boolean = false): List<MediaItem> = withContext(Dispatchers.IO) {
        indexedPaths.clear()
        var images = runCatching { queryImages() }.getOrDefault(emptyList())
        var videos = runCatching { queryVideos() }.getOrDefault(emptyList())

        if (includeUnindexed && shouldWalkStorage()) {
            val missing = runCatching { findUnindexedMedia() }.getOrDefault(emptyList())
            if (missing.isNotEmpty() && scanFiles(missing)) {
                images = runCatching { queryImages() }.getOrDefault(images)
                videos = runCatching { queryVideos() }.getOrDefault(videos)
            }
            lastScanAt = SystemClock.elapsedRealtime()
        }

        pairLivePhotos(images, videos).sortedByDescending { it.dateMillis }
    }

    private fun shouldWalkStorage(): Boolean =
        SystemClock.elapsedRealtime() - lastScanAt > SCAN_INTERVAL_MS

    /**
     * Walks shared storage for media files MediaStore does not know about. App-private
     * [Android] folders and hidden folders are skipped; the walk stops early once it has
     * collected enough candidates or looked at too many entries.
     */
    private fun findUnindexedMedia(): List<File> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val found = ArrayList<File>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        var visited = 0

        while (pending.isNotEmpty() && found.size < MAX_SCAN_FILES) {
            val dir = pending.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (++visited > MAX_SCAN_ENTRIES) return found
                val name = child.name
                if (child.isDirectory) {
                    if (name.startsWith(".")) continue
                    // Android/data and Android/obb belong to other apps. Media there is
                    // indexed by MediaStore already when it is meant to be public.
                    if (name.equals("Android", ignoreCase = true)) continue
                    pending.add(child)
                } else {
                    if (child.length() <= 0L) continue
                    val extension = name.substringAfterLast('.', "").uppercase()
                    if (extension !in MEDIA_EXTENSIONS) continue
                    if (child.absolutePath in indexedPaths) continue
                    found.add(child)
                }
            }
        }
        return found
    }

    /** Asks the media scanner to index the files. Returns true if it handled any. */
    private fun scanFiles(files: List<File>): Boolean {
        if (files.isEmpty()) return false
        val latch = CountDownLatch(files.size)
        var scanned = 0
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                files.map { it.absolutePath }.toTypedArray(),
                null
            ) { _, uri ->
                if (uri != null) scanned++
                latch.countDown()
            }
        }
        latch.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return scanned > 0
    }

    /**
     * Apple Live Photos are exported as two files that share a name: the still
     * (`IMG_1234.HEIC`/`.JPG`) and a short clip (`IMG_1234.MOV`). Android indexes them as
     * independent items, so pair them by folder + base name and remember the clip id.
     */
    private fun pairLivePhotos(
        images: List<MediaItem>,
        videos: List<MediaItem>
    ): List<MediaItem> {
        if (images.isEmpty() || videos.isEmpty()) return images + videos

        val clipByKey = videos
            .filter { it.extension in LIVE_VIDEO_EXTENSIONS }
            .associateBy { livePairKey(it) }

        val stills = images.map { image ->
            if (image.extension !in LIVE_IMAGE_EXTENSIONS) return@map image
            val clip = clipByKey[livePairKey(image)] ?: return@map image
            if (clip.id == image.id) return@map image
            image.copy(liveVideoId = clip.id)
        }
        // Clips stay in the list so they can be looked up by id; the UI hides them
        // because they are played from their still rather than shown as videos.
        return stills + videos
    }

    private fun livePairKey(item: MediaItem): String {
        val folder = item.relativePath.trimEnd('/').lowercase()
        val base = item.name.substringBeforeLast('.', item.name).lowercase()
        return "$folder|$base"
    }

    private fun imageCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun baseProjection(extra: Array<String>): Array<String> {
        val columns = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns += MediaStore.MediaColumns.RELATIVE_PATH
            // Rotation metadata. Width/height ignore this, so videos stored unrotated
            // (very common for WhatsApp/Apple exports) would otherwise be squashed.
            columns += MediaStore.MediaColumns.ORIENTATION
        }
        columns += extra
        return columns.toTypedArray()
    }

    private fun queryImages(selection: String? = null): List<MediaItem> {
        val projection = baseProjection(arrayOf(MediaStore.Images.ImageColumns.DATE_TAKEN))
        return query(
            collection = imageCollection(),
            projection = projection,
            isVideo = false,
            selection = selection
        )
    }

    private fun queryVideos(selection: String? = null): List<MediaItem> {
        val projection = baseProjection(
            arrayOf(
                MediaStore.Video.VideoColumns.DATE_TAKEN,
                MediaStore.Video.VideoColumns.DURATION
            )
        )
        return query(
            collection = videoCollection(),
            projection = projection,
            isVideo = true,
            selection = selection
        )
    }

    private fun query(
        collection: Uri,
        projection: Array<String>,
        isVideo: Boolean,
        selection: String? = null
    ): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        resolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val addedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val bucketIdIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameIdx =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val relPathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val orientationIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
            } else {
                -1
            }
            val takenIdx = cursor.getColumnIndex(
                if (isVideo) MediaStore.Video.VideoColumns.DATE_TAKEN
                else MediaStore.Images.ImageColumns.DATE_TAKEN
            )
            val durationIdx = if (isVideo) {
                cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                if (!data.isNullOrBlank()) indexedPaths += data
                val relative = when {
                    relPathIdx >= 0 && !cursor.isNull(relPathIdx) -> cursor.getString(relPathIdx)
                    !data.isNullOrBlank() -> data.substringBeforeLast('/', "")
                    else -> ""
                }
                val bucketName = if (!cursor.isNull(bucketNameIdx)) {
                    cursor.getString(bucketNameIdx)
                } else {
                    relative.trimEnd('/').substringAfterLast('/')
                }
                val taken = if (takenIdx >= 0) cursor.getLong(takenIdx) else 0L
                val added = cursor.getLong(addedIdx)
                val uri = ContentUris.withAppendedId(collection, id)

                items += MediaItem(
                    id = id,
                    uri = uri,
                    name = cursor.getString(nameIdx) ?: "Untitled",
                    mimeType = cursor.getString(mimeIdx) ?: if (isVideo) "video/*" else "image/*",
                    dateMillis = if (taken > 0) taken else added * 1000L,
                    sizeBytes = cursor.getLong(sizeIdx),
                    width = cursor.getInt(widthIdx),
                    height = cursor.getInt(heightIdx),
                    durationMs = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L,
                    bucketId = cursor.getLong(bucketIdIdx),
                    bucketName = bucketName.orEmpty(),
                    relativePath = relative.orEmpty(),
                    isVideo = isVideo,
                    orientation = if (orientationIdx >= 0) cursor.getInt(orientationIdx) else 0
                )
            }
        }
        return items
    }

    companion object {

        private val LIVE_IMAGE_EXTENSIONS = setOf("JPG", "JPEG", "HEIC", "HEIF")
        private val LIVE_VIDEO_EXTENSIONS = setOf("MOV", "MP4", "M4V")

        private val MEDIA_EXTENSIONS = setOf(
            "JPG", "JPEG", "PNG", "WEBP", "GIF", "BMP", "HEIC", "HEIF", "AVIF", "DNG",
            "MP4", "M4V", "MOV", "MKV", "WEBM", "3GP", "AVI", "TS"
        )

        private const val SCAN_INTERVAL_MS = 60_000L
        private const val SCAN_TIMEOUT_SECONDS = 12L
        private const val MAX_SCAN_FILES = 300
        private const val MAX_SCAN_ENTRIES = 80_000

        fun folderLabel(item: MediaItem): String {
            val path = item.relativePath.ifBlank {
                item.uri.path.orEmpty().let { File(it).parent.orEmpty() }
            }
            return path.trimEnd('/').split('/').lastOrNull().orEmpty()
        }

        fun shareIntent(items: List<MediaItem>): Intent {
            val uris = ArrayList(items.map { it.uri })
            return if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = items.first().mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }
}
