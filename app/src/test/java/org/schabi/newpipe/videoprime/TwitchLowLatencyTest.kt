package org.schabi.newpipe.videoprime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchLowLatencyTest {
    private val live = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:100
        #EXT-X-PROGRAM-DATE-TIME:2026-11-09T10:00:00.000Z
        #EXTINF:2.000,live
        https://video.example/a.ts
        #EXT-X-PROGRAM-DATE-TIME:2026-11-09T10:00:02.000Z
        #EXTINF:2.002,live
        https://video.example/b.ts
        #EXT-X-TWITCH-PREFETCH:https://video.example/c.ts
        #EXT-X-TWITCH-PREFETCH:https://video.example/d.ts
    """.trimIndent()

    @Test
    fun prefetchLinesBecomePieces() {
        val lines = TwitchLowLatency.rewrite(live).lines().filter { it.isNotEmpty() }
        assertFalse(lines.any { it.startsWith("#EXT-X-TWITCH-PREFETCH") })
        assertEquals(
            listOf(
                "#EXTINF:2.002,live",
                "https://video.example/c.ts",
                "#EXTINF:2.002,live",
                "https://video.example/d.ts"
            ),
            lines.takeLast(4)
        )
        // what was there stays as it was
        assertTrue(lines.contains("https://video.example/a.ts"))
        assertTrue(lines.contains("#EXT-X-MEDIA-SEQUENCE:100"))
    }

    @Test
    fun playlistWithoutPrefetchIsUntouched() {
        val vod = "#EXTM3U\n#EXTINF:10.0,\nhttps://video.example/0.ts\n#EXT-X-ENDLIST"
        assertEquals(vod, TwitchLowLatency.rewrite(vod))
    }
}
