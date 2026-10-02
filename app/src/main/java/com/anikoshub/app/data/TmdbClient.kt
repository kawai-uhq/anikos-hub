package com.anikoshub.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TmdbClient(
    private val tokenProvider: () -> String
) {
    companion object {
        const val API_BASE = "https://api.themoviedb.org/3"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        const val IMAGE_ORIGINAL = "https://image.tmdb.org/t/p/original"
    }

    suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) {
            error("Add your TMDB API Read Access Token in Settings.")
        }

        val conn = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 20_000
        }

        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                error("TMDB $code: ${body.take(200)}")
            }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun trending(): List<Media> =
        get("/trending/all/day?language=en-US").toMediaList("all")

    suspend fun popular(type: String): List<Media> =
        get("/$type/popular?language=en-US&page=1").toMediaList(type)

    suspend fun search(query: String): List<Media> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return get(
            "/search/multi?query=$encoded&include_adult=false&language=en-US&page=1"
        ).toMediaList("all")
    }

    suspend fun recommendations(type: String, id: Int): List<Media> =
        get("/$type/$id/recommendations?language=en-US&page=1").toMediaList(type)

    suspend fun similar(type: String, id: Int): List<Media> =
        get("/$type/$id/similar?language=en-US&page=1").toMediaList(type)

    suspend fun tvSeasons(id: Int): List<TvSeason> {
        val json = get("/tv/$id?language=en-US")
        val seasons = json.optJSONArray("seasons") ?: return emptyList()

        return (0 until seasons.length()).mapNotNull { i ->
            val s = seasons.optJSONObject(i) ?: return@mapNotNull null
            val number = s.optInt("season_number", -1)
            if (number <= 0) return@mapNotNull null
            TvSeason(
                number = number,
                name = s.optString("name", "Season $number"),
                episodeCount = s.optInt("episode_count", 0)
            )
        }
    }

    suspend fun episodes(id: Int, season: Int): List<Episode> {
        val json = get("/tv/$id/season/$season?language=en-US")
        val array = json.optJSONArray("episodes") ?: return emptyList()

        return (0 until array.length()).mapNotNull { i ->
            val e = array.optJSONObject(i) ?: return@mapNotNull null
            Episode(
                number = e.optInt("episode_number", i + 1),
                name = e.optString("name", "Episode ${i + 1}"),
                overview = e.optString("overview", ""),
                still = e.optString("still_path").ifBlank { null },
                rating = e.optDouble("vote_average", 0.0),
                runtime = e.optInt("runtime", 0)
            )
        }
    }

    private fun JSONObject.toMediaList(fallback: String): List<Media> {
        val array = optJSONArray("results")
        if (array == null) {
            return listOfNotNull(parseMedia(this, fallback))
        }
        return (0 until array.length()).mapNotNull { parseMedia(array.optJSONObject(it), fallback) }
    }

    private fun parseMedia(o: JSONObject?, fallback: String): Media? {
        if (o == null) return null

        val type = if (fallback == "all") {
            o.optString("media_type").ifBlank { fallback }
        } else {
            fallback
        }
        if (type != "movie" && type != "tv") return null

        val title = o.optString(
            if (type == "movie") "title" else "name"
        ).ifBlank { "Untitled" }

        val date = o.optString(
            if (type == "movie") "release_date" else "first_air_date"
        )

        return Media(
            id = o.optInt("id"),
            title = title,
            poster = o.optString("poster_path").ifBlank { null },
            backdrop = o.optString("backdrop_path").ifBlank { null },
            overview = o.optString("overview"),
            type = type,
            rating = o.optDouble("vote_average", 0.0),
            year = date.take(4)
        )
    }
}
