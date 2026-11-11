package com.luma.gallery.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * A user-created album. Only the media ids are stored; the items themselves stay in MediaStore.
 */
data class CustomAlbum(
    val id: String,
    val name: String,
    val ids: List<Long>
)

object CustomAlbumStore {

    private const val KEY = "custom_albums"

    fun load(prefs: SharedPreferences): List<CustomAlbum> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val idArray = obj.optJSONArray("ids") ?: JSONArray()
                    val ids = buildList {
                        for (i in 0 until idArray.length()) {
                            val id = idArray.optLong(i, -1L)
                            if (id > 0L) add(id)
                        }
                    }
                    add(
                        CustomAlbum(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            ids = ids
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: SharedPreferences, albums: List<CustomAlbum>) {
        val array = JSONArray()
        albums.forEach { album ->
            array.put(
                JSONObject().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("ids", JSONArray(album.ids))
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }
}
