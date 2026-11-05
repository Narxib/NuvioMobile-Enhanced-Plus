package com.nuvio.app.features.magnet

enum class MagnetMediaKind(val metaType: String, val tmdbValue: String) {
    MOVIE(metaType = "movie", tmdbValue = "movie"),
    SERIES(metaType = "series", tmdbValue = "tv");

    companion object {
        fun fromTmdb(value: String?): MagnetMediaKind? = when (value?.trim()?.lowercase()) {
            "movie" -> MOVIE
            "tv" -> SERIES
            else -> null
        }
    }
}

data class MagnetMatch(
    val tmdbId: Int,
    val kind: MagnetMediaKind,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double? = null,
    val runtimeMinutes: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeOverview: String? = null,
    val episodeThumbnail: String? = null,
    val episodeAirDate: String? = null,
) {
    val metaId: String
        get() = "tmdb:$tmdbId"

    val videoId: String
        get() = if (seasonNumber != null && episodeNumber != null) {
            "$metaId:$seasonNumber:$episodeNumber"
        } else {
            metaId
        }

    val episodeLabel: String?
        get() = if (seasonNumber != null && episodeNumber != null) {
            "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
        } else {
            null
        }

    val summary: String?
        get() = episodeOverview?.takeIf(String::isNotBlank) ?: overview?.takeIf(String::isNotBlank)
}

enum class MagnetResolveFailure {
    MissingTmdbKey,
    TmdbUnavailable,
    NoMatch,
}

data class MagnetResolution(
    val hint: MagnetMediaHint?,
    val match: MagnetMatch? = null,
    val failure: MagnetResolveFailure? = null,
)
