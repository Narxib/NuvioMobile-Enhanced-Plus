package com.nuvio.app.features.magnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MagnetPlaybackTest {

    @Test
    fun `defaults to season one when only the episode is known`() {
        val magnet = assertNotNull(
            MagnetLink.parse("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
        )
        val resolution = MagnetResolution(
            hint = null,
            match = MagnetMatch(
                tmdbId = 603,
                kind = MagnetMediaKind.SERIES,
                title = "Reincarnated as a Sword",
            ),
        )
        val target = MagnetPlaybackTarget(
            fileIndex = 2,
            filename = "Show/Show - 03.mkv",
            episodeNumber = 3,
            queue = listOf(
                MagnetPlaybackEpisode(0, "Show/Show - 01.mkv", null, 1),
                MagnetPlaybackEpisode(1, "Show/Show - 02.mkv", null, 2),
                MagnetPlaybackEpisode(2, "Show/Show - 03.mkv", null, 3),
            ),
        )

        val launch = buildMagnetPlayerLaunch(
            magnet = magnet,
            resolution = resolution,
            profileId = 1,
            target = target,
        )

        assertEquals(1, launch.seasonNumber)
        assertEquals(3, launch.episodeNumber)
        assertEquals("tmdb:603:1:3", launch.videoId)
        assertEquals(listOf(0, 1, 2), launch.torrentEpisodes.map { it.fileIndex })
        assertEquals(listOf(1, 1, 1), launch.torrentEpisodes.map { it.season })
        assertEquals(listOf(1, 2, 3), launch.torrentEpisodes.map { it.episode })
    }

    @Test
    fun `keeps a season parsed from the batch`() {
        val magnet = assertNotNull(
            MagnetLink.parse("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"),
        )
        val resolution = MagnetResolution(
            hint = null,
            match = MagnetMatch(
                tmdbId = 603,
                kind = MagnetMediaKind.SERIES,
                title = "Reincarnated as a Sword",
            ),
        )
        val target = MagnetPlaybackTarget(
            fileIndex = 5,
            filename = "Show/Show S02E05.mkv",
            seasonNumber = 2,
            episodeNumber = 5,
            queue = listOf(
                MagnetPlaybackEpisode(5, "Show/Show S02E05.mkv", 2, 5),
                MagnetPlaybackEpisode(6, "Show/Show S02E06.mkv", null, 6),
            ),
        )

        val launch = buildMagnetPlayerLaunch(
            magnet = magnet,
            resolution = resolution,
            profileId = 1,
            target = target,
        )

        assertEquals(2, launch.seasonNumber)
        assertEquals(5, launch.episodeNumber)
        assertEquals(listOf(2, 2), launch.torrentEpisodes.map { it.season })
    }
}
