package com.luma.gallery.ui.viewer

import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.luma.gallery.R
import com.luma.gallery.data.MediaItem
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.albums.AddToAlbumSheet
import com.luma.gallery.ui.albums.CreateAlbumDialog
import com.luma.gallery.ui.components.InfoSheet
import com.luma.gallery.ui.editor.ImageEditorScreen
import com.luma.gallery.ui.editor.VideoTrimScreen
import com.luma.gallery.ui.rememberMediaDeleter
import com.luma.gallery.ui.shareMedia
import com.luma.gallery.ui.theme.LocalLumaPalette
import com.luma.gallery.util.buildLumaPlayer
import com.luma.gallery.util.formatDate
import com.luma.gallery.util.formatDuration
import com.luma.gallery.util.formatTime
import kotlinx.coroutines.delay

data class ViewerRequest(
    val items: List<MediaItem>,
    val startIndex: Int
)

@Composable
fun ViewerScreen(
    viewModel: GalleryViewModel,
    request: ViewerRequest,
    onClose: () -> Unit,
    vaultMode: Boolean = false
) {
    if (request.items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val items = request.items
    val pagerState = rememberPagerState(
        initialPage = request.startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    val page = pagerState.currentPage.coerceIn(0, items.lastIndex)
    val current = items[page]
    val context = LocalContext.current
    val palette = LocalLumaPalette.current

    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmVault by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var editImage by remember { mutableStateOf<MediaItem?>(null) }
    var editVideo by remember { mutableStateOf<MediaItem?>(null) }
    var livePlaying by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var videoPlaying by remember { mutableStateOf(false) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }

    val delete = rememberMediaDeleter(viewModel) { onClose() }

    // The clip is hidden from the grid, so look it up through the full index.
    val liveVideo = current.liveVideoId?.let { id -> viewModel.clipFor(id) }

    BackHandler {
        if (livePlaying) livePlaying = false else onClose()
    }

    LaunchedEffect(page, chromeVisible, current.isVideo) {
        // Photos behave like a normal immersive viewer; videos keep their controls on
        // screen so play/pause, trim and the action row never vanish mid-playback.
        if (chromeVisible && !current.isVideo) {
            delay(4000)
            chromeVisible = false
        }
    }

    LaunchedEffect(activePlayer, current.id) {
        val player = activePlayer ?: return@LaunchedEffect
        while (true) {
            videoPlaying = player.isPlaying
            videoPosition = player.currentPosition.coerceAtLeast(0L)
            videoDuration = player.duration.takeIf { it > 0L } ?: 0L
            delay(250)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val item = items[index]
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    isActive = page == index && editImage == null && editVideo == null,
                    onTap = { chromeVisible = !chromeVisible },
                    onActive = { player -> if (page == index) activePlayer = player },
                    onInactive = { player -> if (activePlayer === player) activePlayer = null }
                )
            } else {
                PhotoPage(
                    item = item,
                    onTap = { chromeVisible = !chromeVisible }
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 8 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 10 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xE0000000),
                            0.7f to Color(0x66000000),
                            1f to Color.Transparent
                        )
                    )
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(
                        icon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = Color(0xFFF3F1FA),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = onClose
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDate(current.dateMillis)} · ${formatTime(current.dateMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xB3FFFFFF)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${page + 1}/${items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x4D000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    if (!vaultMode) {
                        Spacer(Modifier.width(8.dp))
                        RoundIconButton(
                            icon = {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                if (current.isVideo) editVideo = current else editImage = current
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(
                        icon = {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.action_info),
                                tint = Color(0xFFF3F1FA),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { showInfo = true }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !vaultMode,
            enter = fadeIn(tween(140)) + slideInVertically(tween(220)) { it / 8 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 10 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.22f to Color(0x8C000000),
                            1f to Color(0xF2000000)
                        )
                    )
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 12.dp
                    )
                    .padding(horizontal = 8.dp)
            ) {
                if (current.isVideo && activePlayer != null) {
                    VideoControls(
                        player = activePlayer!!,
                        playing = videoPlaying,
                        positionMs = videoPosition,
                        durationMs = videoDuration
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (liveVideo != null) {
                        ViewerAction(
                            label = stringResource(R.string.live_play),
                            modifier = Modifier.weight(1f),
                            icon = {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(23.dp)
                                )
                            },
                            onClick = { livePlaying = true }
                        )
                    }

                    ViewerAction(
                        label = stringResource(R.string.action_share),
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                tint = Color(0xFFEDEBF5),
                                modifier = Modifier.size(21.dp)
                            )
                        },
                        onClick = { shareMedia(context, listOf(current)) }
                    )

                    ViewerAction(
                        label = stringResource(
                            if (viewModel.isFavourite(current.id)) R.string.action_unfavourite
                            else R.string.action_favourite
                        ),
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                imageVector = if (viewModel.isFavourite(current.id)) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Filled.FavoriteBorder
                                },
                                contentDescription = null,
                                tint = if (viewModel.isFavourite(current.id)) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFEDEBF5)
                                },
                                modifier = Modifier.size(21.dp)
                            )
                        },
                        onClick = { viewModel.toggleFavourite(current.id) }
                    )

                    ViewerAction(
                        label = stringResource(R.string.add_to_album),
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color(0xFFEDEBF5),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = { showAddSheet = true }
                    )

                    ViewerAction(
                        label = stringResource(R.string.action_delete),
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF8A9B),
                                modifier = Modifier.size(21.dp)
                            )
                        },
                        onClick = { confirmDelete = true }
                    )
                }
            }
        }

        // Small hint of the accent on the very bottom edge.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(palette.glow)
        )

        if (livePlaying && liveVideo != null) {
            LivePhotoOverlay(
                video = liveVideo,
                onClose = { livePlaying = false }
            )
        }
    }

    if (showInfo) {
        InfoSheet(
            item = current,
            album = viewModel.albumFor(current),
            isFavourite = viewModel.isFavourite(current.id),
            onToggleFavourite = { viewModel.toggleFavourite(current.id) },
            onDismiss = { showInfo = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, 1)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    delete(listOf(current))
                }) {
                    Text(
                        stringResource(R.string.delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showAddSheet) {
        AddToAlbumSheet(
            albums = viewModel.customAlbumModels(),
            onDismiss = { showAddSheet = false },
            onPick = { album ->
                album.customId?.let { id ->
                    viewModel.addToCustomAlbum(id, listOf(current.id))
                    Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
                }
                showAddSheet = false
            },
            onCreateNew = {
                showAddSheet = false
                showCreate = true
            },
            onVault = {
                showAddSheet = false
                confirmVault = true
            }
        )
    }

    if (showCreate) {
        CreateAlbumDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                viewModel.createCustomAlbum(name, listOf(current.id))
                Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (confirmVault) {
        AlertDialog(
            onDismissRequest = { confirmVault = false },
            title = { Text(stringResource(R.string.vault_move_title, 1)) },
            text = { Text(stringResource(R.string.vault_move_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmVault = false
                    viewModel.moveToVault(listOf(current))
                    onClose()
                }) {
                    Text(stringResource(R.string.vault_move_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVault = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val editingImage = editImage
    if (editingImage != null) {
        ImageEditorScreen(
            item = editingImage,
            onClose = { editImage = null },
            onSaved = {
                editImage = null
                viewModel.refresh(force = true)
            }
        )
    }

    val editingVideo = editVideo
    if (editingVideo != null) {
        VideoTrimScreen(
            item = editingVideo,
            onClose = { editVideo = null },
            onSaved = {
                editVideo = null
                viewModel.refresh(force = true)
            }
        )
    }
}

@Composable
private fun PhotoPage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .build(),
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                // Only consume drags once the photo is zoomed in. At 1x the gesture
                // must stay available to the pager so swiping moves to the next item.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoomed = scale > 1f
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break

                        val pinch = event.changes.count { it.pressed } > 1
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val next = (scale * zoomChange).coerceIn(1f, 6f)

                        if (pinch || zoomed || scale > 1f) {
                            if (next != scale || panChange != Offset.Zero) {
                                scale = next
                                offset = if (next > 1f) offset + panChange else Offset.Zero
                                event.changes.forEach { it.consume() }
                            }
                            zoomed = next > 1f
                        }

                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.6f
                        }
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    )
}

@Composable
private fun LivePhotoOverlay(
    video: MediaItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var reportedAspect by remember(video.id) { mutableFloatStateOf(0f) }

    val player = remember(video.id) {
        buildLumaPlayer(context).apply {
            setMediaItem(ExoMediaItem.fromUri(video.uri))
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val ratio = if (videoSize.height > 0) {
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                } else {
                    0f
                }
                if (ratio.isFinite() && ratio > 0f) reportedAspect = ratio
            }
        }
        player.addListener(listener)
        player.prepare()
        player.play()
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(video.id) { detectTapGestures(onTap = { onClose() }) }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val ratio = if (reportedAspect > 0f) reportedAspect else video.aspectRatio
            val fittedHeight = maxWidth / ratio
            val sizeModifier = if (fittedHeight <= maxHeight) {
                Modifier.width(maxWidth).height(fittedHeight)
            } else {
                Modifier.height(maxHeight).width(maxHeight * ratio)
            }

            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player.setVideoTextureView(this)
                    }
                },
                onRelease = { view -> player.clearVideoTextureView(view) },
                modifier = sizeModifier
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.live_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            RoundIconButton(
                icon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onClose
            )
        }
    }
}

@Composable
private fun VideoPage(
    item: MediaItem,
    isActive: Boolean,
    onTap: () -> Unit,
    onActive: (ExoPlayer) -> Unit,
    onInactive: (ExoPlayer) -> Unit
) {
    val context = LocalContext.current
    var reportedAspect by remember(item.id) { mutableFloatStateOf(0f) }
    var playbackError by remember(item.id) { mutableStateOf(false) }

    val player = remember(item.id) {
        buildLumaPlayer(context).apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // ExoPlayer reports display-oriented dimensions (rotation already
                // applied), unlike MediaStore, so this is the authority for layout.
                val ratio = if (videoSize.height > 0) {
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                } else {
                    0f
                }
                if (ratio.isFinite() && ratio > 0f) reportedAspect = ratio
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onInactive(player)
            player.release()
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            onActive(player)
            if (!playbackError) {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        } else {
            player.pause()
            // Free buffers/codecs for pages the user has swiped away from. A paused
            // player would otherwise keep a large 4K buffer alive in the Java heap.
            if (player.playbackState != Player.STATE_IDLE) player.stop()
            onInactive(player)
        }
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val fallbackRatio = item.aspectRatio.takeIf { it > 0f } ?: (16f / 9f)
            val ratio = if (reportedAspect > 0f) reportedAspect else fallbackRatio
            val fittedHeight = maxWidth / ratio
            val sizeModifier = if (fittedHeight <= maxHeight) {
                Modifier
                    .width(maxWidth)
                    .height(fittedHeight)
            } else {
                Modifier
                    .height(maxHeight)
                    .width(maxHeight * ratio)
            }

            // A plain TextureView is used instead of PlayerView: PlayerView keeps its
            // own controller and swallows touches, which would hide the viewer chrome
            // behind the video surface.
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player.setVideoTextureView(this)
                    }
                },
                onRelease = { view -> player.clearVideoTextureView(view) },
                modifier = sizeModifier
            )
        }

        // Compose owns the gestures so the viewer chrome stays reachable above the surface.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(item.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    )
                }
        )

        if (playbackError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE6141418))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.video_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.video_error_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.video_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            playbackError = false
                            reportedAspect = 0f
                            player.stop()
                            player.prepare()
                            player.play()
                        }
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoControls(
    player: ExoPlayer,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val total = durationMs.coerceAtLeast(1L)
    val toggleLabel = stringResource(if (playing) R.string.video_pause else R.string.video_play)
    val progress = if (dragging) {
        dragValue
    } else {
        positionMs.toFloat().coerceIn(0f, total.toFloat())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .semantics { contentDescription = toggleLabel }
                .clickable {
                    if (player.isPlaying) player.pause() else player.play()
                },
            contentAlignment = Alignment.Center
        ) {
            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 15.dp)
                            .background(Color.White, RoundedCornerShape(1.dp))
                    )
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 15.dp)
                            .background(Color.White, RoundedCornerShape(1.dp))
                    )
                }
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.video_play),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(if (dragging) dragValue.toLong() else positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCFFFFFF)
        )
        Slider(
            value = progress,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                player.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..total.toFloat(),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color(0x40FFFFFF)
            )
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCFFFFFF)
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0x4D000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun ViewerAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCFFFFFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
