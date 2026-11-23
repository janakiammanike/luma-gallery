package com.luma.tvremote2.platform.webos

/** TV discovered on the LAN (companion beacon / mDNS / subnet scan). */
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

/** Capability surface a concrete TV platform must provide. */
interface WebOsPlatform {
    suspend fun connect(device: WebOsDevice): Result<Unit>
    suspend fun disconnect()
    fun isConnected(): Boolean
    fun connectedHost(): String?

    // Pointer / buttons
    suspend fun button(name: String)
    suspend fun move(dx: Int, dy: Int)
    suspend fun click()
    suspend fun scroll(dx: Int, dy: Int)

    // Keyboard
    suspend fun typeText(text: String)
    suspend fun deleteCharacters(count: Int)
    suspend fun sendEnter()

    // Audio / power / apps
    suspend fun apps(): List<WebOsApp>
    suspend fun launchApp(id: String)

    // More controls — all backed by real SSAP commands from the legacy client.
    suspend fun searchOnTv(query: String): String
    suspend fun openAppSearch(appId: String, query: String): String
    suspend fun selectInput(inputAppId: String)
    suspend fun playMediaUrl(url: String)
    suspend fun openScreenMirroring()

    suspend fun audioVolume(): Int?
    suspend fun volumeUp()
    suspend fun volumeDown()
    suspend fun setMute(mute: Boolean)
    suspend fun channelUp()
    suspend fun channelDown()
    suspend fun powerOff()
    suspend fun powerOn()
}
