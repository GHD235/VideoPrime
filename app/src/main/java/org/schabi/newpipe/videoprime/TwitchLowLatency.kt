package org.schabi.newpipe.videoprime

import android.net.Uri
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist
import com.google.android.exoplayer2.source.hls.playlist.HlsMultivariantPlaylist
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory
import com.google.android.exoplayer2.upstream.ParsingLoadable
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.schabi.newpipe.App
import org.schabi.newpipe.R

/**
 * PulsePlay: Twitch's live playlists announce the next two pieces of video before they are
 * finished (`#EXT-X-TWITCH-PREFETCH`), which is what Twitch's own player uses to stay close to
 * the broadcaster. Here those lines become ordinary pieces, so the player sees the broadcast
 * about four seconds further on (the same idea as the "low latency" mode of open players such
 * as Streamlink). A playlist without those lines is left exactly as it came.
 */
object TwitchLowLatency {
    private const val PREFETCH = "#EXT-X-TWITCH-PREFETCH:"
    private const val EXTINF = "#EXTINF:"
    private const val DEFAULT_SECONDS = "2.000"

    /** The playlist with its prefetch lines turned into pieces of the length of the last one. */
    @JvmStatic
    fun rewrite(playlist: String): String {
        if (!playlist.contains(PREFETCH)) return playlist
        var seconds = DEFAULT_SECONDS
        val out = StringBuilder(playlist.length + 64)
        for (line in playlist.lineSequence()) {
            if (line.startsWith(EXTINF)) {
                line.substring(EXTINF.length).substringBefore(',').trim()
                    .takeIf { it.toDoubleOrNull() != null }
                    ?.let { seconds = it }
            }
            if (line.startsWith(PREFETCH)) {
                val url = line.substring(PREFETCH.length).trim()
                if (url.isNotEmpty()) {
                    out.append(EXTINF).append(seconds).append(",live\n").append(url).append('\n')
                }
            } else {
                out.append(line).append('\n')
            }
        }
        return out.toString()
    }

    /** On unless turned off in Settings → Video and audio. */
    @JvmStatic
    fun isEnabled(): Boolean = runCatching {
        val app = App.instance
        PreferenceManager.getDefaultSharedPreferences(app)
            .getBoolean(app.getString(R.string.pulseplay_twitch_low_latency_key), true)
    }.getOrDefault(true)

    @JvmStatic
    fun parserFactory(): HlsPlaylistParserFactory = Factory()

    private class Factory : HlsPlaylistParserFactory {
        private val default = DefaultHlsPlaylistParserFactory()

        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> = Rewriting(default.createPlaylistParser())

        override fun createPlaylistParser(
            multivariantPlaylist: HlsMultivariantPlaylist,
            previousMediaPlaylist: HlsMediaPlaylist?
        ): ParsingLoadable.Parser<HlsPlaylist> = Rewriting(default.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))
    }

    private const val MAX_PLAYLIST_BYTES = 1024 * 1024

    private class Rewriting(
        private val parser: ParsingLoadable.Parser<HlsPlaylist>
    ) : ParsingLoadable.Parser<HlsPlaylist> {
        override fun parse(uri: Uri, inputStream: InputStream): HlsPlaylist {
            val bytes = inputStream.readBytes()
            // a list far bigger than any live one, or one the rewriting chokes on: as it came
            val rewritten = if (bytes.size > MAX_PLAYLIST_BYTES) {
                bytes
            } else {
                runCatching { rewrite(bytes.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8) }
                    .getOrDefault(bytes)
            }
            return parser.parse(uri, ByteArrayInputStream(rewritten))
        }
    }
}
