package com.nuvio.app.features.magnet

import com.nuvio.app.features.player.PlayerLaunch

fun buildMagnetPlayerLaunch(
    magnet: MagnetLink,
    resolution: MagnetResolution,
    profileId: Int,
): PlayerLaunch {
    val match = resolution.match
    val hint = resolution.hint
    val contentType = match?.kind?.metaType ?: if (hint?.isSeries == true) "series" else "movie"
    val parentMetaId = match?.metaId ?: "magnet:${magnet.infoHash}"
    val displayName = magnet.displayName?.takeIf(String::isNotBlank)
    return PlayerLaunch(
        profileId = profileId,
        title = match?.title ?: hint?.title ?: displayName ?: MAGNET_FALLBACK_TITLE,
        sourceUrl = "torrent://${magnet.infoHash}",
        logo = null,
        poster = match?.posterUrl,
        background = match?.backdropUrl,
        seasonNumber = match?.seasonNumber,
        episodeNumber = match?.episodeNumber,
        episodeTitle = match?.episodeTitle,
        episodeThumbnail = match?.episodeThumbnail,
        streamTitle = displayName ?: MAGNET_FALLBACK_TITLE,
        streamSubtitle = match?.episodeLabel,
        providerName = MAGNET_PROVIDER_NAME,
        contentType = contentType,
        videoId = match?.videoId ?: parentMetaId,
        parentMetaId = parentMetaId,
        parentMetaType = contentType,
        torrentInfoHash = magnet.infoHash,
        torrentFilename = displayName,
        torrentTrackers = magnet.trackers,
        sourceFilename = displayName,
    )
}

private const val MAGNET_PROVIDER_NAME = "Magnet"
private const val MAGNET_FALLBACK_TITLE = "Magnet link"
