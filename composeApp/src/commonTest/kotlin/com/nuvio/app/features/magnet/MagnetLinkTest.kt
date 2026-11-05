package com.nuvio.app.features.magnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MagnetLinkTest {

    @Test
    fun `parses info hash display name and trackers`() {
        val magnet = MagnetLink.parse(
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
                "&dn=Smallville.S01E06.1080p.WEB-DL.mkv" +
                "&tr=udp%3A%2F%2Ftracker.example%3A80%2Fannounce" +
                "&tr=udp://open.tracker:1337/announce",
        )

        assertNotNull(magnet)
        assertEquals("0123456789abcdef0123456789abcdef01234567", magnet.infoHash)
        assertEquals("Smallville.S01E06.1080p.WEB-DL.mkv", magnet.displayName)
        assertEquals(
            listOf("udp://tracker.example:80/announce", "udp://open.tracker:1337/announce"),
            magnet.trackers,
        )
    }

    @Test
    fun `normalizes base32 info hashes to hex`() {
        val magnet = MagnetLink.parse("magnet:?xt=urn:btih:AERUKZ4JVPG66AJDIVTYTK6N54ASGRLH")

        assertEquals("0123456789abcdef0123456789abcdef01234567", magnet?.infoHash)
    }

    @Test
    fun `reads exact length and plus encoded names`() {
        val magnet = MagnetLink.parse("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Smallville+S01E06&xl=123456")

        assertEquals("Smallville S01E06", magnet?.displayName)
        assertEquals(123456L, magnet?.exactLength)
    }

    @Test
    fun `extracts the link from shared text`() {
        val link = MagnetLink.extractFromText(
            "Mira esto: magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Smallville fin",
        )

        assertEquals(
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Smallville",
            link,
        )
    }

    @Test
    fun `rejects links without a valid info hash`() {
        assertNull(MagnetLink.parse("magnet:?dn=Smallville.S01E06.mkv"))
        assertNull(MagnetLink.parse("magnet:?xt=urn:btih:not-a-hash"))
        assertNull(MagnetLink.parse("https://example.com/file.mkv"))
    }
}
