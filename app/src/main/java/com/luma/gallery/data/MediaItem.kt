package com.luma.gallery.data

import android.net.Uri

/**
 * A single photo or video that Luma can show and manage.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val isVideo: Boolean,
    /** MediaStore rotation in degrees (0/90/180/270). Width/height are stored unrotated. */
    val orientation: Int = 0,
    /** For an Apple Live Photo still, the MediaStore id of its paired .mov clip. */
    val liveVideoId: Long? = null
) {
    val isLivePhoto: Boolean
        get() = liveVideoId != null
    private val rotated: Boolean
        get() = orientation == 90 || orientation == 270

    /** Width as it should appear on screen, with rotation metadata applied. */
    val displayWidth: Int
        get() = if (rotated) height else width

    /** Height as it should appear on screen, with rotation metadata applied. */
    val displayHeight: Int
        get() = if (rotated) width else height

    val aspectRatio: Float
        get() = if (displayWidth > 0 && displayHeight > 0) {
            displayWidth.toFloat() / displayHeight.toFloat()
        } else {
            1f
        }

    val extension: String
        get() = name.substringAfterLast('.', "").uppercase()

    val isGif: Boolean
        get() = mimeType.equals("image/gif", ignoreCase = true)
}
