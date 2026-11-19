package com.luma.tvremote.data

import android.content.Context

class TvStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("luma_tv_remote", Context.MODE_PRIVATE)

    fun savedHosts(): Set<String> =
        prefs.getStringSet(KEY_HOSTS, emptySet()) ?: emptySet()

    fun addHost(host: String) {
        if (host.isBlank()) return
        prefs.edit().putStringSet(KEY_HOSTS, savedHosts() + host.trim()).apply()
    }

    fun removeHost(host: String) {
        val key = host.trim()
        prefs.edit()
            .putStringSet(KEY_HOSTS, savedHosts() - key)
            .putStringSet(KEY_FAVORITES, favorites() - key)
            .remove("key:$key")
            .remove("mac:$key")
            .remove("layout:$key")
            .apply()
    }

    fun lastHost(): String? = prefs.getString(KEY_LAST_HOST, null)

    fun setLastHost(host: String) {
        prefs.edit().putString(KEY_LAST_HOST, host.trim()).apply()
    }

    fun keyFor(host: String): String? = prefs.getString("key:${host.trim()}", null)

    fun saveKey(host: String, key: String) {
        prefs.edit().putString("key:${host.trim()}", key).apply()
    }

    fun macFor(host: String): String? = prefs.getString("mac:${host.trim()}", null)

    fun saveMac(host: String, mac: String) {
        prefs.edit().putString("mac:${host.trim()}", mac).apply()
    }

    fun favorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun isFavorite(host: String): Boolean = host.trim() in favorites()

    fun toggleFavorite(host: String) {
        val key = host.trim()
        val next = if (isFavorite(key)) favorites() - key else favorites() + key
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    /** Returns null when the host has never been customised (all groups visible). */
    fun buttonLayout(host: String): Set<String>? {
        val raw = prefs.getString("layout:${host.trim()}", null) ?: return null
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    fun saveButtonLayout(host: String, groups: Set<String>) {
        prefs.edit()
            .putString("layout:${host.trim()}", groups.sorted().joinToString(","))
            .apply()
    }

    fun clearButtonLayout(host: String) {
        prefs.edit().remove("layout:${host.trim()}").apply()
    }

    fun recentSearches(): List<String> =
        prefs.getString(KEY_RECENT_SEARCHES, null)
            ?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val next = (listOf(q) + recentSearches().filter { it != q }).take(10)
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString("|")).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    /** Recently launched TV apps as "id|title" strings, newest first. */
    fun recentApps(): List<Pair<String, String>> =
        prefs.getString(KEY_RECENT_APPS, null)
            ?.split('|')
            ?.mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            ?.take(8) ?: emptyList()

    fun addRecentApp(id: String, title: String) {
        val entry = "$id=$title"
        val next = (listOf(entry) + recentApps().map { "${it.first}=${it.second}" }
            .filter { !it.startsWith("$id=") }).take(8)
        prefs.edit().putString(KEY_RECENT_APPS, next.joinToString("|")).apply()
    }

    fun clearRecentApps() {
        prefs.edit().remove(KEY_RECENT_APPS).apply()
    }

    fun darkTheme(): Boolean = prefs.getBoolean(KEY_DARK_THEME, false)

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun haptics(): Boolean = prefs.getBoolean(KEY_HAPTICS, true)

    fun setHaptics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    private companion object {
        const val KEY_HOSTS = "hosts"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_RECENT_APPS = "recent_apps"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_HAPTICS = "haptics"
    }
}
