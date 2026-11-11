package com.luma.gallery.ui.search

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.gallery.R
import com.luma.gallery.data.MediaItem
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.components.EmptyState
import com.luma.gallery.ui.components.LumaChip
import com.luma.gallery.ui.components.MediaGrid
import com.luma.gallery.ui.theme.LocalLumaPalette
import java.util.Locale

private enum class TypeFilter { All, Images, Videos }

@Composable
fun SearchScreen(
    viewModel: GalleryViewModel,
    onOpen: (List<MediaItem>, Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filter = TypeFilter.entries[filterIndex]
    val palette = LocalLumaPalette.current

    val results = remember(query, filter, viewModel.items) {
        val needle = query.trim().lowercase(Locale.ROOT)
        viewModel.items.filter { item ->
            val typeMatch = when (filter) {
                TypeFilter.All -> true
                TypeFilter.Images -> !item.isVideo
                TypeFilter.Videos -> item.isVideo
            }
            val textMatch = needle.isEmpty() || listOf(
                item.name,
                item.bucketName,
                item.relativePath,
                item.mimeType
            ).any { it.lowercase(Locale.ROOT).contains(needle) }
            typeMatch && textMatch
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                )
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.tab_search),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = palette.muted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.card,
                    unfocusedContainerColor = palette.card,
                    disabledContainerColor = palette.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val labels = listOf(
                    stringResource(R.string.filter_all),
                    stringResource(R.string.filter_images),
                    stringResource(R.string.filter_videos)
                )
                labels.forEachIndexed { index, label ->
                    LumaChip(
                        text = label,
                        selected = filterIndex == index,
                        onClick = { filterIndex = index }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }

        when {
            results.isEmpty() && query.isNotBlank() -> EmptyState(
                title = stringResource(R.string.search_empty),
                hint = stringResource(R.string.search_hint)
            )

            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CountBadge(
                    text = stringResource(R.string.empty_gallery),
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> MediaGrid(
                items = results,
                columns = viewModel.gridColumns,
                selection = viewModel.selectedIds,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 132.dp),
                showDateHeaders = false,
                onOpen = { index -> onOpen(results, index) },
                onLongPress = { viewModel.toggleSelection(it.id) }
            )
        }
    }
}

@Composable
private fun CountBadge(text: String, modifier: Modifier = Modifier) {
    val palette = LocalLumaPalette.current
    Box(
        modifier
            .clip(CircleShape)
            .background(palette.card)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted
        )
    }
}
