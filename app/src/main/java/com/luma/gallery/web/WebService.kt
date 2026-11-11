package com.luma.gallery.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.luma.gallery.MainActivity
import com.luma.gallery.R
import fi.iki.elonen.NanoHTTPD

/**
 * Keeps the LAN web server alive while the phone screen is off or the app is in the
 * background, so a TV can keep playing.
 */
class WebService : Service() {

    private var server: WebServer? = null
    private var starting = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        val port = WebPrefs.port(this)
        if (server != null) {
            publish(true, port)
            return
        }
        if (starting) return
        starting = true
        WebPrefs.setEnabled(this, true)
        WebBridge.publish(
            WebBridge.Status(
                starting = true,
                port = port,
                pin = WebPrefs.pin(this),
                urls = WebNet.urls(port)
            )
        )
        updateForeground(starting = true, port = port)

        Thread {
            try {
                val instance = WebServer(applicationContext, port)
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = instance
                acquireLocks()
                starting = false
                publish(true, port)
                updateForeground(starting = false, port = port)
            } catch (t: Throwable) {
                android.util.Log.e("LumaWeb", "Server start failed", t)
                starting = false
                server = null
                WebPrefs.setEnabled(this, false)
                WebBridge.publish(
                    WebBridge.Status(
                        running = false,
                        starting = false,
                        port = port,
                        pin = WebPrefs.pin(this),
                        urls = WebNet.urls(port),
                        error = t.message ?: "Could not start the web server"
                    )
                )
                updateForeground(starting = false, port = port, failed = true)
                stopSelf()
            }
        }.apply { name = "luma-web-start"; isDaemon = true }.start()
    }

    private fun stopEverything() {
        runCatching { server?.stop() }
        server = null
        starting = false
        releaseLocks()
        WebPrefs.setEnabled(this, false)
        WebBridge.publish(
            WebBridge.Status(
                running = false,
                port = WebPrefs.port(this),
                pin = WebPrefs.pin(this)
            )
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish(running: Boolean, port: Int) {
        WebBridge.publish(
            WebBridge.Status(
                running = running,
                starting = false,
                port = port,
                pin = WebPrefs.pin(this),
                urls = WebNet.urls(port)
            )
        )
    }

    // ----- notification ----------------------------------------------------

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Web & TV access",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the local photo gallery reachable from your TV and PC"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateForeground(starting: Boolean, port: Int, failed: Boolean = false) {
        val notification = buildNotification(starting, port, failed)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    private fun buildNotification(
        starting: Boolean,
        port: Int,
        failed: Boolean = false
    ): Notification {
        val urls = WebNet.urls(port)
        val title = when {
            failed -> getString(R.string.web_failed)
            starting -> getString(R.string.web_starting)
            else -> getString(R.string.web_on)
        }
        val text = when {
            failed -> "Port $port"
            urls.isNotEmpty() -> urls.first()
            else -> getString(R.string.web_wifi_hint)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WebService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_photos)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.web_off), stopIntent)
            .build()
    }

    // ----- locks -----------------------------------------------------------

    private fun acquireLocks() {
        runCatching {
            if (wifiLock == null) {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifi?.createWifiLock(mode, "luma:web")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
        runCatching {
            if (wakeLock == null) {
                val power = getSystemService(PowerManager::class.java)
                wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "luma:web-server")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        runCatching { server?.stop() }
        server = null
        starting = false
        releaseLocks()
        if (!WebPrefs.isEnabled(this)) {
            WebBridge.publish(
                WebBridge.Status(
                    running = false,
                    port = WebPrefs.port(this),
                    pin = WebPrefs.pin(this)
                )
            )
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.luma.gallery.web.action.START"
        const val ACTION_STOP = "com.luma.gallery.web.action.STOP"

        private const val CHANNEL_ID = "luma_web"
        private const val NOTIFICATION_ID = 4211

        fun start(context: Context) {
            WebPrefs.setEnabled(context, true)
            val intent = Intent(context, WebService::class.java).setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            WebPrefs.setEnabled(context, false)
            val intent = Intent(context, WebService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
            WebBridge.publish(
                WebBridge.Status(
                    running = false,
                    port = WebPrefs.port(context),
                    pin = WebPrefs.pin(context)
                )
            )
        }

        /** Restarts the server after the app process was recreated. */
        fun ensureRunning(context: Context) {
            if (WebPrefs.isEnabled(context)) start(context)
        }
    }
}
