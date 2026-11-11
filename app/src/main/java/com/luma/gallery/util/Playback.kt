package com.luma.gallery.util

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * Builds an ExoPlayer with a small, bounded buffer.
 *
 * ExoPlayer's default load control sizes its buffer from the track bitrate. For the
 * high-bitrate 4K HEVC clips phones record (40–90 Mbps) that means hundreds of megabytes
 * of Java-heap byte arrays, which crashes the app with OutOfMemoryError. Local files only
 * need a few seconds of read-ahead, so cap the buffer at 15 s / 32 MB.
 */
fun buildLumaPlayer(context: Context): ExoPlayer {
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            5_000,   // min buffer
            15_000,  // max buffer
            1_000,   // buffer for playback
            2_000    // buffer after rebuffer
        )
        .setTargetBufferBytes(32 * 1024 * 1024)
        .build()
    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .build()
}
