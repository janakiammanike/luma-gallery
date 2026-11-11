package com.luma.gallery.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.gallery.R
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.components.EmptyState
import com.luma.gallery.ui.components.MediaGrid
import com.luma.gallery.ui.theme.LocalLumaPalette

@Composable
fun TrashScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val palette = LocalLumaPalette.current
    val items = viewModel.trashItems
    val selection = viewModel.selectedIds
    var confirmPurge by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.clearSelection()
        viewModel.refreshTrash(force = true)
    }

    val selectedItems = items.filter { it.id in selection }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
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
                        text = stringResource(R.string.trash_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (items.isEmpty()) {
                            stringResource(R.string.trash_note)
                        } else {
                            stringResource(R.string.photos_count, items.size)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted
                    )
                }
            } else {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(R.string.selected_count, selection.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.restoreFromTrash(selection) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = stringResource(R.string.action_restore),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { confirmPurge = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete_forever),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        when {
            !viewModel.trashLoaded && items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            items.isEmpty() -> EmptyState(
                title = stringResource(R.string.trash_empty),
                hint = stringResource(R.string.trash_empty_hint)
            )

            else -> MediaGrid(
                items = items,
                columns = viewModel.gridColumns,
                selection = selection,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 40.dp),
                onOpen = { index -> viewModel.toggleSelection(items[index].id) },
                onLongPress = { viewModel.toggleSelection(it.id) }
            )
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text(stringResource(R.string.delete_forever_title)) },
            text = { Text(stringResource(R.string.delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurge = false
                    viewModel.deleteFromTrashForever(selection)
                }) {
                    Text(
                        stringResource(R.string.action_delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
