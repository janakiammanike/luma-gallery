package com.luma.gallery.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Trims a video using Media3's Transformer, then saves the result as a new gallery item.
 * The source is never modified.
 */
object VideoTrimmer {

    suspend fun trimAndSave(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        val output = File(context.cacheDir, "trim_${UUID.randomUUID()}.mp4")
        val exported = export(context, uri, startMs, endMs, output)
        if (exported && output.exists() && output.length() > 0L) {
            val saved = MediaExporter.saveVideo(context, output, displayName)
            output.delete()
            saved
        } else {
            output.delete()
            null
        }
    }

    private suspend fun export(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        output: File
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                })
                .build()

            continuation.invokeOnCancellation { transformer.cancel() }

            runCatching {
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                    .setEndPositionMs(endMs.coerceAtLeast(startMs + 300L))
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(clipping)
                    .build()
                val edited = EditedMediaItem.Builder(mediaItem).build()
                transformer.start(edited, output.absolutePath)
            }.onFailure {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }
}
