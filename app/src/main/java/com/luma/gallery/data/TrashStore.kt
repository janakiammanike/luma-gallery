package com.luma.gallery.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A file that Luma has moved out of the gallery and into its own trash folder. The bytes live in
 * app-private storage, so restoring works on every device — even ones that don't implement the
 * Android 11 system trash.
 */
data class TrashEntry(
    val id: String,
    val originalId: Long,
    val name: String,
    val mimeType: String,
    val relativePath: String,
    val dateMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** MediaStore rotation in degrees; width/height above are stored unrotated. */
    val orientation: Int = 0,
    val durationMs: Long,
    val isVideo: Boolean,
    val trashedAt: Long,
    val fileName: String
) {
    /** Stable id used by the grid when this entry is shown as a media item. */
    val mediaId: Long
        get() = id.hashCode().toLong()
}

object TrashStore {

    private const val KEY = "trash_entries"
    private const val DIR = "trash"
    private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun fileFor(context: Context, entry: TrashEntry): File = File(dir(context), entry.fileName)

    fun toMediaItem(context: Context, entry: TrashEntry): MediaItem = MediaItem(
        id = entry.mediaId,
        uri = Uri.fromFile(fileFor(context, entry)),
        name = entry.name,
        mimeType = entry.mimeType,
        dateMillis = entry.dateMillis,
        sizeBytes = entry.sizeBytes,
        width = entry.width,
        height = entry.height,
        durationMs = entry.durationMs,
        bucketId = 0L,
        bucketName = "",
        relativePath = entry.relativePath,
        isVideo = entry.isVideo,
        orientation = entry.orientation
    )

    fun load(prefs: SharedPreferences): List<TrashEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    add(
                        TrashEntry(
                            id = obj.getString("id"),
                            originalId = obj.optLong("originalId", -1L),
                            name = obj.getString("name"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            relativePath = obj.optString("relativePath", ""),
                            dateMillis = obj.optLong("dateMillis", 0L),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            width = obj.optInt("width", 0),
                            height = obj.optInt("height", 0),
                            orientation = obj.optInt("orientation", 0),
                            durationMs = obj.optLong("durationMs", 0L),
                            isVideo = obj.optBoolean("isVideo", false),
                            trashedAt = obj.optLong("trashedAt", 0L),
                            fileName = obj.getString("fileName")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: SharedPreferences, entries: List<TrashEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("originalId", entry.originalId)
                    put("name", entry.name)
                    put("mimeType", entry.mimeType)
                    put("relativePath", entry.relativePath)
                    put("dateMillis", entry.dateMillis)
                    put("sizeBytes", entry.sizeBytes)
                    put("width", entry.width)
                    put("height", entry.height)
                    put("orientation", entry.orientation)
                    put("durationMs", entry.durationMs)
                    put("isVideo", entry.isVideo)
                    put("trashedAt", entry.trashedAt)
                    put("fileName", entry.fileName)
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    /**
     * Copies the given media into the trash folder. The caller is responsible for deleting the
     * originals afterwards; [TrashEntry.originalId] tells it which items were safely copied.
     */
    suspend fun moveToTrash(
        context: Context,
        prefs: SharedPreferences,
        items: List<MediaItem>
    ): List<TrashEntry> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val folder = dir(context)
        val created = ArrayList<TrashEntry>()

        items.forEach { item ->
            val extension = item.name.substringAfterLast('.', "").ifBlank { "bin" }
            val fileName = "${UUID.randomUUID()}.$extension"
            val target = File(folder, fileName)

            val copied = runCatching {
                resolver.openInputStream(item.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                } ?: error("No input stream for ${item.uri}")
            }.isSuccess

            if (copied && target.length() > 0L) {
                created += TrashEntry(
                    id = UUID.randomUUID().toString(),
                    originalId = item.id,
                    name = item.name,
                    mimeType = item.mimeType,
                    relativePath = item.relativePath,
                    dateMillis = item.dateMillis,
                    sizeBytes = target.length(),
                    width = item.width,
                    height = item.height,
                    orientation = item.orientation,
                    durationMs = item.durationMs,
                    isVideo = item.isVideo,
                    trashedAt = System.currentTimeMillis(),
                    fileName = fileName
                )
            } else {
                target.delete()
            }
        }

        if (created.isNotEmpty()) {
            save(prefs, load(prefs) + created)
        }
        created
    }

    /**
     * Puts trashed entries back into MediaStore, in their original folder when possible.
     * Returns how many were restored.
     */
    suspend fun restore(
        context: Context,
        prefs: SharedPreferences,
        entries: List<TrashEntry>
    ): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var restored = 0

        entries.forEach { entry ->
            val file = fileFor(context, entry)
            if (!file.exists()) return@forEach

            val collection =
                if (entry.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entry.name)
                put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
                put(MediaStore.MediaColumns.DATE_ADDED, entry.dateMillis / 1000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (entry.relativePath.isNotBlank()) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, entry.relativePath)
                    }
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(MediaStore.MediaColumns.DATE_TAKEN, entry.dateMillis)
                }
            }

            val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
            if (uri == null) return@forEach

            val written = runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                } ?: error("No output stream for $uri")
            }.isSuccess

            if (written) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null
                        )
                    }
                }
                file.delete()
                restored++
            } else {
                runCatching { resolver.delete(uri, null, null) }
            }
        }

        // Drop entries whose backing file is gone (restored or missing).
        val remaining = load(prefs).filter { fileFor(context, it).exists() }
        save(prefs, remaining)
        restored
    }

    fun remove(context: Context, prefs: SharedPreferences, entries: List<TrashEntry>) {
        entries.forEach { runCatching { fileFor(context, it).delete() } }
        val removed = entries.map { it.id }.toSet()
        save(prefs, load(prefs).filterNot { it.id in removed })
    }

    fun removeEntry(context: Context, prefs: SharedPreferences, entry: TrashEntry) {
        remove(context, prefs, listOf(entry))
    }

    /** Deletes anything that has been waiting for more than 30 days. */
    suspend fun purgeExpired(context: Context, prefs: SharedPreferences) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entries = load(prefs)
            val expired = entries.filter { entry ->
                !fileFor(context, entry).exists() ||
                    (entry.trashedAt > 0L && now - entry.trashedAt > MAX_AGE_MS)
            }
            if (expired.isNotEmpty()) remove(context, prefs, expired)
        }
}
