package org.schabi.newpipe.videoprime

import org.junit.Assert.assertEquals
import org.junit.Test

class BestThumbnailTest {
    @Test
    fun youtubeVideosAlwaysAskForTheBigWebp() {
        val big = "https://i.ytimg.com/vi_webp/dQw4w9WgXcQ/hq720.webp"
        assertEquals(big, BestThumbnail.of("https://i1.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertEquals(big, BestThumbnail.of("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg?sqp=abc&rs=def"))
    }

    @Test
    fun liveThumbnailsStayAsTheyAre() {
        val live = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault_live.jpg"
        assertEquals(live, BestThumbnail.of(live))
    }

    @Test
    fun coversAreAskedForAtOneOfFewSizesNeverSmallerThanDrawn() {
        val cover = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"
        assertEquals("https://lh3.googleusercontent.com/abc=w360-h360-l90-rj", BestThumbnail.of(cover, 200))
        assertEquals("https://lh3.googleusercontent.com/abc=w720-h720-l90-rj", BestThumbnail.of(cover, 361))
        assertEquals("https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj", BestThumbnail.of(cover, 1000))
        // unknown size: the biggest
        assertEquals("https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj", BestThumbnail.of(cover))
    }

    @Test
    fun avatarsKeepABiggerSizeTheyAlreadyHave() {
        assertEquals(
            "https://yt3.ggpht.com/xyz=s360-c-k",
            BestThumbnail.of("https://yt3.ggpht.com/xyz=s88-c-k", 100)
        )
        val big = "https://yt3.ggpht.com/xyz=s900-c-k"
        assertEquals(big, BestThumbnail.of(big, 100))
    }
}
