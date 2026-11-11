package com.luma.gallery.ui

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.gallery.R
import com.luma.gallery.data.Album
import com.luma.gallery.data.AlbumBuilder
import com.luma.gallery.data.CustomAlbum
import com.luma.gallery.data.CustomAlbumStore
import com.luma.gallery.data.MediaItem
import com.luma.gallery.data.MediaRepository
import com.luma.gallery.data.TrashEntry
import com.luma.gallery.data.TrashStore
import com.luma.gallery.data.VaultEntry
import com.luma.gallery.data.VaultStore
import com.luma.gallery.ui.theme.Accent
import com.luma.gallery.ui.theme.ThemeMode
import com.luma.gallery.web.WebBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    /** Includes Live Photo clips, which [items] hides from every grid and album. */
    private var indexedItems: List<MediaItem> = emptyList()

    /** The clip that belongs to a Live Photo still, if it is still on the device. */
    fun clipFor(id: Long): MediaItem? = indexedItems.firstOrNull { it.id == id }

    /** Keeps a Live Photo's still and clip together when moving it out of the gallery. */
    private fun withLiveClips(selected: List<MediaItem>): List<MediaItem> =
        selected
            .flatMap { item ->
                val clip = item.liveVideoId?.let { clipFor(it) }
                if (clip != null) listOf(item, clip) else listOf(item)
            }
            .distinctBy { it.id }

    private fun List<MediaItem>.withoutLiveClips(): List<MediaItem> {
        val clipIds = mapNotNullTo(HashSet()) { it.liveVideoId }
        return if (clipIds.isEmpty()) this else filter { it.id !in clipIds }
    }

    var isLoading by mutableStateOf(false)
        private set

    var loadedOnce by mutableStateOf(false)
        private set

    var favourites by mutableStateOf(readFavourites())
        private set

    var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var customAlbums by mutableStateOf(CustomAlbumStore.load(prefs))
        private set

    var trashItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    private var trashEntries by mutableStateOf<List<TrashEntry>>(emptyList())

    var trashLoaded by mutableStateOf(false)
        private set

    val trashSupported: Boolean
        get() = true

    var vaultItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    private var vaultEntries by mutableStateOf<List<VaultEntry>>(emptyList())

    var vaultLoaded by mutableStateOf(false)
        private set

    var vaultUnlocked by mutableStateOf(false)
        private set

    var gridColumns by mutableIntStateOf(prefs.getInt(KEY_COLUMNS, 4))
        private set

    var accent by mutableStateOf(readAccent())
        private set

    var customAccentArgb by mutableIntStateOf(
        prefs.getInt(KEY_ACCENT_CUSTOM, 0xFFB49CFF.toInt())
    )
        private set

    var themeMode by mutableStateOf(readThemeMode())
        private set

    var languageTag by mutableStateOf(prefs.getString(KEY_LANGUAGE, null))
        private set

    var hasAllFilesAccess by mutableStateOf(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    )
        private set

    private var lastLoadedAt = 0L
    private var pendingRefresh = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            pendingRefresh = true
        }
    }

    init {
        runCatching {
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                observer
            )
        }
        reloadTrash()
        reloadVault()
        viewModelScope.launch {
            TrashStore.purgeExpired(getApplication(), prefs)
            reloadTrash()
        }
        viewModelScope.launch {
            WebBridge.libraryChanges.collect {
                pendingRefresh = false
                refresh(force = true)
            }
        }
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        if (isLoading) return
        if (!force && lastLoadedAt > 0 && System.currentTimeMillis() - lastLoadedAt < 15_000) {
            pendingRefresh = false
            return
        }
        viewModelScope.launch {
            isLoading = true
            pendingRefresh = false
            val loaded = repository.loadAll(includeUnindexed = hasAllFilesAccess)
            indexedItems = loaded
            items = loaded.withoutLiveClips()
            lastLoadedAt = System.currentTimeMillis()
            loadedOnce = true
            isLoading = false
        }
    }

    fun onScreenResumed() {
        if (pendingRefresh) refresh(force = true)
    }

    fun smartAlbums(): List<Album> = AlbumBuilder.smartAlbums(items, favourites)

    fun folderAlbums(): List<Album> = AlbumBuilder.folderAlbums(items)

    fun customAlbumModels(): List<Album> = customAlbums.map { custom ->
        val matched = items.filter { it.id in custom.ids }
        Album(
            key = "custom:${custom.id}",
            title = custom.name,
            icon = com.luma.gallery.data.AlbumIcon.Folder,
            count = matched.size,
            cover = matched.maxByOrNull { it.dateMillis },
            customId = custom.id,
            itemIds = custom.ids.toSet()
        )
    }

    // ----- custom albums ---------------------------------------------------

    fun createCustomAlbum(name: String, ids: Collection<Long> = emptyList()): Album? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        val album = CustomAlbum(
            id = UUID.randomUUID().toString(),
            name = clean,
            ids = ids.toList()
        )
        customAlbums = customAlbums + album
        CustomAlbumStore.save(prefs, customAlbums)
        return customAlbumModels().firstOrNull { it.customId == album.id }
    }

    fun renameCustomAlbum(customId: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        customAlbums = customAlbums.map {
            if (it.id == customId) it.copy(name = clean) else it
        }
        CustomAlbumStore.save(prefs, customAlbums)
    }

    fun deleteCustomAlbum(customId: String) {
        customAlbums = customAlbums.filterNot { it.id == customId }
        CustomAlbumStore.save(prefs, customAlbums)
    }

    /** Adds ids to an album, ignoring duplicates. Returns the album's new item count. */
    fun addToCustomAlbum(customId: String, ids: Collection<Long>): Int {
        var count = 0
        customAlbums = customAlbums.map { album ->
            if (album.id == customId) {
                val merged = (album.ids + ids).distinct()
                count = merged.size
                album.copy(ids = merged)
            } else {
                album
            }
        }
        CustomAlbumStore.save(prefs, customAlbums)
        return count
    }

    fun removeFromCustomAlbum(customId: String, ids: Collection<Long>) {
        val removed = ids.toSet()
        customAlbums = customAlbums.map { album ->
            if (album.id == customId) album.copy(ids = album.ids.filterNot { it in removed })
            else album
        }
        CustomAlbumStore.save(prefs, customAlbums)
    }

    // ----- trash -----------------------------------------------------------

    fun refreshTrash(force: Boolean = false) {
        reloadTrash()
    }

    private fun reloadTrash() {
        val app = getApplication<Application>()
        trashEntries = TrashStore.load(prefs)
        trashItems = trashEntries.map { TrashStore.toMediaItem(app, it) }
        trashLoaded = true
    }

    /** Moves items into Luma's trash, then removes them from the gallery. */
    fun moveToTrash(selected: List<MediaItem>) {
        if (selected.isEmpty()) return
        val items = withLiveClips(selected)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val entries = TrashStore.moveToTrash(app, prefs, items)
            var kept = 0

            withContext(Dispatchers.IO) {
                entries.forEach { entry ->
                    val item = items.firstOrNull { it.id == entry.originalId }
                    val deleted = item != null && runCatching {
                        resolver.delete(item.uri, null, null) > 0
                    }.getOrDefault(false)

                    if (deleted) {
                        kept++
                    } else {
                        // The copy is only useful if the original is actually gone.
                        TrashStore.removeEntry(app, prefs, entry)
                    }
                }
            }

            clearSelection()
            reloadTrash()
            refresh(force = true)
            if (kept > 0) {
                Toast.makeText(app, R.string.trash_moved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restoreFromTrash(ids: Set<Long>) {
        val entries = trashEntries.filter { it.mediaId in ids }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val restored = TrashStore.restore(app, prefs, entries)
            clearSelection()
            reloadTrash()
            refresh(force = true)
            if (restored > 0) {
                Toast.makeText(app, R.string.trash_restored, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteFromTrashForever(ids: Set<Long>) {
        val entries = trashEntries.filter { it.mediaId in ids }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) { TrashStore.remove(app, prefs, entries) }
            clearSelection()
            reloadTrash()
            Toast.makeText(app, R.string.trash_purged, Toast.LENGTH_SHORT).show()
        }
    }

    fun albumItems(album: Album): List<MediaItem> =
        AlbumBuilder.itemsFor(album, items, favourites)

    // ----- vault -----------------------------------------------------------

    fun refreshVault() {
        reloadVault()
    }

    private fun reloadVault() {
        val app = getApplication<Application>()
        VaultStore.pruneMissing(app, prefs)
        vaultEntries = VaultStore.load(prefs)
        vaultItems = vaultEntries.map { VaultStore.toMediaItem(app, it) }
        vaultLoaded = true
    }

    fun lockVault() {
        vaultUnlocked = false
        clearSelection()
    }

    fun unlockVault() {
        vaultUnlocked = true
    }

    /** Moves items into the private vault, then removes them from the gallery. */
    fun moveToVault(selected: List<MediaItem>) {
        if (selected.isEmpty()) return
        val items = withLiveClips(selected)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val entries = VaultStore.moveToVault(app, prefs, items)
            var kept = 0

            withContext(Dispatchers.IO) {
                entries.forEach { entry ->
                    val item = items.firstOrNull { it.id == entry.originalId }
                    val deleted = item != null && runCatching {
                        resolver.delete(item.uri, null, null) > 0
                    }.getOrDefault(false)

                    if (deleted) {
                        kept++
                    } else {
                        VaultStore.removeEntry(app, prefs, entry)
                    }
                }
            }

            clearSelection()
            reloadVault()
            refresh(force = true)
            if (kept > 0) {
                Toast.makeText(app, R.string.vault_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restoreFromVault(ids: Set<Long>) {
        val entries = vaultEntries.filter { it.mediaId in ids }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val restored = VaultStore.restore(app, prefs, entries)
            clearSelection()
            reloadVault()
            refresh(force = true)
            if (restored > 0) {
                Toast.makeText(app, R.string.vault_restored, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteFromVaultForever(ids: Set<Long>) {
        val entries = vaultEntries.filter { it.mediaId in ids }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) { VaultStore.remove(app, prefs, entries) }
            clearSelection()
            reloadVault()
            Toast.makeText(app, R.string.trash_purged, Toast.LENGTH_SHORT).show()
        }
    }

    fun itemsByIds(ids: Set<Long>): List<MediaItem> =
        items.filter { it.id in ids }

    fun albumFor(item: MediaItem): Album? = folderAlbums().firstOrNull { it.bucketId == item.bucketId }

    // ----- selection -------------------------------------------------------

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun selectAll(ids: Collection<Long>) {
        selectedIds = ids.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    // ----- favourites ------------------------------------------------------

    fun isFavourite(id: Long): Boolean = id in favourites

    fun toggleFavourite(id: Long) {
        favourites = if (id in favourites) favourites - id else favourites + id
        prefs.edit().putStringSet(KEY_FAVOURITES, favourites.map { it.toString() }.toSet()).apply()
    }

    // ----- settings --------------------------------------------------------

    fun updateGridColumns(columns: Int) {
        gridColumns = columns.coerceIn(3, 6)
        prefs.edit().putInt(KEY_COLUMNS, gridColumns).apply()
    }

    fun updateAccent(value: Accent) {
        accent = value
        prefs.edit().putString(KEY_ACCENT, value.name).apply()
    }

    fun updateCustomAccent(argb: Int) {
        customAccentArgb = argb
        accent = Accent.Custom
        prefs.edit()
            .putInt(KEY_ACCENT_CUSTOM, argb)
            .putString(KEY_ACCENT, Accent.Custom.name)
            .apply()
    }

    fun updateThemeMode(value: ThemeMode) {
        themeMode = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    fun setLanguage(tag: String?) {
        languageTag = tag
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val manager = getApplication<Application>()
                    .getSystemService(LocaleManager::class.java)
                manager.applicationLocales = if (tag == null) {
                    android.os.LocaleList.getEmptyLocaleList()
                } else {
                    android.os.LocaleList.forLanguageTags(tag)
                }
            }
        }
    }

    fun refreshAllFilesAccess() {
        hasAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
    }

    fun deleteUri(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.delete(uri, null, null)
        }
        refresh(force = true)
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        }
        super.onCleared()
    }

    private fun readFavourites(): Set<Long> =
        prefs.getStringSet(KEY_FAVOURITES, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    private fun readAccent(): Accent =
        runCatching { Accent.valueOf(prefs.getString(KEY_ACCENT, Accent.Violet.name)!!) }
            .getOrDefault(Accent.Violet)

    private fun readThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.Dark.name)!!) }
            .getOrDefault(ThemeMode.Dark)

    companion object {
        const val PREFS = "luma_prefs"
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_COLUMNS = "grid_columns"
        private const val KEY_ACCENT = "accent"
        private const val KEY_ACCENT_CUSTOM = "accent_custom"
        private const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
    }
}
