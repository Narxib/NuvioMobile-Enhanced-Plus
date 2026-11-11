package com.nuvio.app.features.magnet

import com.nuvio.app.features.p2p.P2pTorrentFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagnetTorrentFilesTest {

    @Test
    fun `keeps video files and sorts episodes`() {
        val rows = buildMagnetFileRows(
            files = listOf(
                P2pTorrentFile(index = 0, path = "Show/Show - 02.mkv", sizeBytes = 200L),
                P2pTorrentFile(index = 1, path = "Show/Show - 01.mkv", sizeBytes = 100L),
                P2pTorrentFile(index = 2, path = "Show/notes.txt", sizeBytes = 10L),
            ),
            fallbackSeason = 1,
        )

        assertEquals(listOf("Show - 01.mkv", "Show - 02.mkv"), rows.map { it.filename })
        assertEquals(listOf(1, 2), rows.map { it.episode })
        assertEquals(listOf(1, 1), rows.map { it.season })
        assertEquals(1..2, rows.episodeRange())
    }

    @Test
    fun `drops sample files from a batch`() {
        val rows = buildMagnetFileRows(
            files = listOf(
                P2pTorrentFile(index = 0, path = "Show/Show - 01.mkv", sizeBytes = 100L),
                P2pTorrentFile(index = 1, path = "Show/Show - 02.mkv", sizeBytes = 100L),
                P2pTorrentFile(index = 2, path = "Show/sample.mkv", sizeBytes = 5L),
            ),
            fallbackSeason = null,
        )

        assertEquals(2, rows.size)
        assertEquals(listOf(1, 2), rows.map { it.episode })
    }

    @Test
    fun `keeps unparsed videos when there are not enough episodes`() {
        val rows = buildMagnetFileRows(
            files = listOf(
                P2pTorrentFile(index = 0, path = "Movie/Movie.mkv", sizeBytes = 100L),
                P2pTorrentFile(index = 1, path = "Movie/Featurette.mkv", sizeBytes = 50L),
            ),
            fallbackSeason = null,
        )

        assertEquals(2, rows.size)
        assertNull(rows.episodeRange())
    }

    @Test
    fun `builds a playback target for the selected file`() {
        val row = buildMagnetFileRows(
            files = listOf(
                P2pTorrentFile(index = 4, path = "Show/Show S01E05.mkv", sizeBytes = 100L),
            ),
            fallbackSeason = null,
        ).single()

        val target = row.toPlaybackTarget()

        assertEquals(4, target.fileIndex)
        assertEquals("Show/Show S01E05.mkv", target.filename)
        assertEquals(1, target.seasonNumber)
        assertEquals(5, target.episodeNumber)
        assertTrue(row.filename.endsWith(".mkv"))
    }

    @Test
    fun `carries the whole batch as the playback queue`() {
        val rows = buildMagnetFileRows(
            files = listOf(
                P2pTorrentFile(index = 0, path = "Show/Show - 01.mkv", sizeBytes = 100L),
                P2pTorrentFile(index = 1, path = "Show/Show - 02.mkv", sizeBytes = 100L),
            ),
            fallbackSeason = 2,
        )

        val target = rows[1].toPlaybackTarget(rows)

        assertEquals(1, target.fileIndex)
        assertEquals(listOf(0, 1), target.queue.map { it.fileIndex })
        assertEquals(listOf(1, 2), target.queue.map { it.episode })
        assertEquals(listOf(2, 2), target.queue.map { it.season })
    }

    @Test
    fun `defaults the playback season to one when only the episode is known`() {
        val rows = buildMagnetFileRows(
            files = listOf(
                P2pTorrentFile(
                    index = 2,
                    path = "[Erai-raws] Show - 03 [1080p][Multiple Subtitle].mkv",
                    sizeBytes = 100L,
                ),
            ),
            fallbackSeason = null,
        )
        val row = rows.single()
        val target = row.toPlaybackTarget(rows)

        assertEquals(1, row.playbackSeason)
        assertEquals(1, target.seasonNumber)
        assertEquals(1, target.queue.single().season)
    }
}
