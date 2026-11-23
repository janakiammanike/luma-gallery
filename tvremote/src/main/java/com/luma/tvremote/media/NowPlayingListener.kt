package com.luma.tvremote.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val appPackage: String?,
    val isPlaying: Boolean
) {
    /** Query string used to hand the track off to a TV app's search. */
    val searchQuery: String
        get() = listOf(title, artist).filter { it.isNotBlank() }.distinct().joinToString(" ")
}

/** Reads the phone's active media session so the app can hand the current song to the TV. */
object NowPlayingStore {
    private val _track = MutableStateFlow<NowPlaying?>(null)
    val track: StateFlow<NowPlaying?> = _track.asStateFlow()

    fun update(track: NowPlaying?) {
        _track.value = track
    }

    /** Read the currently-playing session on demand. Needs notification-listener access. */
    fun readCurrent(context: Context) {
        if (!isAccessGranted(context)) {
            update(null)
            return
        }
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return update(null)
            val self = ComponentName(context, NowPlayingListener::class.java)
            val controllers: List<MediaController> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    manager.getActiveSessions(self)
                } else {
                    emptyList()
                }
            val active = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            if (active == null) {
                update(null)
                return
            }
            val metadata = active.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            if (title.isBlank()) {
                update(null)
                return
            }
            update(
                NowPlaying(
                    title = title,
                    artist = artist,
                    appPackage = active.packageName,
                    isPlaying = true
                )
            )
        } catch (_: SecurityException) {
            update(null)
        } catch (_: Exception) {
            update(null)
        }
    }

    fun isAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val self = ComponentName(context, NowPlayingListener::class.java).flattenToString()
        return flat.contains(self)
    }
}

/**
 * Notification listener used to (a) read media sessions and (b) keep the
 * current track fresh via MediaController callbacks as songs change.
 */
class NowPlayingListener : NotificationListenerService() {

    private var callbacks: MediaController.Callback? = null
    private var boundControllers = emptyList<MediaController>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        registerCallbacks()
        NowPlayingStore.readCurrent(applicationContext)
    }

    override fun onListenerDisconnected() {
        unregisterCallbacks()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        NowPlayingStore.readCurrent(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NowPlayingStore.readCurrent(applicationContext)
    }

    private fun registerCallbacks() {
        unregisterCallbacks()
        try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return
            val self = ComponentName(this, NowPlayingListener::class.java)
            boundControllers = manager.getActiveSessions(self)
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    NowPlayingStore.readCurrent(applicationContext)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    NowPlayingStore.readCurrent(applicationContext)
                }
            }
            callbacks = cb
            boundControllers.forEach { it.registerCallback(cb) }
        } catch (_: Exception) {
            // Best-effort; on-demand refresh still works.
        }
    }

    private fun unregisterCallbacks() {
        callbacks?.let { cb -> boundControllers.forEach { it.unregisterCallback(cb) } }
        callbacks = null
        boundControllers = emptyList()
    }
}
