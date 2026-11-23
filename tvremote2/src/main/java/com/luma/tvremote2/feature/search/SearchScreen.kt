package com.luma.tvremote2.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaButton
import com.luma.tvremote2.designsystem.LumaTextField
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun SearchScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        SectionCard("Content search") {
            Text(
                "Opens the TV's own search UI. Not a cloud-wide content index.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            LumaTextField(
                value = state.searchText,
                onValueChange = vm::onSearchText,
                label = "Search on TV",
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(LumaTokens.IconSmall)
                    )
                }
            )
            LumaButton(
                text = "Search TV",
                onClick = vm::searchTv,
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
            )
            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                    maxLines = 2
                )
            }
        }
    }
}
