package org.schabi.newpipe.videoprime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class MusicDetectorTest {
    private fun info(
        name: String,
        uploader: String,
        category: String = "",
        seconds: Long = 210,
        type: StreamType = StreamType.VIDEO_STREAM,
        service: Int = ServiceList.YouTube.serviceId
    ) = StreamInfo(service, "https://www.youtube.com/watch?v=x", "", type, "x", name, 0).apply {
        uploaderName = uploader
        this.category = category
        duration = seconds
    }

    @Test
    fun songs() {
        assertTrue(MusicDetector.isSong(info("Nada que perder", "Pignoise - Topic")))
        assertTrue(MusicDetector.isSong(info("Despacito", "LuisFonsiVEVO")))
        assertTrue(MusicDetector.isSong(info("Algo", "Artista", category = "Music")))
        assertTrue(MusicDetector.isSong(info("Algo", "Artista", category = "Música")))
        assertTrue(MusicDetector.isSong(info("Canción (Official Video)", "Artista")))
        assertTrue(MusicDetector.isSong(info("Canción - Videoclip", "Artista")))
    }

    @Test
    fun notSongs() {
        // a video played in the background is not a song for being in the background
        assertFalse(MusicDetector.isSong(info("GTA VI #2", "Gamer", category = "Gaming")))
        // a title that looks like a song does not beat the category the uploader chose
        assertFalse(MusicDetector.isSong(info("Reacción (Official Video)", "Gamer", category = "Entertainment")))
        assertFalse(MusicDetector.isSong(info("Podcast", "Charlas")))
        assertFalse(MusicDetector.isSong(info("Mix 2 horas", "Pignoise - Topic", seconds = 7200)))
        assertFalse(MusicDetector.isSong(info("Intro", "Pignoise - Topic", seconds = 10)))
        assertFalse(MusicDetector.isSong(info("Directo", "Artista", category = "Music", type = StreamType.LIVE_STREAM)))
        assertFalse(MusicDetector.isSong(info("Directo", "rubius", category = "Music", service = ServiceList.Twitch.serviceId)))
    }
}
