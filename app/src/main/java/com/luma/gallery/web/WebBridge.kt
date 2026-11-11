package com.luma.gallery.web

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tiny process-wide bridge between the foreground [WebService] and the Compose UI.
 */
object WebBridge {

    data class Status(
        val running: Boolean = false,
        val starting: Boolean = false,
        val port: Int = WebPrefs.DEFAULT_PORT,
        val urls: List<String> = emptyList(),
        val pin: String = "",
        val error: String? = null
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _libraryChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val libraryChanges: SharedFlow<Unit> = _libraryChanges.asSharedFlow()

    fun publish(status: Status) {
        _status.value = status
    }

    /** Called after an upload so the in-app gallery can refresh its list. */
    fun notifyLibraryChanged() {
        _libraryChanges.tryEmit(Unit)
    }
}
