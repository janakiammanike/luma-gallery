package com.luma.gallery.data

import androidx.annotation.StringRes
import com.luma.gallery.R
import java.util.Locale

enum class AlbumIcon {
    All, Camera, Screenshot, Download, Chat, Instagram, Send, Facebook, Video, ScreenRecording, Heart, Folder
}

/**
 * Albums that Luma understands out of the box, based on the folder an item lives in.
 */
enum class SmartAlbum(@StringRes val titleRes: Int, val icon: AlbumIcon) {
    ALL(R.string.album_all, AlbumIcon.All),
    CAMERA(R.string.album_camera, AlbumIcon.Camera),
    SCREENSHOTS(R.string.album_screenshots, AlbumIcon.Screenshot),
    DOWNLOADS(R.string.album_downloads, AlbumIcon.Download),
    WHATSAPP(R.string.album_whatsapp, AlbumIcon.Chat),
    INSTAGRAM(R.string.album_instagram, AlbumIcon.Instagram),
    TELEGRAM(R.string.album_telegram, AlbumIcon.Send),
    FACEBOOK(R.string.album_facebook, AlbumIcon.Facebook),
    SCREEN_RECORDINGS(R.string.album_screen_recordings, AlbumIcon.ScreenRecording),
    VIDEOS(R.string.album_videos, AlbumIcon.Video),
    FAVOURITES(R.string.album_favourites, AlbumIcon.Heart);

    fun matches(item: MediaItem, favouriteIds: Set<Long>): Boolean = when (this) {
        ALL -> true
        CAMERA -> item.bucketName.equals("camera", true) ||
            item.relativePath.contains("dcim/camera", true)
        SCREENSHOTS -> item.relativePath.contains("screenshot", true) ||
            item.bucketName.contains("screenshot", true)
        DOWNLOADS -> item.relativePath.contains("download", true) ||
            item.bucketName.contains("download", true)
        WHATSAPP -> item.relativePath.contains("whatsapp", true) ||
            item.bucketName.contains("whatsapp", true)
        INSTAGRAM -> item.relativePath.contains("instagram", true) ||
            item.bucketName.contains("instagram", true)
        TELEGRAM -> item.relativePath.contains("telegram", true) ||
            item.bucketName.contains("telegram", true)
        FACEBOOK -> item.relativePath.contains("facebook", true) ||
            item.bucketName.contains("facebook", true)
        SCREEN_RECORDINGS -> item.isVideo && (
            item.relativePath.contains("screen", true) ||
                item.bucketName.contains("screen", true) ||
                item.relativePath.contains("record", true)
            )
        VIDEOS -> item.isVideo
        FAVOURITES -> item.id in favouriteIds
    }
}

data class Album(
    val key: String,
    val title: String,
    val icon: AlbumIcon,
    val count: Int,
    val cover: MediaItem?,
    val smart: SmartAlbum? = null,
    val bucketId: Long? = null,
    val customId: String? = null,
    val itemIds: Set<Long> = emptySet()
)

object AlbumBuilder {

    private fun MediaItem.folderKey(): String =
        "folder:${bucketId}:${bucketName.lowercase(Locale.ROOT)}"

    fun smartAlbums(items: List<MediaItem>, favouriteIds: Set<Long>): List<Album> =
        SmartAlbum.entries.mapNotNull { smart ->
            val matched = items.filter { smart.matches(it, favouriteIds) }
            if (matched.isEmpty() && smart != SmartAlbum.ALL) return@mapNotNull null
            Album(
                key = "smart:${smart.name}",
                title = "",
                icon = smart.icon,
                count = matched.size,
                cover = matched.maxByOrNull { it.dateMillis },
                smart = smart
            )
        }

    fun folderAlbums(items: List<MediaItem>): List<Album> =
        items
            .filter { it.bucketName.isNotBlank() }
            .groupBy { it.bucketId to it.bucketName }
            .map { (bucket, bucketItems) ->
                Album(
                    key = "folder:${bucket.first}:${bucket.second.lowercase(Locale.ROOT)}",
                    title = bucket.second,
                    icon = AlbumIcon.Folder,
                    count = bucketItems.size,
                    cover = bucketItems.maxByOrNull { it.dateMillis },
                    bucketId = bucket.first
                )
            }
            .filter { album ->
                // Hide buckets that are already represented by a smart album.
                val sample = items.firstOrNull { it.bucketId == album.bucketId }
                sample == null || SmartAlbum.entries.none { smart ->
                    smart != SmartAlbum.ALL && smart != SmartAlbum.FAVOURITES &&
                        smart != SmartAlbum.VIDEOS && smart.matches(sample, emptySet())
                }
            }
            .sortedByDescending { it.count }

    fun itemsFor(album: Album, items: List<MediaItem>, favouriteIds: Set<Long>): List<MediaItem> =
        when {
            album.smart != null -> items.filter { album.smart.matches(it, favouriteIds) }
                .sortedByDescending { it.dateMillis }
            album.bucketId != null -> items.filter { it.bucketId == album.bucketId }
                .sortedByDescending { it.dateMillis }
            album.customId != null -> items.filter { it.id in album.itemIds }
                .sortedByDescending { it.dateMillis }
            else -> items
        }
}
