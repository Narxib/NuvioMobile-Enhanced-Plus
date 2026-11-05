package com.nuvio.app.features.magnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagnetTmdbMatcherTest {

    @Test
    fun `prefers the matching series for episode hints`() {
        val selected = selectMagnetCandidate(
            hint = MagnetMediaHint(title = "Smallville", season = 1, episode = 6),
            candidates = listOf(
                candidate(id = 1, kind = MagnetMediaKind.MOVIE, title = "Smallville", year = 2001),
                candidate(id = 2, kind = MagnetMediaKind.SERIES, title = "Smallville", year = 2001),
            ),
        )

        assertEquals(2, selected?.id)
    }

    @Test
    fun `uses the year to tell remakes apart`() {
        val selected = selectMagnetCandidate(
            hint = MagnetMediaHint(title = "The Matrix", year = 1999),
            candidates = listOf(
                candidate(id = 1, kind = MagnetMediaKind.MOVIE, title = "The Matrix", year = 2021),
                candidate(id = 2, kind = MagnetMediaKind.MOVIE, title = "The Matrix", year = 1999),
            ),
        )

        assertEquals(2, selected?.id)
    }

    @Test
    fun `rejects unrelated titles`() {
        val selected = selectMagnetCandidate(
            hint = MagnetMediaHint(title = "Smallville", season = 1, episode = 6),
            candidates = listOf(
                candidate(id = 1, kind = MagnetMediaKind.MOVIE, title = "Superman Returns", year = 2006),
                candidate(id = 2, kind = MagnetMediaKind.MOVIE, title = "A Quiet Place", year = 2018),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun `matches titles with and without articles`() {
        assertTrue(titleSimilarity("The Matrix", "Matrix") > 0.9)
        assertEquals(1.0, titleSimilarity("Smallville", "smallville"))
    }

    private fun candidate(
        id: Int,
        kind: MagnetMediaKind,
        title: String,
        year: Int?,
    ) = MagnetTmdbCandidate(
        id = id,
        kind = kind,
        title = title,
        originalTitle = null,
        year = year,
        popularity = 10.0,
        posterPath = null,
        backdropPath = null,
        overview = null,
        rating = null,
    )
}
