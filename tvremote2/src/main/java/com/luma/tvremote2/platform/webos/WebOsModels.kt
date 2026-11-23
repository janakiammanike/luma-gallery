package com.luma.tvremote2.platform.webos

/** TV discovered on the LAN (via companion beacon, mDNS or subnet scan). */
data class WebOsDevice(
    val host: String,
    val model: String? = null,
    val uuid: String? = null,
    val macAddresses: List<String> = emptyList(),
    val viaCompanion: Boolean = false
)

/** Launchpoint entry returned by com.webos.applicationManager/listLaunchPoints. */
data class WebOsApp(
    val id: String,
    val title: String,
    val type: String = "",
    val icon: String? = null
)

/** Result of a pairing attempt — the client-key is always Keystore-backed. */
data class WebOsPairing(val host: String, val clientKey: String)

/** Capability surface a concrete LG transport must provide in Phase 2. */
interface WebOsPlatform {
    suspend fun connect(device: WebOsDevice): Result<Unit>
    suspend fun disconnect()
    fun isConnected(): Boolean

    // Input / pointer
    suspend fun button(name: String)
    suspend fun move(dx: Int, dy: Int)
    suspend fun click()
    suspend fun scroll(dx: Int, dy: Int)
    suspend fun typeText(text: String)
    suspend fun deleteCharacters(count: Int)
    suspend fun sendEnter()

    // Device / apps
    suspend fun apps(): List<WebOsApp>
    suspend fun launchApp(id: String)
    suspend fun audioStatus(): Result<Int>
    suspend fun volumeUp()
    suspend fun volumeDown()
    suspend fun setMute(mute: Boolean)
}
