package org.schabi.newpipe.videoprime

import java.util.Locale
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * PulsePlay: tells a song from any other video, for the songs saved to listen without connection
 * and the automatic radio. Before, anything played in the background counted as a song (a
 * podcast or a gameplay video listened to with the screen off was saved too).
 *
 * A song is a YouTube video (not live, from 30 s to 20 min) that is:
 * - from a YouTube Music artist channel ("Estopa - Topic"), always songs;
 * - from a VEVO channel;
 * - in YouTube's "Music" category (what the uploader chose: music videos, lyric videos...);
 * - or, only when YouTube gives no category, titled like a song release ("Official Video",
 *   "Videoclip", "Lyrics", "Audio oficial"...).
 */
object MusicDetector {
    private const val MIN_SECONDS = 30L
    private const val MAX_SECONDS = 20 * 60L

    private val MUSIC_CATEGORIES = setOf("music", "música", "musica")

    private val SONG_TITLE = Regex(
        "official (music )?video|official audio|official lyric|lyric video|\\blyrics\\b|" +
            "video ?oficial|audio oficial|videoclip|video ?clip|\\bletra\\b|\\((audio|visualizer)\\)",
        RegexOption.IGNORE_CASE
    )

    @JvmStatic
    fun isSong(info: StreamInfo): Boolean {
        if (info.serviceId != ServiceList.YouTube.serviceId) return false
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            return false
        }
        if (info.duration !in MIN_SECONDS..MAX_SECONDS) return false
        val uploader = info.uploaderName.orEmpty()
        if (uploader.endsWith(" - Topic")) return true
        if (uploader.lowercase(Locale.ROOT).endsWith("vevo")) return true
        val category = info.category.orEmpty().trim()
        if (category.isNotEmpty()) return category.lowercase(Locale.ROOT) in MUSIC_CATEGORIES
        return SONG_TITLE.containsMatchIn(info.name.orEmpty())
    }
}
