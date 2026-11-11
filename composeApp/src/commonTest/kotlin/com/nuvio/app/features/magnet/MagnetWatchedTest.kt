package com.nuvio.app.features.magnet

import com.nuvio.app.features.p2p.P2pTorrentFile
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MagnetWatchedTest {

    private val rows = buildMagnetFileRows(
        files = listOf(
            P2pTorrentFile(index = 0, path = "Show/Show - 01.mkv", sizeBytes = 100L),
            P2pTorrentFile(index = 1, path = "Show/Show - 02.mkv", sizeBytes = 100L),
            P2pTorrentFile(index = 2, path = "Show/Show - 03.mkv", sizeBytes = 100L),
        ),
        fallbackSeason = null,
    )

    @Test
    fun `builds the same video id the player uses`() {
        assertEquals("tmdb:603:1:3", magnetEpisodeVideoId("tmdb:603", 1, 3))
    }

    @Test
    fun `matches a watched mark stored under a series alias`() {
        val watchedKeys = setOf(watchedItemKey(type = "show", id = "tmdb:603", season = 1, episode = 2))

        assertTrue(rows[1].isWatched("tmdb:603", "series", watchedKeys, WatchProgressUiState()))
        assertFalse(rows[2].isWatched("tmdb:603", "series", watchedKeys, WatchProgressUiState()))
    }

    @Test
    fun `treats a completed progress entry as watched`() {
        val progress = WatchProgressUiState(
            entries = listOf(
                WatchProgressEntry(
                    contentType = "series",
                    parentMetaId = "tmdb:603",
                    parentMetaType = "series",
                    videoId = "tmdb:603:1:3",
                    title = "Show",
                    seasonNumber = 1,
                    episodeNumber = 3,
                    lastPositionMs = 20_000L,
                    durationMs = 20_000L,
                    lastUpdatedEpochMs = 1L,
                    isCompleted = true,
                ),
            ),
        )

        assertTrue(rows[2].isWatched("tmdb:603", "series", emptySet(), progress))
        assertFalse(rows[1].isWatched("tmdb:603", "series", emptySet(), progress))
    }

    @Test
    fun `reports progress only while the episode is unfinished`() {
        fun entry(completed: Boolean) = WatchProgressEntry(
            contentType = "series",
            parentMetaId = "tmdb:603",
            parentMetaType = "series",
            videoId = "tmdb:603:1:2",
            title = "Show",
            seasonNumber = 1,
            episodeNumber = 2,
            lastPositionMs = 6_000L,
            durationMs = 10_000L,
            lastUpdatedEpochMs = 1L,
            isCompleted = completed,
        )

        assertEquals(
            0.6f,
            rows[1].progressFraction("tmdb:603", WatchProgressUiState(entries = listOf(entry(completed = false)))),
        )
        assertEquals(
            0f,
            rows[1].progressFraction("tmdb:603", WatchProgressUiState(entries = listOf(entry(completed = true)))),
        )
        assertEquals(0f, rows[0].progressFraction("tmdb:603", WatchProgressUiState()))
    }
}
