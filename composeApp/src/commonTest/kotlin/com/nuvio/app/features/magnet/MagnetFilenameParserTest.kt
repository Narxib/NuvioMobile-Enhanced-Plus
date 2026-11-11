package com.nuvio.app.features.magnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagnetFilenameParserTest {

    @Test
    fun `parses season and episode from scene naming`() {
        val hint = MagnetFilenameParser.parse("Smallville.S01E06.1080p.WEB-DL.DDP5.1.H.264-NTb.mkv")

        assertEquals("Smallville", hint?.title)
        assertEquals(1, hint?.season)
        assertEquals(6, hint?.episode)
        assertTrue(hint?.isSeries == true)
    }

    @Test
    fun `parses season and episode from cross naming`() {
        val hint = MagnetFilenameParser.parse("Smallville - 1x06 - Hourglass [1080p].mkv")

        assertEquals("Smallville", hint?.title)
        assertEquals(1, hint?.season)
        assertEquals(6, hint?.episode)
    }

    @Test
    fun `keeps the series year for disambiguation`() {
        val hint = MagnetFilenameParser.parse("Smallville.2001.S01E06.1080p.WEB-DL.mkv")

        assertEquals("Smallville", hint?.title)
        assertEquals(2001, hint?.year)
        assertEquals(1, hint?.season)
        assertEquals(6, hint?.episode)
    }

    @Test
    fun `parses a movie with its release year`() {
        val hint = MagnetFilenameParser.parse("The.Matrix.1999.1080p.BluRay.x264-GROUP.mkv")

        assertEquals("The Matrix", hint?.title)
        assertEquals(1999, hint?.year)
        assertFalse(hint?.isSeries == true)
    }

    @Test
    fun `keeps numbers that belong to the movie title`() {
        val hint = MagnetFilenameParser.parse("Blade.Runner.2049.2017.1080p.BluRay.mkv")

        assertEquals("Blade Runner 2049", hint?.title)
        assertEquals(2017, hint?.year)
    }

    @Test
    fun `parses season packs`() {
        val hint = MagnetFilenameParser.parse("Smallville Season 1 Complete 1080p BluRay")

        assertEquals("Smallville", hint?.title)
        assertEquals(1, hint?.season)
        assertNull(hint?.episode)
        assertTrue(hint?.isSeries == true)
    }

    @Test
    fun `strips leading release groups`() {
        val hint = MagnetFilenameParser.parse("[SubsPlease] Smallville S01E06 1080p.mkv")

        assertEquals("Smallville", hint?.title)
    }

    @Test
    fun `parses anime absolute episode numbering`() {
        val hint = MagnetFilenameParser.parse(
            "[Erai-raws] Tensei Shitara Ken Deshita II - 06 [1080p HIDIVE WEB-DL AVC AAC][23BEC8F4].mkv",
        )

        assertEquals("Tensei Shitara Ken Deshita II", hint?.title)
        assertNull(hint?.season)
        assertEquals(6, hint?.episode)
    }

    @Test
    fun `keeps hyphenated movie titles without an episode number`() {
        val hint = MagnetFilenameParser.parse("Spider-Man.2002.1080p.BluRay.mkv")

        assertEquals("Spider-Man", hint?.title)
        assertEquals(2002, hint?.year)
        assertFalse(hint?.isSeries == true)
    }

    @Test
    fun `parses anime episode ranges as batches`() {
        val hint = MagnetFilenameParser.parse(
            "[Erai-raws] Tensei Shitara Ken Deshita - 01 ~ 12 [1080p][Multiple Subtitle]",
        )

        assertEquals("Tensei Shitara Ken Deshita", hint?.title)
        assertNull(hint?.episode)
        assertEquals(1..12, hint?.episodeRange)
        assertTrue(hint?.isSeries == true)
    }

    @Test
    fun `parses episode ranges with a season marker`() {
        val hint = MagnetFilenameParser.parse("Smallville S01E01-E06 1080p WEB-DL")

        assertEquals("Smallville", hint?.title)
        assertEquals(1, hint?.season)
        assertNull(hint?.episode)
        assertEquals(1..6, hint?.episodeRange)
    }

    @Test
    fun `parses season packs with an episode range`() {
        val hint = MagnetFilenameParser.parse("Smallville Season 1 (01-12) 1080p BluRay")

        assertEquals("Smallville", hint?.title)
        assertEquals(1, hint?.season)
        assertEquals(1..12, hint?.episodeRange)
    }

    @Test
    fun `does not treat release years as episode ranges`() {
        val hint = MagnetFilenameParser.parse("Blade.Runner.2049.2017.1080p.BluRay.mkv")

        assertNull(hint?.episodeRange)
        assertFalse(hint?.isSeries == true)
    }
}
