package com.luma.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.luma.gallery.R
import com.luma.gallery.data.Album
import com.luma.gallery.data.MediaItem
import com.luma.gallery.data.MediaRepository
import com.luma.gallery.ui.theme.LocalLumaPalette
import com.luma.gallery.util.formatBytes
import com.luma.gallery.util.formatDate
import com.luma.gallery.util.formatDateHeader
import com.luma.gallery.util.formatDuration
import com.luma.gallery.util.formatTime
import com.luma.gallery.util.mimeLabel
import com.luma.gallery.util.resolutionLabel

@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val palette = LocalLumaPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(palette.glow, Color.Transparent),
                    center = Offset(140f, 40f),
                    radius = 900f
                )
            )
    )
}

@Composable
fun LumaWordmark(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = LocalLumaPalette.current.muted
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalLumaPalette.current.muted,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
fun LumaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumaPalette.current
    val bg = if (selected) MaterialTheme.colorScheme.primary else palette.card
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else palette.border, CircleShape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun MediaThumb(
    item: MediaItem,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumaPalette.current
    val context = LocalContext.current
    val ratio = remember(item.id) { item.aspectRatio.coerceIn(0.62f, 1.75f) }
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .aspectRatio(ratio)
            .clip(shape)
            .background(palette.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        if (item.isLivePhoto) {
            Text(
                text = stringResource(R.string.live_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }


        if (item.isGif) {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }

        if (inSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (selected) Color(0x33B49CFF) else Color(0x14000000))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color(0x66000000))
                    .border(
                        width = 1.5.dp,
                        color = if (selected) Color.Transparent else Color.White,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

private sealed interface TimelineRow {
    val key: String

    data class Header(val label: String, override val key: String) : TimelineRow
    data class Media(val item: MediaItem, val index: Int) : TimelineRow {
        override val key: String get() = "m-${item.id}"
    }
}

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    showDateHeaders: Boolean = true,
    header: (@Composable () -> Unit)? = null,
    onOpen: (Int) -> Unit,
    onLongPress: (MediaItem) -> Unit
) {
    val today = stringResource(R.string.date_today)
    val yesterday = stringResource(R.string.date_yesterday)

    val rows = remember(items, showDateHeaders, today, yesterday) {
        val result = ArrayList<TimelineRow>()
        var lastLabel: String? = null
        items.forEachIndexed { index, item ->
            if (showDateHeaders) {
                val label = formatDateHeader(item.dateMillis) ?: run {
                    val date = com.luma.gallery.util.localDate(item.dateMillis)
                    val now = java.time.LocalDate.now()
                    if (date == now) today else yesterday
                }
                if (label != lastLabel) {
                    result += TimelineRow.Header(label, "h-$label-${item.id}")
                    lastLabel = label
                }
            }
            result += TimelineRow.Media(item, index)
        }
        result
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = rememberLazyStaggeredGridState(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalItemSpacing = 5.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            header?.let {
                item(span = StaggeredGridItemSpan.FullLine, key = "screen-header") {
                    it()
                }
            }

            items(
                count = rows.size,
                key = { rows[it].key },
                span = { index ->
                    if (rows[index] is TimelineRow.Header) {
                        StaggeredGridItemSpan.FullLine
                    } else {
                        StaggeredGridItemSpan.SingleLane
                    }
                }
            ) { index ->
                when (val row = rows[index]) {
                    is TimelineRow.Header -> {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 22.dp,
                                bottom = 6.dp
                            )
                        )
                    }
                    is TimelineRow.Media -> {
                        MediaThumb(
                            item = row.item,
                            selected = row.item.id in selection,
                            inSelectionMode = selection.isNotEmpty(),
                            onClick = {
                                if (selection.isEmpty()) onOpen(row.index)
                                else onLongPress(row.item)
                            },
                            onLongClick = { onLongPress(row.item) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(LocalLumaPalette.current.card),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_photos),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalLumaPalette.current.muted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    item: MediaItem,
    album: Album?,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLumaPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.card,
        dragHandle = null
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.info_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isFavourite) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else palette.chrome
                        )
                        .combinedClickable(onClick = onToggleFavourite)
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavourite) R.string.action_unfavourite else R.string.action_favourite
                        ),
                        tint = if (isFavourite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            InfoRow(stringResource(R.string.info_name), item.name)
            InfoRow(
                stringResource(R.string.info_path),
                MediaRepository.folderLabel(item).ifBlank { "—" }
            )
            InfoRow(
                stringResource(R.string.info_album),
                album?.title ?: item.bucketName.ifBlank { "—" }
            )
            InfoRow(stringResource(R.string.info_type), mimeLabel(item.mimeType))
            InfoRow(
                stringResource(R.string.info_resolution),
                resolutionLabel(item.displayWidth, item.displayHeight)
            )
            if (item.isVideo) {
                InfoRow(stringResource(R.string.info_duration), formatDuration(item.durationMs))
            }
            InfoRow(stringResource(R.string.info_size), formatBytes(item.sizeBytes))
            InfoRow(
                stringResource(R.string.info_date),
                "${formatDate(item.dateMillis)} · ${formatTime(item.dateMillis)}"
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalLumaPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FloatingPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalLumaPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(palette.chrome)
            .border(1.dp, palette.border, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        content()
    }
}

@Composable
fun StatPill(text: String, modifier: Modifier = Modifier) {
    val palette = LocalLumaPalette.current
    Box(
        modifier
            .clip(CircleShape)
            .background(palette.card)
            .border(1.dp, palette.border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
    }
}
