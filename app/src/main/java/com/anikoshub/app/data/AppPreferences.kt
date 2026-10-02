package com.anikoshub.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("anikoshub", Context.MODE_PRIVATE)

    var tmdbToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var preferredProviderId: String
        get() = prefs.getString(KEY_PROVIDER, Providers.all.first().id) ?: Providers.all.first().id
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    fun isFavorite(media: Media): Boolean =
        favoriteKeys().contains(media.key)

    fun toggleFavorite(media: Media): Boolean {
        val keys = favoriteKeys().toMutableSet()
        val added = if (keys.contains(media.key)) {
            keys.remove(media.key)
            false
        } else {
            keys.add(media.key)
            true
        }
        saveFavoriteKeys(keys)
        saveFavoriteMedia(media, added)
        return added
    }

    fun getFavorites(): List<Media> {
        val raw = prefs.getString(KEY_FAVORITE_DATA, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Media(
                    id = o.optInt("id"),
                    title = o.optString("title"),
                    poster = o.optString("poster").ifBlank { null },
                    backdrop = o.optString("backdrop").ifBlank { null },
                    overview = o.optString("overview"),
                    type = o.optString("type"),
                    rating = o.optDouble("rating", 0.0),
                    year = o.optString("year")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun favoriteKeys(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    private fun saveFavoriteKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, keys).apply()
    }

    private fun saveFavoriteMedia(media: Media, add: Boolean) {
        val current = getFavorites().toMutableList()
        current.removeAll { it.key == media.key }
        if (add) current.add(0, media)
        val arr = JSONArray()
        current.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("title", m.title)
                    put("poster", m.poster ?: "")
                    put("backdrop", m.backdrop ?: "")
                    put("overview", m.overview)
                    put("type", m.type)
                    put("rating", m.rating)
                    put("year", m.year)
                }
            )
        }
        prefs.edit().putString(KEY_FAVORITE_DATA, arr.toString()).apply()
    }

    companion object {
        private const val KEY_TOKEN = "tmdb_token"
        private const val KEY_PROVIDER = "preferred_provider"
        private const val KEY_FAVORITES = "favorite_keys"
        private const val KEY_FAVORITE_DATA = "favorite_data"
    }
}
