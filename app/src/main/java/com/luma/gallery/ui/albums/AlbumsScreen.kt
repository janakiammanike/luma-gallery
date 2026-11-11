package com.luma.gallery.ui.albums

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.luma.gallery.R
import com.luma.gallery.data.Album
import com.luma.gallery.data.AlbumIcon
import com.luma.gallery.data.MediaItem
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.components.EmptyState
import com.luma.gallery.ui.components.LumaWordmark
import com.luma.gallery.ui.components.MediaGrid
import com.luma.gallery.ui.components.SectionHeader
import com.luma.gallery.ui.rememberMediaDeleter
import com.luma.gallery.ui.shareMedia
import com.luma.gallery.ui.theme.LocalLumaPalette

@Composable
fun albumTitle(album: Album): String =
    album.smart?.let { stringResource(it.titleRes) } ?: album.title

@Composable
fun albumIcon(icon: AlbumIcon): Painter = when (icon) {
    AlbumIcon.All -> painterResource(R.drawable.ic_photos)
    AlbumIcon.Camera -> painterResource(R.drawable.ic_camera)
    AlbumIcon.Screenshot -> painterResource(R.drawable.ic_screenshot)
    AlbumIcon.Download -> painterResource(R.drawable.ic_download)
    AlbumIcon.Chat -> painterResource(R.drawable.ic_chat)
    AlbumIcon.Instagram -> painterResource(R.drawable.ic_camera)
    AlbumIcon.Send -> painterResource(R.drawable.ic_send)
    AlbumIcon.Facebook -> painterResource(R.drawable.ic_image)
    AlbumIcon.Video -> painterResource(R.drawable.ic_play_circle)
    AlbumIcon.ScreenRecording -> painterResource(R.drawable.ic_screenshot)
    AlbumIcon.Heart -> painterResource(R.drawable.ic_favorite)
    AlbumIcon.Folder -> painterResource(R.drawable.ic_folder)
}

@Composable
fun AlbumsScreen(
    viewModel: GalleryViewModel,
    onOpenAlbum: (Album) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenVault: () -> Unit
) {
    val smartAlbums = remember(viewModel.items, viewModel.favourites) { viewModel.smartAlbums() }
    val folderAlbums = remember(viewModel.items) { viewModel.folderAlbums() }
    val customAlbums = remember(viewModel.items, viewModel.customAlbums) {
        viewModel.customAlbumModels()
    }
    var showCreate by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                            10.dp
                    )
                    .padding(bottom = 8.dp)
            ) {
                LumaWordmark(
                    title = stringResource(R.string.tab_albums),
                    subtitle = stringResource(R.string.photos_count, viewModel.items.size)
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(stringResource(R.string.albums_smart), Modifier.padding(horizontal = 4.dp))
        }

        items(smartAlbums, key = { it.key }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album) })
        }

        if (folderAlbums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    stringResource(R.string.albums_folders),
                    Modifier.padding(horizontal = 4.dp)
                )
            }
            items(folderAlbums, key = { it.key }) { album ->
                AlbumCard(album = album, onClick = { onOpenAlbum(album) })
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                stringResource(R.string.albums_mine),
                Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            NewAlbumCard(onClick = { showCreate = true })
        }

        items(customAlbums, key = { it.key }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album) })
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                stringResource(R.string.vault_section),
                Modifier.padding(horizontal = 4.dp)
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            VaultCard(
                count = viewModel.vaultItems.size,
                onClick = onOpenVault
            )
        }

        if (viewModel.trashSupported) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    stringResource(R.string.albums_recently_deleted),
                    Modifier.padding(horizontal = 4.dp)
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                TrashCard(onClick = onOpenTrash)
            }
        }
    }

    if (showCreate) {
        CreateAlbumDialog(
            onDismiss = { showCreate = false },
            onCreate = { viewModel.createCustomAlbum(it) }
        )
    }
}

@Composable
private fun NewAlbumCard(onClick: () -> Unit) {
    val palette = LocalLumaPalette.current
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.album_new),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TrashCard(onClick: () -> Unit) {
    val palette = LocalLumaPalette.current
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.trash_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.trash_note),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }
    }
}

