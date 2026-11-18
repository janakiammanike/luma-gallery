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
        prefs.edit().putStringSet(KEY_HOSTS, savedHosts() - host.trim()).apply()
        prefs.edit().remove("key:${host.trim()}").apply()
    }

    fun lastHost(): String? = prefs.getString(KEY_LAST_HOST, null)

    fun setLastHost(host: String) {
        prefs.edit().putString(KEY_LAST_HOST, host.trim()).apply()
    }

    fun keyFor(host: String): String? = prefs.getString("key:${host.trim()}", null)

    fun saveKey(host: String, key: String) {
        prefs.edit().putString("key:${host.trim()}", key).apply()
    }

    private companion object {
        const val KEY_HOSTS = "hosts"
        const val KEY_LAST_HOST = "last_host"
    }
}
