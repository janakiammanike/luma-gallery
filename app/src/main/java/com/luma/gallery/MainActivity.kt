package com.luma.gallery

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.luma.gallery.data.Album
import com.luma.gallery.data.MediaPermissions
import com.luma.gallery.ui.GalleryViewModel
import com.luma.gallery.ui.albums.AlbumDetailScreen
import com.luma.gallery.ui.albums.AlbumsScreen
import com.luma.gallery.ui.components.AmbientGlow
import com.luma.gallery.ui.components.FloatingPill
import com.luma.gallery.ui.onboarding.PermissionScreen
import com.luma.gallery.ui.photos.PhotosScreen
import com.luma.gallery.ui.search.SearchScreen
import com.luma.gallery.ui.settings.SettingsScreen
import com.luma.gallery.ui.theme.LocalLumaPalette
import com.luma.gallery.ui.theme.LumaTheme
import com.luma.gallery.ui.trash.TrashScreen
import com.luma.gallery.ui.vault.VaultScreen
import com.luma.gallery.ui.viewer.ViewerRequest
import com.luma.gallery.ui.viewer.ViewerScreen
import com.luma.gallery.web.WebService
import java.util.Locale

class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val tag = newBase
            .getSharedPreferences(GalleryViewModel.PREFS, Context.MODE_PRIVATE)
            .getString(GalleryViewModel.KEY_LANGUAGE, null)
        super.attachBaseContext(
            if (tag.isNullOrBlank()) newBase else newBase.withLocale(tag)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WebService.ensureRunning(this)
        applyHighRefreshRate()
        setContent {
            val viewModel: GalleryViewModel = viewModel()
            LumaTheme(
                mode = viewModel.themeMode,
                accent = viewModel.accent,
                customAccent = viewModel.customAccentArgb
            ) {
                LumaRoot(viewModel)
            }
        }
    }

    /**
     * Phones often default to 60 Hz for apps they don't recognise. Pick the highest
     * refresh mode that keeps the current resolution so scrolling and pager gestures
     * run at the panel's native rate (90/120/144 Hz).
     */
    private fun applyHighRefreshRate() {
        runCatching {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val current = display.mode
            val sameResolution = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            val fastest = (sameResolution.ifEmpty { display.supportedModes.toList() })
                .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = fastest.modeId
                preferredRefreshRate = fastest.refreshRate
            }
        }
    }
}

private fun Context.withLocale(tag: String): Context {
    val locale = Locale.forLanguageTag(tag)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}

@Composable
private fun LumaRoot(viewModel: GalleryViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(MediaPermissions.hasAccess(context)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var showTrash by remember { mutableStateOf(false) }
    var showVault by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = MediaPermissions.hasAccess(context)
        viewModel.refreshAllFilesAccess()
        viewModel.onScreenResumed()
        if (granted && !viewModel.loadedOnce) viewModel.refresh(force = true)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.lockVault()
    }

    if (!granted) {
        PermissionScreen(onGranted = {
            granted = true
            viewModel.refresh(force = true)
        })
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientGlow(Modifier.align(Alignment.TopCenter))

        when (tab) {
            0 -> PhotosScreen(
                viewModel = viewModel,
                onOpen = { list, index -> viewer = ViewerRequest(list, index) }
            )
            1 -> AlbumsScreen(
                viewModel = viewModel,
                onOpenAlbum = { openAlbum = it },
                onOpenTrash = {
                    viewModel.clearSelection()
                    showTrash = true
                },
                onOpenVault = {
                    viewModel.clearSelection()
                    showVault = true
                }
            )
            2 -> SearchScreen(
                viewModel = viewModel,
                onOpen = { list, index -> viewer = ViewerRequest(list, index) }
            )
            else -> SettingsScreen(viewModel)
        }

        LumaBottomBar(
            tab = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        )
    }

    val album = openAlbum
    if (album != null) {
        AlbumDetailScreen(
            viewModel = viewModel,
            album = album,
            onBack = { openAlbum = null },
            onOpen = { list, index -> viewer = ViewerRequest(list, index) }
        )
    }

    if (showTrash) {
        TrashScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.clearSelection()
                showTrash = false
            }
        )
    }

    if (showVault) {
        VaultScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.clearSelection()
                showVault = false
            }
        )
    }

    val request = viewer
    if (request != null) {
        ViewerScreen(
            viewModel = viewModel,
            request = request,
            onClose = { viewer = null }
        )
    }
}

@Composable
private fun LumaBottomBar(
    tab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumaPalette.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val labels = listOf(
        stringResource(R.string.tab_photos),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_search),
        stringResource(R.string.tab_settings)
    )

    FloatingPill(modifier.padding(bottom = bottomInset + 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = tab == index
                Column(
                    modifier = Modifier
                        .width(78.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TabIcon(
                        index = index,
                        tint = if (selected) MaterialTheme.colorScheme.primary else palette.muted
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else palette.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(index: Int, tint: Color) {
    when (index) {
        0 -> Icon(painterResource(R.drawable.ic_photos), null, tint = tint, modifier = Modifier.size(21.dp))
        1 -> Icon(painterResource(R.drawable.ic_albums), null, tint = tint, modifier = Modifier.size(21.dp))
        2 -> Icon(Icons.Filled.Search, null, tint = tint, modifier = Modifier.size(21.dp))
        else -> Icon(Icons.Filled.Settings, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}
