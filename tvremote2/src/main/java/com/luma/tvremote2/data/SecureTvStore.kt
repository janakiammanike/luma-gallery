package com.luma.tvremote2.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed storage for the new app.
 *
 * Legacy TvStore kept pairing client-keys in plain SharedPreferences. In 2.0
 * every value lives in EncryptedSharedPreferences whose master key is held by
 * the Android Keystore, so no credential is ever written as plain text.
 *
 * Fresh pairing is intentional: the new app requests a new client-key from the
 * TV instead of importing the old one.
 */
class SecureTvStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun lastHost(): String? = prefs.getString(KEY_LAST_HOST, null)

    fun setLastHost(host: String) {
        prefs.edit().putString(KEY_LAST_HOST, host.trim()).apply()
    }

    /** Pairing keys are encrypted at rest, keyed by TV host. */
    fun clientKeyFor(host: String): String? = prefs.getString("key:${host.trim()}", null)

    fun saveClientKey(host: String, key: String) {
        prefs.edit().putString("key:${host.trim()}", key).apply()
    }

    fun clearClientKey(host: String) {
        prefs.edit().remove("key:${host.trim()}").apply()
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
        const val PREFS_NAME = "luma_tv_remote_2_secure"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_HAPTICS = "haptics"
    }
}
