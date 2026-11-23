package com.luma.tvremote2.core

/** Navigation Compose routes, mirroring the legacy screen keys 1:1. */
object Routes {
    const val CONNECT = "connect"
    const val REMOTE = "remote"
    const val CURSOR = "cursor"
    const val KEYBOARD = "keyboard"
    const val APPS = "apps"
    const val MORE = "more"
    const val INPUTS = "inputs"
    const val SEARCH = "search"
    const val MUSIC = "music"
    const val CAST = "cast"
    const val SETTINGS = "settings"
}

/** Bottom-nav items from the legacy RemoteApp.kt. */
data class NavItem(val route: String, val label: String, val glyph: String)

val primaryNavItems = listOf(
    NavItem(Routes.REMOTE, "Remote", "◉"),
    NavItem(Routes.CURSOR, "Cursor", "✥"),
    NavItem(Routes.KEYBOARD, "Keyboard", "⌨"),
    NavItem(Routes.APPS, "Apps", "▦"),
    NavItem(Routes.MORE, "More", "⋯")
)

val moreItems = listOf(
    NavItem(Routes.INPUTS, "Inputs & sound", ""),
    NavItem(Routes.SEARCH, "Content search", ""),
    NavItem(Routes.MUSIC, "Music to TV", ""),
    NavItem(Routes.CAST, "Cast & mirror", "")
)
