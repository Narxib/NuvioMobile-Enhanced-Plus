package com.nuvio.app.features.magnet

import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.watchedItemKeys
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressUiState

internal fun magnetEpisodeVideoId(metaId: String, season: Int, episode: Int): String =
    "$metaId:$season:$episode"

internal fun MagnetFileRow.isWatched(
    metaId: String,
    metaType: String,
    watchedKeys: Set<String>,
    progress: WatchProgressUiState,
): Boolean {
    val season = playbackSeason ?: return false
    val episode = episode ?: return false
    if (watchedItemKeys(metaType, metaId, season, episode).any(watchedKeys::contains)) return true
    return progress.progressForVideo(
        videoId = magnetEpisodeVideoId(metaId, season, episode),
        parentMetaId = metaId,
        seasonNumber = season,
        episodeNumber = episode,
    )?.isEffectivelyCompleted == true
}

internal fun MagnetFileRow.progressFraction(
    metaId: String,
    progress: WatchProgressUiState,
): Float {
    val season = playbackSeason ?: return 0f
    val episode = episode ?: return 0f
    val entry = progress.progressForVideo(
        videoId = magnetEpisodeVideoId(metaId, season, episode),
        parentMetaId = metaId,
        seasonNumber = season,
        episodeNumber = episode,
    ) ?: return 0f
    return if (entry.isEffectivelyCompleted) 0f else entry.progressFraction
}

internal fun toggleMagnetEpisodeWatched(
    metaId: String,
    metaType: String,
    title: String,
    season: Int,
    episode: Int,
    isWatched: Boolean,
) {
    val item = WatchedItem(
        id = metaId,
        type = metaType,
        name = title,
        season = season,
        episode = episode,
        markedAtEpochMs = 0L,
    )
    if (isWatched) {
        WatchedRepository.unmarkWatched(item)
    } else {
        WatchedRepository.markWatched(item)
    }
    WatchProgressRepository.clearProgress(
        videoId = magnetEpisodeVideoId(metaId, season, episode),
        parentMetaId = metaId,
    )
}
