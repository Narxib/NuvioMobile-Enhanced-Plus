package com.nuvio.app.features.magnet

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.http.encodeURLParameter

object MagnetMetadataService {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, MagnetResolution>()
    private val cacheMutex = Mutex()

    suspend fun resolve(
        magnet: MagnetLink,
        queryOverride: String? = null,
    ): MagnetResolution {
        val baseHint = MagnetFilenameParser.parse(magnet.displayName)
        val parsedHint = MagnetFilenameParser.parse(queryOverride?.takeIf(String::isNotBlank) ?: magnet.displayName)
        val hint = parsedHint?.copy(
            year = parsedHint.year ?: baseHint?.year,
            season = parsedHint.season ?: baseHint?.season,
            episode = parsedHint.episode ?: baseHint?.episode,
            episodeRange = parsedHint.episodeRange ?: baseHint?.episodeRange,
        ) ?: baseHint
        if (hint == null) return MagnetResolution(hint = null, failure = MagnetResolveFailure.NoMatch)

        val cacheKey = listOf(
            hint.title.lowercase(),
            hint.year?.toString().orEmpty(),
            hint.season?.toString().orEmpty(),
            hint.episode?.toString().orEmpty(),
        ).joinToString("|")
        cacheMutex.withLock { cache[cacheKey] }?.let { return it }

        val apiKey = TmdbSettingsRepository.effectiveApiKey().takeIf(String::isNotBlank)
            ?: return MagnetResolution(hint = hint, failure = MagnetResolveFailure.MissingTmdbKey)
        val language = normalizeTmdbLanguage(TmdbSettingsRepository.snapshot().language)
        val searchLanguages = listOf(language, "en", "es-ES").distinct()
        var candidates = searchMagnetCandidates(hint, apiKey, searchLanguages)
            ?: return MagnetResolution(hint = hint, failure = MagnetResolveFailure.TmdbUnavailable)
        if (candidates.isEmpty()) {
            val baseTitle = removeSequelSuffix(hint.title)
            if (baseTitle != hint.title) {
                candidates = searchMagnetCandidates(hint.copy(title = baseTitle), apiKey, searchLanguages)
                    ?: return MagnetResolution(hint = hint, failure = MagnetResolveFailure.TmdbUnavailable)
            }
        }
        val selected = selectMagnetCandidate(hint, candidates)
            ?: return MagnetResolution(hint = hint, failure = MagnetResolveFailure.NoMatch)
        val match = fetchMatch(selected, hint, apiKey, language)
            ?: return MagnetResolution(hint = hint, failure = MagnetResolveFailure.TmdbUnavailable)

        val resolution = MagnetResolution(hint = hint, match = match)
        cacheMutex.withLock {
            if (cache.size >= MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
            cache[cacheKey] = resolution
        }
        return resolution
    }

    private suspend fun searchMagnetCandidates(
        hint: MagnetMediaHint,
        apiKey: String,
        languages: List<String>,
    ): List<MagnetTmdbCandidate>? {
        var anySucceeded = false
        val bestByIdentity = linkedMapOf<Pair<MagnetMediaKind, Int>, MagnetTmdbCandidate>()
        languages.forEach { searchLanguage ->
            val response = fetch<TmdbMultiSearchResponse>(
                endpoint = "search/multi",
                apiKey = apiKey,
                query = mapOf(
                    "query" to hint.title.encodeURLParameter(),
                    "language" to searchLanguage,
                    "include_adult" to "false",
                    "page" to "1",
                ),
            ) ?: return@forEach
            anySucceeded = true
            response.results.mapNotNull(::toCandidate).forEach { candidate ->
                val identity = candidate.kind to candidate.id
                val previous = bestByIdentity[identity]
                if (previous == null ||
                    scoreMagnetCandidate(hint, candidate) > scoreMagnetCandidate(hint, previous)
                ) {
                    bestByIdentity[identity] = candidate
                }
            }
        }
        return bestByIdentity.values.toList().takeIf { anySucceeded }
    }

    private suspend fun fetchMatch(
        candidate: MagnetTmdbCandidate,
        hint: MagnetMediaHint,
        apiKey: String,
        language: String,
    ): MagnetMatch? = when (candidate.kind) {
        MagnetMediaKind.MOVIE -> {
            val details = fetch<TmdbMovieDetails>(
                endpoint = "movie/${candidate.id}",
                apiKey = apiKey,
                query = mapOf("language" to language),
            ) ?: return null
            MagnetMatch(
                tmdbId = candidate.id,
                kind = MagnetMediaKind.MOVIE,
                title = details.title?.takeIf(String::isNotBlank) ?: candidate.title,
                year = details.releaseDate?.take(4)?.toIntOrNull() ?: hint.year,
                overview = details.overview?.takeIf(String::isNotBlank),
                posterUrl = tmdbImageUrl(details.posterPath, "w500"),
                backdropUrl = tmdbImageUrl(details.backdropPath, "w1280"),
                rating = details.voteAverage?.takeIf { it > 0.0 },
                runtimeMinutes = details.runtime?.takeIf { it > 0 },
            )
        }

        MagnetMediaKind.SERIES -> {
            val details = fetch<TmdbTvDetails>(
                endpoint = "tv/${candidate.id}",
                apiKey = apiKey,
                query = mapOf("language" to language),
            ) ?: return null
            val episode = fetchEpisode(
                tmdbId = candidate.id,
                season = hint.season,
                episode = hint.episode,
                apiKey = apiKey,
                language = language,
            )
            MagnetMatch(
                tmdbId = candidate.id,
                kind = MagnetMediaKind.SERIES,
                title = details.name?.takeIf(String::isNotBlank) ?: candidate.title,
                year = details.firstAirDate?.take(4)?.toIntOrNull() ?: hint.year,
                overview = details.overview?.takeIf(String::isNotBlank),
                posterUrl = tmdbImageUrl(details.posterPath, "w500"),
                backdropUrl = tmdbImageUrl(details.backdropPath, "w1280"),
                rating = details.voteAverage?.takeIf { it > 0.0 },
                runtimeMinutes = episode?.runtime?.takeIf { it > 0 }
                    ?: details.episodeRunTime.firstOrNull()?.takeIf { it > 0 },
                seasonNumber = hint.season,
                episodeNumber = hint.episode,
                episodeTitle = episode?.name?.takeIf(String::isNotBlank),
                episodeOverview = episode?.overview?.takeIf(String::isNotBlank),
                episodeThumbnail = tmdbImageUrl(episode?.stillPath, "w300"),
                episodeAirDate = episode?.airDate?.takeIf(String::isNotBlank),
            )
        }
    }

    private suspend fun fetchEpisode(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        apiKey: String,
        language: String,
    ): TmdbEpisodeDetails? {
        if (season == null || episode == null) return null
        return fetch(
            endpoint = "tv/$tmdbId/season/$season/episode/$episode",
            apiKey = apiKey,
            query = mapOf("language" to language),
        )
    }

    private fun toCandidate(item: TmdbMultiSearchItem): MagnetTmdbCandidate? {
        val kind = MagnetMediaKind.fromTmdb(item.mediaType) ?: return null
        val title = (item.title ?: item.name)?.takeIf(String::isNotBlank) ?: return null
        return MagnetTmdbCandidate(
            id = item.id,
            kind = kind,
            title = title,
            originalTitle = (item.originalTitle ?: item.originalName)?.takeIf(String::isNotBlank),
            year = (item.releaseDate ?: item.firstAirDate)?.take(4)?.toIntOrNull(),
            popularity = item.popularity ?: 0.0,
            posterPath = item.posterPath,
            backdropPath = item.backdropPath,
            overview = item.overview,
            rating = item.voteAverage,
        )
    }

    private suspend inline fun <reified T> fetch(
        endpoint: String,
        apiKey: String,
        query: Map<String, String>,
    ): T? = runCatching {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        json.decodeFromString<T>(httpGetText(url))
    }.getOrNull()
}

private fun tmdbImageUrl(path: String?, size: String): String? {
    val cleanPath = path?.trim()?.takeIf(String::isNotBlank) ?: return null
    return "https://image.tmdb.org/t/p/$size$cleanPath"
}

private val sequelSuffixPattern = Regex(
    """(?i)\s+(?:ii|iii|iv|v|vi|season\s*\d+|part\s*\d+|\d+(?:st|nd|rd|th)\s+season)$""",
)

private fun removeSequelSuffix(title: String): String =
    title.replace(sequelSuffixPattern, "").trim()

private const val MAX_CACHE_ENTRIES = 64

@Serializable
private data class TmdbMultiSearchResponse(
    val results: List<TmdbMultiSearchItem> = emptyList(),
)

@Serializable
private data class TmdbMultiSearchItem(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val popularity: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
private data class TmdbMovieDetails(
    val title: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
private data class TmdbTvDetails(
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
private data class TmdbEpisodeDetails(
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
)
