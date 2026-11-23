package com.luma.tvremote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Fetches TV app icons from the TV's own HTTPS endpoint (self-signed cert),
 * with a small in-memory cache so re-scrolling the list is instant.
 */
object AppIconLoader {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    private val client: OkHttpClient by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /** Returns null when the icon can't be loaded; caller shows a text fallback. */
    suspend fun load(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) cache[url] = bitmap
                    bitmap
                }
            } catch (e: Exception) {
                Log.d("LumaIcons", "failed to load $url: ${e.message}")
                null
            }
        }
    }
}
