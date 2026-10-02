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
        get() = prefs.getString(KEY_PROVIDER, Providers.all.first().id)
            ?: Providers.all.first().id
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    // ── Favorites ──────────────────────────────────────────────

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

    fun getFavorites(): List<Media> = readMediaList(KEY_FAVORITE_DATA)

    private fun favoriteKeys(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    private fun saveFavoriteKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, keys).apply()
    }

    private fun saveFavoriteMedia(media: Media, add: Boolean) {
        val current = getFavorites().toMutableList()
        current.removeAll { it.key == media.key }
        if (add) current.add(0, media)
        writeMediaList(KEY_FAVORITE_DATA, current)
    }

    // ── Continue watching ──────────────────────────────────────

    fun recordWatch(
        media: Media,
        season: Int?,
        episode: Int?,
        providerId: String
    ) {
        val entry = WatchEntry(
            media = media,
            season = season,
            episode = episode,
            providerId = providerId,
            watchedAt = System.currentTimeMillis()
        )
        val list = getContinueWatching().toMutableList()
        list.removeAll { it.media.key == media.key }
        list.add(0, entry)
        // Keep last 30
        writeWatchList(list.take(30))
    }

    fun getContinueWatching(): List<WatchEntry> {
        val raw = prefs.getString(KEY_CONTINUE, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val media = Media(
                    id = o.optInt("id"),
                    title = o.optString("title"),
                    poster = o.optString("poster").ifBlank { null },
                    backdrop = o.optString("backdrop").ifBlank { null },
                    overview = o.optString("overview"),
                    type = o.optString("type"),
                    rating = o.optDouble("rating", 0.0),
                    year = o.optString("year")
                )
                WatchEntry(
                    media = media,
                    season = o.optInt("season", -1).takeIf { it > 0 },
                    episode = o.optInt("episode", -1).takeIf { it > 0 },
                    providerId = o.optString("providerId", Providers.all.first().id),
                    watchedAt = o.optLong("watchedAt", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun removeContinueWatching(mediaKey: String) {
        writeWatchList(getContinueWatching().filter { it.media.key != mediaKey })
    }

    private fun writeWatchList(list: List<WatchEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.media.id)
                    put("title", e.media.title)
                    put("poster", e.media.poster ?: "")
                    put("backdrop", e.media.backdrop ?: "")
                    put("overview", e.media.overview)
                    put("type", e.media.type)
                    put("rating", e.media.rating)
                    put("year", e.media.year)
                    put("season", e.season ?: -1)
                    put("episode", e.episode ?: -1)
                    put("providerId", e.providerId)
                    put("watchedAt", e.watchedAt)
                }
            )
        }
        prefs.edit().putString(KEY_CONTINUE, arr.toString()).apply()
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun readMediaList(key: String): List<Media> {
        val raw = prefs.getString(key, "[]") ?: "[]"
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

    private fun writeMediaList(key: String, list: List<Media>) {
        val arr = JSONArray()
        list.forEach { m ->
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
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_TOKEN = "tmdb_token"
        private const val KEY_PROVIDER = "preferred_provider"
        private const val KEY_FAVORITES = "favorite_keys"
        private const val KEY_FAVORITE_DATA = "favorite_data"
        private const val KEY_CONTINUE = "continue_watching"
    }
}
