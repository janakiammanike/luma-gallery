package com.luma.tvremote2.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.tvremote2.data.SecureTvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteUiState(
    val hostInput: String = "",
    val status: String = "Not connected",
    val connectedHost: String? = null,
    val darkTheme: Boolean = false,
    val haptics: Boolean = true,
    val keyboardText: String = "",
    val message: String? = null
)

/**
 * Phase 1 shell state only.
 *
 * Real connect / pairing / commands arrive with the LG platform migration.
 * Keystore-backed storage is wired here already so credentials never land in
 * plain SharedPreferences in the new app.
 */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureTvStore(application)

    private val _ui = MutableStateFlow(
        RemoteUiState(
            hostInput = store.lastHost().orEmpty(),
            darkTheme = store.darkTheme(),
            haptics = store.haptics()
        )
    )
    val ui: StateFlow<RemoteUiState> = _ui.asStateFlow()

    fun onHostInput(value: String) {
        _ui.update { it.copy(hostInput = value) }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun onKeyboardText(value: String) {
        _ui.update { it.copy(keyboardText = value) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            store.setDarkTheme(enabled)
            _ui.update { it.copy(darkTheme = enabled) }
        }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch {
            store.setHaptics(enabled)
            _ui.update { it.copy(haptics = enabled) }
        }
    }

    fun reportPending(feature: String) {
        _ui.update { it.copy(message = "$feature migrates in the next phase") }
    }
}