@Composable
private fun VaultCard(count: Int, onClick: () -> Unit) {
    val palette = LocalLumaPalette.current
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.vault_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (count == 0) {
                    stringResource(R.string.vault_note)
                } else {
                    stringResource(R.string.photos_count, count) + " · " +
                        stringResource(R.string.vault_note)
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    val palette = LocalLumaPalette.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(palette.card)
            .clickable(onClick = onClick)
    ) {
        val cover = album.cover
        if (cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cover.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = albumTitle(album),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = albumIcon(album.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.Center)
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color(0x22000000),
                        1f to Color(0xD9000000)
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x66000000))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = albumIcon(album.icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = album.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = albumTitle(album),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.photos_count, album.count),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xCCFFFFFF)
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    viewModel: GalleryViewModel,
    album: Album,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val palette = LocalLumaPalette.current
    val liveAlbum = remember(album.key, viewModel.customAlbums, viewModel.items) {
        if (album.customId != null) {
            viewModel.customAlbumModels().firstOrNull { it.customId == album.customId } ?: album
        } else {
            album
        }
    }
    val items = remember(
        liveAlbum.key,
        liveAlbum.itemIds,
        viewModel.items,
        viewModel.favourites
    ) {
        viewModel.albumItems(liveAlbum)
    }
    val selection = viewModel.selectedIds
    var confirmDelete by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteAlbum by remember { mutableStateOf(false) }
    var albumMenu by remember { mutableStateOf(false) }
    val delete = rememberMediaDeleter(viewModel) {
        viewModel.clearSelection()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selection.isEmpty()) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = albumTitle(liveAlbum),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.photos_count, items.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted
                        )
                    }
                    if (liveAlbum.customId != null) {
                        Box {
                            IconButton(onClick = { albumMenu = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.album_options),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            DropdownMenu(
                                expanded = albumMenu,
                                onDismissRequest = { albumMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.album_rename)) },
                                    onClick = {
                                        albumMenu = false
                                        showRename = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.album_delete),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        albumMenu = false
                                        showDeleteAlbum = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = stringResource(R.string.selected_count, selection.size),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_to_album),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { shareMedia(context, viewModel.itemsByIds(selection)) }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_album),
                    hint = stringResource(
                        if (liveAlbum.customId != null) R.string.empty_album_hint
                        else R.string.empty_gallery_hint
                    )
                )
            } else {
                MediaGrid(
                    items = items,
                    columns = viewModel.gridColumns,
                    selection = selection,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp),
                    showDateHeaders = false,
                    onOpen = { index -> onOpen(items, index) },
                    onLongPress = { viewModel.toggleSelection(it.id) }
                )
            }
        }
    }

    if (confirmDelete) {
        val selectedItems = viewModel.itemsByIds(selection)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, selectedItems.size)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    delete(selectedItems)
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
            albums = viewModel.customAlbumModels().filter { it.customId != liveAlbum.customId },
            onDismiss = { showAddSheet = false },
            onPick = { target ->
                target.customId?.let { id ->
                    viewModel.addToCustomAlbum(id, selection)
                    Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
                }
                showAddSheet = false
                viewModel.clearSelection()
            },
            onCreateNew = {
                showAddSheet = false
                showCreate = true
            }
        )
    }

    if (showCreate) {
        CreateAlbumDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                viewModel.createCustomAlbum(name, selection)
                viewModel.clearSelection()
                Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showRename && liveAlbum.customId != null) {
        RenameAlbumDialog(
            initialName = liveAlbum.title,
            onDismiss = { showRename = false },
            onRename = { name ->
                liveAlbum.customId?.let { viewModel.renameCustomAlbum(it, name) }
            }
        )
    }

    if (showDeleteAlbum && liveAlbum.customId != null) {
        DeleteAlbumDialog(
            onDismiss = { showDeleteAlbum = false },
            onConfirm = {
                liveAlbum.customId?.let { viewModel.deleteCustomAlbum(it) }
                onBack()
            }
        )
    }
}
