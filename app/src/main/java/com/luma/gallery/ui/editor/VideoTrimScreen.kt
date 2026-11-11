package com.luma.gallery.ui.editor

import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.luma.gallery.R
import com.luma.gallery.data.MediaItem
import com.luma.gallery.data.VideoTrimmer
import com.luma.gallery.util.buildLumaPlayer
import com.luma.gallery.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoTrimScreen(
    item: MediaItem,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val player = remember(item.id) {
        buildLumaPlayer(context).apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = false
        }
    }

    var durationMs by remember { mutableLongStateOf(item.durationMs) }
    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(item.durationMs.toFloat()) }
    var ready by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    BackHandler(enabled = !saving, onBack = onClose)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val measured = player.duration.takeIf { it > 0L } ?: item.durationMs
                    if (measured > 0L) {
                        durationMs = measured
                        if (!ready) {
                            startMs = 0f
                            endMs = measured.toFloat()
                            ready = true
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, startMs, endMs, isPlaying) {
        if (startMs > endMs) {
            endMs = startMs
        }
        while (true) {
            if (player.isPlaying && endMs > startMs && player.currentPosition >= endMs) {
                player.seekTo(startMs.toLong())
            }
            delay(120)
        }
    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.currentPosition < startMs.toLong() || player.currentPosition >= endMs.toLong()) {
                player.seekTo(startMs.toLong())
            }
            player.play()
        }
    }

    fun save() {
        if (saving || !ready || endMs - startMs < 300f) return
        saving = true
        player.pause()
        scope.launch {
            val displayName = "Luma_trim_${System.currentTimeMillis()}.mp4"
            val saved = VideoTrimmer.trimAndSave(
                context = context,
                uri = item.uri,
                startMs = startMs.toLong(),
                endMs = endMs.toLong(),
                displayName = displayName
            )
            saving = false
            if (saved != null) {
                Toast.makeText(context, R.string.video_trim_saved, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, R.string.video_trim_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, enabled = !saving) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.video_editor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { save() },
                    enabled = !saving && ready && endMs - startMs >= 300f
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        color = if (saving || !ready || endMs - startMs < 300f) Color(0x66FFFFFF)
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val ratio = item.aspectRatio.takeIf { it > 0f } ?: (16f / 9f)
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

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { togglePlayback() }) {
                        if (isPlaying) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    Modifier
                                        .size(width = 4.dp, height = 16.dp)
                                        .background(Color.White, RoundedCornerShape(1.dp))
                                )
                                Box(
                                    Modifier
                                        .size(width = 4.dp, height = 16.dp)
                                        .background(Color.White, RoundedCornerShape(1.dp))
                                )
                            }
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121218))
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 14.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 16.dp
                    )
            ) {
                Text(
                    text = stringResource(R.string.video_trim_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99FFFFFF)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(startMs.toLong()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatDuration((endMs - startMs).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF)
                    )
                    Text(
                        text = formatDuration(endMs.toLong()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        startMs = range.start.coerceIn(0f, durationMs.toFloat())
                        endMs = range.endInclusive.coerceIn(startMs + 300f, durationMs.toFloat())
                        if (player.isPlaying) player.seekTo(startMs.toLong())
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(600f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (saving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.editor_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
