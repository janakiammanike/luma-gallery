package com.luma.gallery.web

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/** Persisted settings for the local-network web server. */
object WebPrefs {

    private const val PREFS = "luma_web"
    private const val KEY_PIN = "pin"
    private const val KEY_PORT = "port"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SESSION = "session_token"

    const val DEFAULT_PORT = 8787

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pin(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_PIN, null)?.let { if (it.length >= 4) return it }
        return newPin(context)
    }

    fun newPin(context: Context): String {
        val fresh = generatePin()
        prefs(context).edit().putString(KEY_PIN, fresh).apply()
        rotateSession(context)
        return fresh
    }

    fun port(context: Context): Int = prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Stable browser cookie value. Keeping it in prefs means a TV does not have to type
     * the PIN again after the phone reboots; changing the PIN still invalidates nothing
     * on its own, so [newPin] also rotates this token.
     */
    fun sessionToken(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_SESSION, null)?.let { if (it.length >= 32) return it }
        return rotateSession(context)
    }

    fun rotateSession(context: Context): String {
        val random = SecureRandom()
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(KEY_SESSION, token).apply()
        return token
    }

    private fun generatePin(): String {
        val random = SecureRandom()
        return buildString(6) {
            repeat(6) { append(random.nextInt(10)) }
        }
    }
}
