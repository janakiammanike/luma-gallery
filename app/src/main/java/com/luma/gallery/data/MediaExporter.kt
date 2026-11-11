package com.luma.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * Writes edited copies into the shared gallery so other apps can see them too.
 * Android 10+ uses the scoped-storage MediaStore flow; older versions write to the
 * public folder directly and ask the media scanner to index the new file.
 */
object MediaExporter {

    private const val ALBUM = "LumaEdits"
    private const val JPEG_QUALITY = 94

    suspend fun saveImage(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        val write: (OutputStream) -> Boolean = { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                relativeDir = "${Environment.DIRECTORY_PICTURES}/$ALBUM",
                displayName = displayName,
                mimeType = "image/jpeg",
                write = write
            )
        } else {
            writeLegacy(
                context = context,
                type = Environment.DIRECTORY_PICTURES,
                displayName = displayName,
                write = write
            )
        }
    }

    suspend fun saveVideo(
        context: Context,
        source: File,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        if (!source.exists() || source.length() <= 0L) return@withContext null

        val write: (OutputStream) -> Boolean = { output ->
            source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                relativeDir = "${Environment.DIRECTORY_MOVIES}/$ALBUM",
                displayName = displayName,
                mimeType = "video/mp4",
                write = write
            )
        } else {
            writeLegacy(
                context = context,
                type = Environment.DIRECTORY_MOVIES,
                displayName = displayName,
                write = write
            )
        }
    }

    private fun saveWithMediaStore(
        context: Context,
        collection: Uri,
        relativeDir: String,
        displayName: String,
        mimeType: String,
        write: (OutputStream) -> Boolean
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
            ?: return null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                if (write(output)) {
                    output.flush()
                    true
                } else {
                    false
                }
            } ?: false
        }.getOrDefault(false)

        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        runCatching {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }
        return uri
    }

    private fun writeLegacy(
        context: Context,
        type: String,
        displayName: String,
        write: (OutputStream) -> Boolean
    ): Uri? {
        return runCatching {
            val folder = File(Environment.getExternalStoragePublicDirectory(type), ALBUM)
                .apply { if (!exists()) mkdirs() }
            val file = File(folder, displayName)
            file.outputStream().use { output ->
                if (!write(output)) {
                    file.delete()
                    return@runCatching null
                }
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
            Uri.fromFile(file)
        }.getOrNull()
    }
}
