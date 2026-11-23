package com.luma.tvremote2.platform.webos

import org.json.JSONObject

private typealias Requester = suspend (String, Map<String, Any?>, Long) -> JSONObject

/**
 * Features that depend on the TV's firmware and installed apps.
 *
 * These are deliberately best-effort: never guess an app id silently, resolve
 * it from the launch points the TV itself reported, use short timeouts, and
 * always return an honest description of what happened.
 */
object WebOsOptionalFeatures {

    private const val SHORT_TIMEOUT_MS = 4_000L

    suspend fun searchOnTv(
        request: Requester,
        apps: suspend () -> List<WebOsApp>,
        query: String
    ): String {
        if (query.isBlank()) return "Type something to search"

        try {
            request("com.webos.service.search/search", mapOf("query" to query), SHORT_TIMEOUT_MS)
            return "TV search opened for \"$query\""
        } catch (_: Exception) {
            // Fall through to a launch point reported by this TV.
        }

        val candidate = apps().firstOrNull {
            it.id.contains("search", ignoreCase = true) ||
                it.title.contains("search", ignoreCase = true)
        }
        if (candidate != null) {
            try {
                request(
                    "system.launcher/launch",
                    mapOf("id" to candidate.id, "params" to mapOf("query" to query)),
                    SHORT_TIMEOUT_MS
                )
                return "Opened ${candidate.title} for \"$query\""
            } catch (_: Exception) {
                // Try the plain launch below.
            }
        }
        return "This TV does not expose search over the network. Open TV search and use the Keyboard tab."
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
                    "system.launcher/launch",
                    mapOf("id" to candidate.id, "params" to mapOf("uri" to uri)),
                    SHORT_TIMEOUT_MS
                )
                return "Opened ${candidate.title} search for \"$query\""
            } catch (_: Exception) {
                // Firmware may reject this URI form; try the next one.
            }
        }
        return try {
            request("system.launcher/launch", mapOf("id" to candidate.id), SHORT_TIMEOUT_MS)
            "Opened ${candidate.title}. Search there, then use the Keyboard tab to type \"$query\"."
        } catch (e: Exception) {
            "Could not open ${candidate.title}: ${e.message ?: "launch failed"}"
        }
    }

    suspend fun launchByKeyword(
        request: Requester,
        apps: suspend () -> List<WebOsApp>,
        keyword: String,
        successMessage: String,
        failureMessage: String
    ): String {
        val candidate = apps().firstOrNull {
            it.id.contains(keyword, ignoreCase = true) ||
                it.title.contains(keyword, ignoreCase = true)
        } ?: return failureMessage
        return try {
            request("system.launcher/launch", mapOf("id" to candidate.id), SHORT_TIMEOUT_MS)
            successMessage
        } catch (e: Exception) {
            "Could not open ${candidate.title}: ${e.message ?: "launch failed"}"
        }
    }
}
