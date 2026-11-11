package com.luma.gallery.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.luma.gallery.data.MediaItem
import com.luma.gallery.data.MediaRepository

/**
 * Moves media into Luma's own trash. Restoring is handled by the Trash screen, and items are
 * purged automatically after 30 days.
 */
@Composable
fun rememberMediaDeleter(
    viewModel: GalleryViewModel,
    onFinished: () -> Unit = {}
): (List<MediaItem>) -> Unit {
    val currentOnFinished by rememberUpdatedState(onFinished)

    return { items ->
        if (items.isNotEmpty()) {
            viewModel.moveToTrash(items)
            currentOnFinished()
        }
    }
}

fun shareMedia(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val intent = MediaRepository.shareIntent(items)
    context.startActivity(Intent.createChooser(intent, null))
}
