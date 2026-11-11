package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerTorrentEpisodeTest {

    private val episodes = listOf(
        PlayerTorrentEpisode(fileIndex = 0, path = "Show/Show - 01.mkv", season = 1, episode = 1),
        PlayerTorrentEpisode(fileIndex = 1, path = "Show/Show - 02.mkv", season = 1, episode = 2),
        PlayerTorrentEpisode(fileIndex = 2, path = "Show/Show - 03.mkv", season = 1, episode = 3),
    )

    @Test
    fun `resolves the next file in torrent order`() {
        assertEquals(2, nextTorrentEpisode(episodes, 0)?.episode)
        assertEquals(3, nextTorrentEpisode(episodes, 1)?.episode)
    }

    @Test
    fun `returns null for the last file`() {
        assertNull(nextTorrentEpisode(episodes, 2))
    }

    @Test
    fun `returns null when the current file is not in the queue`() {
        assertNull(nextTorrentEpisode(episodes, 9))
        assertNull(nextTorrentEpisode(episodes, null))
        assertNull(nextTorrentEpisode(episodes.take(1), 0))
    }
}
