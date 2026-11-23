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

    fun airMouseEnabled(): Boolean = prefs.getBoolean(KEY_AIR_MOUSE_ENABLED, false)

    fun setAirMouseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AIR_MOUSE_ENABLED, enabled).apply()
    }

    fun airMouseSensitivity(): Float = prefs.getFloat(KEY_AIR_MOUSE_SENSITIVITY, 1f)

    fun setAirMouseSensitivity(value: Float) {
        prefs.edit().putFloat(KEY_AIR_MOUSE_SENSITIVITY, value).apply()
    }

    fun airMouseInvertX(): Boolean = prefs.getBoolean(KEY_AIR_MOUSE_INVERT_X, false)

    fun airMouseInvertY(): Boolean = prefs.getBoolean(KEY_AIR_MOUSE_INVERT_Y, false)

    fun setAirMouseInvert(x: Boolean, y: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AIR_MOUSE_INVERT_X, x)
            .putBoolean(KEY_AIR_MOUSE_INVERT_Y, y)
            .apply()
    }

    fun airGainX(): Float = prefs.getFloat(KEY_AIR_GAIN_X, 1f)

    fun airGainY(): Float = prefs.getFloat(KEY_AIR_GAIN_Y, 1f)

    fun airSpanX(): Int = prefs.getInt(KEY_AIR_SPAN_X, 0)

    fun airSpanY(): Int = prefs.getInt(KEY_AIR_SPAN_Y, 0)

    fun airAxisSignX(): Int = prefs.getInt(KEY_AIR_AXIS_SIGN_X, 1)

    fun airAxisSignY(): Int = prefs.getInt(KEY_AIR_AXIS_SIGN_Y, 1)

    fun shakeToCenter(): Boolean = prefs.getBoolean(KEY_SHAKE_TO_CENTER, false)

    fun setShakeToCenter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_TO_CENTER, enabled).apply()
    }

    /** Saves everything the guided calibration produces. */
    fun saveAirCalibration(
        gainX: Float,
        gainY: Float,
        spanX: Int,
        spanY: Int,
        signX: Int,
        signY: Int
    ) {
        prefs.edit()
            .putFloat(KEY_AIR_GAIN_X, gainX)
            .putFloat(KEY_AIR_GAIN_Y, gainY)
            .putInt(KEY_AIR_SPAN_X, spanX)
            .putInt(KEY_AIR_SPAN_Y, spanY)
            .putInt(KEY_AIR_AXIS_SIGN_X, signX)
            .putInt(KEY_AIR_AXIS_SIGN_Y, signY)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "luma_tv_remote_2_secure"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_HAPTICS = "haptics"
        const val KEY_AIR_MOUSE_ENABLED = "air_mouse_enabled"
        const val KEY_AIR_MOUSE_SENSITIVITY = "air_mouse_sensitivity"
        const val KEY_AIR_MOUSE_INVERT_X = "air_mouse_invert_x"
        const val KEY_AIR_MOUSE_INVERT_Y = "air_mouse_invert_y"
        const val KEY_AIR_GAIN_X = "air_gain_x"
        const val KEY_AIR_GAIN_Y = "air_gain_y"
        const val KEY_AIR_SPAN_X = "air_span_x"
        const val KEY_AIR_SPAN_Y = "air_span_y"
        const val KEY_AIR_AXIS_SIGN_X = "air_axis_sign_x"
        const val KEY_AIR_AXIS_SIGN_Y = "air_axis_sign_y"
        const val KEY_SHAKE_TO_CENTER = "shake_to_center"
    }
}
