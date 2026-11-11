package com.nuvio.app.features.magnet

import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerTorrentEpisode

fun buildMagnetPlayerLaunch(
    magnet: MagnetLink,
    resolution: MagnetResolution,
    profileId: Int,
    target: MagnetPlaybackTarget? = null,
): PlayerLaunch {
    val match = resolution.match
    val hint = resolution.hint
    val contentType = match?.kind?.metaType ?: if (hint?.isSeries == true) "series" else "movie"
    val parentMetaId = match?.metaId ?: "magnet:${magnet.infoHash}"
    val displayName = magnet.displayName?.takeIf(String::isNotBlank)
    val targetFilename = target?.filename?.takeIf(String::isNotBlank)
    val fallbackSeason = target?.seasonNumber ?: match?.seasonNumber
    val seasonNumber = fallbackSeason ?: target?.episodeNumber?.let { 1 }
    val episodeNumber = target?.episodeNumber ?: match?.episodeNumber
    val sameEpisodeAsMatch = target?.episodeNumber == null || target.episodeNumber == match?.episodeNumber
    val videoId = if (target?.episodeNumber != null && seasonNumber != null && episodeNumber != null) {
        "$parentMetaId:$seasonNumber:$episodeNumber"
    } else {
        match?.videoId ?: parentMetaId
    }
    return PlayerLaunch(
        profileId = profileId,
        title = match?.title ?: hint?.title ?: displayName ?: MAGNET_FALLBACK_TITLE,
        sourceUrl = "torrent://${magnet.infoHash}",
        logo = null,
        poster = match?.posterUrl,
        background = match?.backdropUrl,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = match?.episodeTitle.takeIf { sameEpisodeAsMatch },
        episodeThumbnail = match?.episodeThumbnail.takeIf { sameEpisodeAsMatch },
        streamTitle = targetFilename?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: displayName
            ?: MAGNET_FALLBACK_TITLE,
        streamSubtitle = match?.episodeLabel,
        providerName = MAGNET_PROVIDER_NAME,
        contentType = contentType,
        videoId = videoId,
        parentMetaId = parentMetaId,
        parentMetaType = contentType,
        torrentInfoHash = magnet.infoHash,
        torrentFileIdx = target?.fileIndex,
        torrentFilename = targetFilename ?: displayName,
        torrentTrackers = magnet.trackers,
        torrentEpisodes = buildMagnetTorrentEpisodes(target, fallbackSeason),
        sourceFilename = targetFilename ?: displayName,
    )
}

private fun buildMagnetTorrentEpisodes(
    target: MagnetPlaybackTarget?,
    fallbackSeason: Int?,
): List<PlayerTorrentEpisode> = target?.queue.orEmpty().mapNotNull { episode ->
    val episodeNumber = episode.episode ?: return@mapNotNull null
    PlayerTorrentEpisode(
        fileIndex = episode.fileIndex,
        path = episode.filename,
        season = episode.season ?: fallbackSeason ?: 1,
        episode = episodeNumber,
    )
}

private const val MAGNET_PROVIDER_NAME = "Magnet"
private const val MAGNET_FALLBACK_TITLE = "Magnet link"
