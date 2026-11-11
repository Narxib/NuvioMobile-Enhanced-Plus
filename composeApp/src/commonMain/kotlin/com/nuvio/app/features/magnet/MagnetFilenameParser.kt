package com.nuvio.app.features.magnet

data class MagnetMediaHint(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeRange: IntRange? = null,
) {
    val isSeries: Boolean
        get() = season != null || episode != null || episodeRange != null
}

object MagnetFilenameParser {
    private val seriesPattern = Regex("""(?i)(?<![a-z0-9])s(\d{1,2})[\s._-]*e(\d{1,4})(?!\d)""")
    private val crossPattern = Regex("""(?<!\d)(\d{1,2})[xX](\d{1,4})(?!\d)""")
    private val seasonPackPattern = Regex("""(?i)(?<![a-z0-9])season[\s._-]*(\d{1,2})(?!\d)""")
    private val episodeRangePattern = Regex(
        """(?i)(?<![a-z0-9])(?:e|ep|eps|episode|episodes)?\s*(\d{1,3})\s*(?:~|～|–|—|-|to)\s*(?:e|ep|eps|episode|episodes)?\s*(\d{1,3})(?![\dx])""",
    )
    private val episodeRangeSuffixPattern = Regex(
        """(?i)^\s*(?:~|～|–|—|-)\s*(?:e|ep|eps|episode|episodes)?\s*(\d{1,3})(?![\dx])""",
    )
    private val absoluteEpisodePattern = Regex(
        """(?i)(?<![a-z0-9])[-–—]\s*(\d{1,3})(?:\s*v\d)?(?=\s*(?:\[|\(|$|1080p|720p|2160p|480p|4k|web|bluray|hdtv|dvd))""",
    )
    private val yearPattern = Regex("""(?<![\dx])(19\d{2}|20\d{2})(?![\dx])""")
    private val videoExtensionPattern = Regex("""(?i)\.(mkv|mp4|avi|mov|m4v|ts|m2ts|webm|wmv|flv)$""")
    private val leadingGroupPattern = Regex("""^\s*(\[[^\]]{1,60}]\s*)+""")
    private val bracketPattern = Regex("""\[[^\]]{0,60}]""")
    private val trailingYearPattern = Regex("""\s+(19\d{2}|20\d{2})$""")
    private val separatorPattern = Regex("""[._]+""")
    private val whitespacePattern = Regex("""\s+""")

    private val releaseTokens = listOf(
        "1080p", "720p", "2160p", "480p", "4k", "uhd", "webrip", "web-dl", "webdl", "web",
        "bluray", "blu-ray", "brrip", "bdrip", "hdtv", "dvdrip", "remux", "x264", "x265",
        "h264", "h265", "hevc", "avc", "xvid", "divx", "aac", "ac3", "eac3", "ddp", "dts",
        "truehd", "atmos", "proper", "repack", "extended", "unrated", "remastered", "hdr",
        "dolby", "vision", "multi", "dual", "subs", "subbed", "dubbed",
    )

    fun parse(displayName: String?): MagnetMediaHint? {
        val rawName = displayName?.trim().orEmpty()
        if (rawName.isBlank()) return null
        val name = rawName
            .replace(videoExtensionPattern, "")
            .replace(leadingGroupPattern, "")
            .trim()
        if (name.isBlank()) return null

        val episodeMatch = seriesPattern.find(name) ?: crossPattern.find(name)
        val episodeStart = episodeMatch?.groupValues?.get(2)?.toIntOrNull()
        val suffixRange = episodeMatch?.let { match ->
            episodeRangeSuffixPattern.find(name.substring(match.range.last + 1))
                ?.groupValues?.get(1)
                ?.toIntOrNull()
                ?.let { end -> validEpisodeRange(episodeStart, end) }
        }
        val standaloneRangeMatch = if (suffixRange == null) {
            episodeRangePattern.findAll(name).firstOrNull { match ->
                validEpisodeRange(
                    match.groupValues[1].toIntOrNull(),
                    match.groupValues[2].toIntOrNull(),
                ) != null
            }
        } else {
            null
        }
        val seasonPackMatch = seasonPackPattern.find(name)
        val absoluteEpisodeMatch = if (episodeMatch == null && standaloneRangeMatch == null) {
            absoluteEpisodePattern.find(name)
        } else {
            null
        }
        val season = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
            ?: seasonPackMatch?.groupValues?.get(1)?.toIntOrNull()
        val episodeRange = suffixRange ?: standaloneRangeMatch?.let { match ->
            validEpisodeRange(
                match.groupValues[1].toIntOrNull(),
                match.groupValues[2].toIntOrNull(),
            )
        }
        val episode = if (episodeRange == null) {
            episodeStart ?: absoluteEpisodeMatch?.groupValues?.get(1)?.toIntOrNull()
        } else {
            null
        }
        val lastYearMatch = yearPattern.findAll(name).lastOrNull()
        val titleEnd = listOfNotNull(
            episodeMatch?.range?.first,
            standaloneRangeMatch?.range?.first,
            seasonPackMatch?.range?.first,
            absoluteEpisodeMatch?.range?.first,
            lastYearMatch?.range?.first,
        ).minOrNull() ?: name.length
        val title = cleanTitle(
            value = name.substring(0, titleEnd),
            stripTrailingYear = episodeMatch != null || seasonPackMatch != null ||
                standaloneRangeMatch != null || absoluteEpisodeMatch != null,
        )
        if (title.isBlank()) return null

        return MagnetMediaHint(
            title = title,
            year = lastYearMatch?.value?.toIntOrNull(),
            season = season,
            episode = episode,
            episodeRange = episodeRange,
        )
    }

    private fun validEpisodeRange(start: Int?, end: Int?): IntRange? {
        if (start == null || end == null) return null
        if (start < 1 || end <= start) return null
        if (end - start > MAX_BATCH_EPISODE_SPAN) return null
        return start..end
    }

    private fun cleanTitle(value: String, stripTrailingYear: Boolean): String {
        var title = value
            .replace(bracketPattern, " ")
            .replace(separatorPattern, " ")
            .replace(whitespacePattern, " ")
            .trim(' ', '-', '–', '—', ':', '(', ')')
        val releaseStart = releaseTokens
            .asSequence()
            .map { token -> tokenIndex(title, token) }
            .filter { it > 0 }
            .minOrNull()
        if (releaseStart != null) {
            title = title.substring(0, releaseStart)
        }
        if (stripTrailingYear) {
            title = title.replace(trailingYearPattern, "")
        }
        title = title
            .replace(whitespacePattern, " ")
            .trim(' ', '-', '–', '—', ':', ',', '(', ')')
        return title
    }

    private fun tokenIndex(text: String, token: String): Int {
        var start = text.indexOf(token, ignoreCase = true)
        while (start >= 0) {
            val end = start + token.length
            val beforeIsBoundary = start == 0 || !text[start - 1].isLetterOrDigit()
            val afterIsBoundary = end >= text.length || !text[end].isLetterOrDigit()
            if (beforeIsBoundary && afterIsBoundary) return start
            start = text.indexOf(token, end, ignoreCase = true)
        }
        return -1
    }
}

private const val MAX_BATCH_EPISODE_SPAN = 200
