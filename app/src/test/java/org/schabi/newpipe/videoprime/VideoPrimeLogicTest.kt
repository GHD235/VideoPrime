package org.schabi.newpipe.videoprime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class VideoPrimeLogicTest {
    @Test
    fun sizesAreShownLikePhonesDo() {
        assertEquals("2 GB", formatSize(2048L * 1024 * 1024))
        assertEquals("500 MB", formatSize(500L * 1024 * 1024))
        assertEquals("10 GB", formatSize(10240L * 1024 * 1024))
        assertTrue(formatSize(2560L * 1024 * 1024).matches(Regex("2[.,]5 GB")))
        assertEquals("", formatSize(0))
    }

    @Test
    fun searchPlatformFollowsServiceAndFilter() {
        val youtube = ServiceList.YouTube.serviceId
        assertEquals(SearchPlatform.YOUTUBE, SearchPlatform.of(youtube, arrayOf()))
        assertEquals(SearchPlatform.YOUTUBE, SearchPlatform.of(youtube, arrayOf("videos")))
        assertEquals(SearchPlatform.YOUTUBE_MUSIC, SearchPlatform.of(youtube, arrayOf("music_songs")))
        assertEquals(SearchPlatform.TWITCH, SearchPlatform.of(ServiceList.Twitch.serviceId, arrayOf()))
    }

    @Test
    fun youtubeAndYoutubeMusicHaveTheirOwnFilters() {
        val music = SearchPlatform.YOUTUBE_MUSIC.filters()
        val videos = SearchPlatform.YOUTUBE.filters()
        assertEquals(SearchPlatform.MUSIC_DEFAULT, music.first())
        assertTrue(music.all { it.startsWith("music_") })
        assertTrue(videos.none { it.startsWith("music_") })
        assertTrue(SearchPlatform.TWITCH.filters().isNotEmpty())
    }
}
