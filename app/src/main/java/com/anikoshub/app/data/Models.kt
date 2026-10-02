package com.anikoshub.app.data

data class Media(
    val id: Int,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String,
    val type: String, // "movie" or "tv"
    val rating: Double,
    val year: String
) {
    val key: String get() = "$type:$id"
}

data class TvSeason(
    val number: Int,
    val name: String,
    val episodeCount: Int
)

data class Episode(
    val number: Int,
    val name: String,
    val overview: String,
    val still: String?,
    val rating: Double,
    val runtime: Int
)

data class Provider(
    val id: String,
    val displayName: String,
    val movieUrl: (mediaId: Int) -> String,
    val tvUrl: (mediaId: Int, season: Int, episode: Int) -> String
)

object Providers {
    val all = listOf(
        Provider(
            id = "vidlink",
            displayName = "VidLink",
            movieUrl = { id -> "https://vidlink.pro/movie/$id" },
            tvUrl = { id, s, e -> "https://vidlink.pro/tv/$id/$s/$e" }
        ),
        Provider(
            id = "cinesrc",
            displayName = "CineSrc",
            movieUrl = { id -> "https://cinesrc.st/embed/movie/$id" },
            tvUrl = { id, s, e -> "https://cinesrc.st/embed/tv/$id?s=$s&e=$e" }
        ),
        Provider(
            id = "vidfast",
            displayName = "VidFast",
            movieUrl = { id -> "https://vidfast.pro/movie/$id" },
            tvUrl = { id, s, e -> "https://vidfast.pro/tv/$id/$s/$e" }
        )
    )

    fun byId(id: String): Provider =
        all.find { it.id == id } ?: all.first()
}

sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
