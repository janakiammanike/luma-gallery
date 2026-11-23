package com.luma.tvremote2.platform.webos

import org.json.JSONObject

private typealias Requester = suspend (String, Map<String, Any?>, Long) -> JSONObject

/**
 * Features that depend on the TV's firmware and installed apps.
 *
 * Never guess an app id silently: resolve it from the launch points the TV
 * itself reported, use short timeouts, and always return an honest description
 * of what actually happened. Launching a real app is preferred over calling a
 * service endpoint, because that is what produces a visible result on webOS 4.1.
 */
object WebOsOptionalFeatures {

    private const val SHORT_TIMEOUT_MS = 4_000L
    private const val LAUNCH_APP = "system.launcher/launch"

    suspend fun searchOnTv(
        request: Requester,
        apps: suspend () -> List<WebOsApp>,
        query: String
    ): String {
        if (query.isBlank()) return "Type something to search"

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val candidate = apps().firstOrNull {
            it.id.contains("search", ignoreCase = true) ||
                it.title.contains("search", ignoreCase = true)
        }
        if (candidate != null) {
            for (uri in listOf("search://query?query=$encoded", "search://search?query=$encoded")) {
                try {
                    request(
                        LAUNCH_APP,
                        mapOf("id" to candidate.id, "params" to mapOf("uri" to uri)),
                        SHORT_TIMEOUT_MS
                    )
                    return "Opened ${candidate.title} for \"$query\""
                } catch (_: Exception) {
                    // Try the next URI form, then a plain launch.
                }
            }
            try {
                request(
                    LAUNCH_APP,
                    mapOf("id" to candidate.id, "params" to mapOf("query" to query)),
                    SHORT_TIMEOUT_MS
                )
                return "Opened ${candidate.title} for \"$query\""
            } catch (_: Exception) {
                // Fall through to the YouTube path proven on this TV.
            }
        }
        return openAppSearch(request, apps, "youtube", query)
    }

    suspend fun openAppSearch(
        request: Requester,
        apps: suspend () -> List<WebOsApp>,
        keyword: String,
        query: String
    ): String {
        if (query.isBlank()) return "Type something to search"
        val candidate = apps().firstOrNull {
            it.id.contains(keyword, ignoreCase = true) ||
                it.title.contains(keyword, ignoreCase = true)
        } ?: return "No app matching \"$keyword\" is installed on this TV"

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val deepLinks = when {
            keyword.contains("youtube", ignoreCase = true) -> listOf(
                "youtube://results?search_query=$encoded",
                "youtube://search?q=$encoded"
            )
            keyword.contains("netflix", ignoreCase = true) -> listOf("nflix://search?query=$encoded")
            else -> emptyList()
        }
        for (uri in deepLinks) {
            try {
                request(
                    LAUNCH_APP,
                    mapOf("id" to candidate.id, "params" to mapOf("uri" to uri)),
                    SHORT_TIMEOUT_MS
                )
                return "Opened ${candidate.title} search for \"$query\""
            } catch (_: Exception) {
                // Firmware may reject this URI form; try the next one.
            }
        }
        return try {
            request(LAUNCH_APP, mapOf("id" to candidate.id), SHORT_TIMEOUT_MS)
            "Opened ${candidate.title}. Search there, then use the Keyboard tab to type \"$query\"."
        } catch (e: Exception) {
            "Could not open ${candidate.title}: ${e.message ?: "launch failed"}"
        }
    }

    suspend fun playMediaUrl(
        request: Requester,
        apps: suspend () -> List<WebOsApp>,
        url: String
    ): String {
        if (url.isBlank()) return "Enter a media URL first"

        // A YouTube link cannot be played by the generic media player. Hand it
        // to the TV's own YouTube app using the deep-link path already proven
        // by "Send to TV".
        if (url.contains("youtube.com", ignoreCase = true) ||
            url.contains("youtu.be", ignoreCase = true)
        ) {
            val app = apps().firstOrNull {
                it.id.contains("youtube", ignoreCase = true) ||
                    it.title.contains("youtube", ignoreCase = true)
            } ?: return "YouTube is not installed on this TV"

            val videoId = Regex("(?:v=|youtu\\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})")
                .find(url)?.groupValues?.get(1)
            if (videoId != null) {
                try {
                    request(
                        LAUNCH_APP,
                        mapOf("id" to app.id, "params" to mapOf("uri" to "youtube://watch?v=$videoId")),
                        SHORT_TIMEOUT_MS
                    )
                    return "Opened the video in ${app.title}"
                } catch (_: Exception) {
                    // Fall back to opening the app itself.
                }
            }
            return try {
                request(LAUNCH_APP, mapOf("id" to app.id), SHORT_TIMEOUT_MS)
                "Opened ${app.title}. Paste the link in its search box."
            } catch (e: Exception) {
                "Could not open ${app.title}: ${e.message ?: "launch failed"}"
            }
        }

        val media = apps().firstOrNull {
            it.id.contains("mediadiscovery", ignoreCase = true) ||
                it.title.contains("media", ignoreCase = true)
        } ?: return "No media player app is installed on this TV"
        return try {
            request(
                LAUNCH_APP,
                mapOf("id" to media.id, "params" to mapOf("uri" to url, "mediaUrl" to url)),
                SHORT_TIMEOUT_MS
            )
            "Sent the URL to ${media.title}"
        } catch (e: Exception) {
            "Could not open ${media.title}: ${e.message ?: "launch failed"}"
        }
    }

    suspend fun openScreenMirroring(
        request: Requester,
        apps: suspend () -> List<WebOsApp>
    ): String {
        val candidate = apps().firstOrNull {
            it.id.contains("mirror", ignoreCase = true) ||
                it.title.contains("mirror", ignoreCase = true)
        } ?: return "No screen mirroring app is installed on this TV"
        return try {
            request(LAUNCH_APP, mapOf("id" to candidate.id), SHORT_TIMEOUT_MS)
            "Opened screen mirroring on the TV"
        } catch (e: Exception) {
            "Could not open ${candidate.title}: ${e.message ?: "launch failed"}"
        }
    }
}
