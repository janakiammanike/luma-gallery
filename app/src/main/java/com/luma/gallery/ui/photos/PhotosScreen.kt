package com.luma.gallery.ui.photos

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.gallery.R
import com.luma.gallery.data.MediaItem
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.albums.AddToAlbumSheet
import com.luma.gallery.ui.albums.CreateAlbumDialog
import com.luma.gallery.ui.components.EmptyState
import com.luma.gallery.ui.components.LumaWordmark
import com.luma.gallery.ui.components.MediaGrid
import com.luma.gallery.ui.rememberMediaDeleter
import com.luma.gallery.ui.shareMedia
import com.luma.gallery.ui.theme.LocalLumaPalette

@Composable
fun PhotosScreen(
    viewModel: GalleryViewModel,
    onOpen: (List<MediaItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalLumaPalette.current
    val items = viewModel.items
    val selection = viewModel.selectedIds
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmVault by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    val delete = rememberMediaDeleter(viewModel) {
        viewModel.clearSelection()
    }

    Column(Modifier.fillMaxSize()) {
        PhotosTopBar(
            viewModel = viewModel,
            selectionCount = selection.size,
            onClearSelection = { viewModel.clearSelection() },
            onAddToAlbum = { showAddSheet = true },
            onShare = { shareMedia(context, viewModel.itemsByIds(selection)) },
            onDelete = { confirmDelete = true }
        )

        when {
            !viewModel.loadedOnce && items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                    }
                }
            }

            items.isEmpty() -> EmptyState(
                title = stringResource(R.string.empty_gallery),
                hint = stringResource(R.string.empty_gallery_hint)
            )

            else -> MediaGrid(
                items = items,
                columns = viewModel.gridColumns,
                selection = selection,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 132.dp),
                onOpen = { index -> onOpen(items, index) },
                onLongPress = { viewModel.toggleSelection(it.id) }
            )
        }
    }

    if (confirmDelete) {
        val selectedItems = viewModel.itemsByIds(selection)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(stringResource(R.string.delete_title, selectedItems.size))
            },
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
            albums = viewModel.customAlbumModels(),
            onDismiss = { showAddSheet = false },
            onPick = { album ->
                album.customId?.let { id ->
                    viewModel.addToCustomAlbum(id, selection)
                    Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
                }
                showAddSheet = false
                viewModel.clearSelection()
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
                viewModel.createCustomAlbum(name, selection)
                viewModel.clearSelection()
                Toast.makeText(context, R.string.album_added, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (confirmVault) {
        val selectedItems = viewModel.itemsByIds(selection)
        AlertDialog(
            onDismissRequest = { confirmVault = false },
            title = { Text(stringResource(R.string.vault_move_title, selectedItems.size)) },
            text = { Text(stringResource(R.string.vault_move_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmVault = false
                    viewModel.moveToVault(selectedItems)
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
}

@Composable
private fun PhotosTopBar(
    viewModel: GalleryViewModel,
    selectionCount: Int,
    onClearSelection: () -> Unit,
    onAddToAlbum: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalLumaPalette.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    if (selectionCount > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = stringResource(R.string.selected_count, selectionCount),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddToAlbum) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_to_album),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LumaWordmark(
            title = "Luma",
            subtitle = stringResource(R.string.photos_count, viewModel.items.size),
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(palette.card)
                    .clickable {
                        val next = when (viewModel.gridColumns) {
                            3 -> 4
                            4 -> 5
                            5 -> 6
                            else -> 3
                        }
                        viewModel.updateGridColumns(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.gridColumns.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(palette.card)
                    .clickable { viewModel.refresh(force = true) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
