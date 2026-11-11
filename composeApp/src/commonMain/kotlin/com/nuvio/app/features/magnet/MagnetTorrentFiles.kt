package com.nuvio.app.features.magnet

import com.nuvio.app.features.p2p.P2pTorrentFile

data class MagnetPlaybackEpisode(
    val fileIndex: Int,
    val filename: String,
    val season: Int?,
    val episode: Int?,
)

data class MagnetPlaybackTarget(
    val fileIndex: Int? = null,
    val filename: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val queue: List<MagnetPlaybackEpisode> = emptyList(),
)

internal data class MagnetFileRow(
    val file: P2pTorrentFile,
    val season: Int?,
    val episode: Int?,
) {
    val filename: String
        get() = file.path.substringAfterLast('/').substringAfterLast('\\')

    val sizeBytes: Long
        get() = file.sizeBytes

    val playbackSeason: Int?
        get() = season ?: episode?.let { 1 }
}

internal fun buildMagnetFileRows(
    files: List<P2pTorrentFile>,
    fallbackSeason: Int?,
): List<MagnetFileRow> {
    val videos = files.filter { it.isVideoFile() }
    val candidates = videos.filterNot { it.isSampleFile() }.ifEmpty { videos }
    if (candidates.isEmpty()) return emptyList()

    val rows = candidates.map { file ->
        val hint = MagnetFilenameParser.parse(file.filename())
        MagnetFileRow(
            file = file,
            season = hint?.season ?: fallbackSeason.takeIf { hint?.episode != null },
            episode = hint?.episode,
        )
    }
    val withEpisodes = rows.filter { it.episode != null }
    val selected = if (withEpisodes.size >= 2) withEpisodes else rows
    return selected.sortedWith(
        compareBy<MagnetFileRow> { it.season ?: Int.MAX_VALUE }
            .thenBy { it.episode ?: Int.MAX_VALUE }
            .thenBy { it.filename.lowercase() },
    )
}

internal fun List<MagnetFileRow>.episodeRange(): IntRange? {
    val episodes = mapNotNull { it.episode }
    if (episodes.size < 2) return null
    return episodes.min()..episodes.max()
}

internal fun MagnetFileRow.toPlaybackTarget(queue: List<MagnetFileRow> = emptyList()): MagnetPlaybackTarget =
    MagnetPlaybackTarget(
        fileIndex = file.index,
        filename = file.path,
        seasonNumber = playbackSeason,
        episodeNumber = episode,
        queue = queue.map { row ->
            MagnetPlaybackEpisode(
                fileIndex = row.file.index,
                filename = row.file.path,
                season = row.playbackSeason,
                episode = row.episode,
            )
        },
    )

private fun P2pTorrentFile.filename(): String =
    path.substringAfterLast('/').substringAfterLast('\\')

private fun P2pTorrentFile.isVideoFile(): Boolean {
    val name = filename()
    return VIDEO_EXTENSIONS.any { extension -> name.endsWith(extension, ignoreCase = true) }
}

private fun P2pTorrentFile.isSampleFile(): Boolean = SAMPLE_PATTERN.containsMatchIn(path)

private val VIDEO_EXTENSIONS = setOf(
    ".mkv", ".mp4", ".avi", ".mov", ".m4v", ".ts", ".m2ts", ".mts",
    ".webm", ".wmv", ".flv", ".mpg", ".mpeg", ".rmvb", ".ogv", ".3gp",
)

private val SAMPLE_PATTERN = Regex("""(?i)(?:^|[/\\_.\-\s])sample(?:[/\\_.\-\s]|$)""")
