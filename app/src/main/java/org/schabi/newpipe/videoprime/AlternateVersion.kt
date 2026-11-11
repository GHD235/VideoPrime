package org.schabi.newpipe.videoprime

import android.util.Log
import com.grack.nanojson.JsonParser
import java.net.URLEncoder
import java.text.Normalizer
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Some album tracks of YouTube Music ("<artist> - Topic" channels) are blocked by the label for
 * every client but YouTube's own apps ("This video is not available"), while the official video
 * or another upload of the same song plays fine. When that happens, play that other version.
 */
object AlternateVersion {
    private const val TAG = "PulsePlayAlternate"
    private const val TOPIC = " - Topic"
    private const val MAX_TRIES = 3
    private const val MIN_SECONDS = 60L
    private const val MAX_SECONDS = 12 * 60L

    /** Versions that are not the same recording, unless the original title says so. */
    private val OTHER_RECORDINGS = listOf(
        "karaoke", "cover", "instrumental", "remix", "live", "en directo", "en vivo",
        "acustic", "acoustic", "8d", "slowed", "speed up", "sped up", "reaction", "tutorial",
        "guitar", "guitarra", "piano"
    )

    private const val MAX_EXTRA_WORDS = 2
    private val DECORATION = setOf(
        "video", "videoclip", "clip", "oficial", "official", "audio", "letra", "lyrics", "lyric",
        "hd", "hq", "4k", "remaster", "remastered", "remasterizado", "music", "musica", "con"
    )

    private const val NO_ALTERNATE_MILLIS = 10 * 60 * 1000L
    private val MARKS = Regex("\\p{M}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Songs with no alternate found lately: the queue and prefetch ask for them again. */
    private val noAlternate = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun canHelp(serviceId: Int, error: Throwable): Boolean = serviceId == ServiceList.YouTube.serviceId &&
        (
            error.javaClass == ContentNotAvailableException::class.java ||
                error is GeographicRestrictionException
            )

    /** Blocking: returns another playable version of the song at [url], or null. */
    @JvmStatic
    fun find(serviceId: Int, url: String): StreamInfo? {
        val failedAt = noAlternate[url]
        if (failedAt != null && System.currentTimeMillis() - failedAt < NO_ALTERNATE_MILLIS) {
            return null
        }
        return search(serviceId, url).also {
            if (it == null) noAlternate[url] = System.currentTimeMillis() else noAlternate.remove(url)
        }
    }

    private fun search(serviceId: Int, url: String): StreamInfo? = runCatching {
        val (title, author) = titleAndAuthor(url) ?: return null
        // only album tracks: for other videos a "similar" one could be something else entirely
        if (!author.endsWith(TOPIC)) return null
        val artist = author.removeSuffix(TOPIC).trim()
        val service = NewPipe.getService(serviceId)
        val query = service.searchQHFactory.fromQuery(
            "$artist $title",
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            ""
        )
        val originalId = service.streamLHFactory.getId(url)
        val candidates = SearchInfo.getInfo(service, query).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter {
                it.url != url && runCatching { service.streamLHFactory.getId(it.url) }
                    .getOrNull() != originalId
            }
            .mapNotNull { item -> score(item, title, artist)?.let { item to it } }
            .sortedByDescending { it.second }
            .take(MAX_TRIES)
        for ((item, _) in candidates) {
            val info = runCatching { StreamInfo.getInfo(service, item.url) }.getOrNull()
            if (info != null) {
                Log.i(TAG, "\"$title\" not available, playing ${info.name}")
                return info
            }
        }
        null
    }.getOrNull()

    private fun score(item: StreamInfoItem, title: String, artist: String): Int? {
        val name = normalize(item.name)
        val uploader = normalize(item.uploaderName.orEmpty())
        val wantedTitle = normalize(title)
        val wantedArtist = normalize(artist)
        if (wantedTitle.isEmpty() || !name.contains(wantedTitle)) return null
        val byArtist = uploader.contains(wantedArtist) ||
            (uploader.isNotEmpty() && wantedArtist.contains(uploader))
        if (!byArtist && !name.contains(wantedArtist)) return null
        if (OTHER_RECORDINGS.any { hasWord(name, it) && !hasWord(wantedTitle, it) }) return null
        val seconds = item.duration
        if (seconds > 0 && (seconds < MIN_SECONDS || seconds > MAX_SECONDS)) return null
        // words that are neither the song, the artist nor a usual label ("video clip", "letra"...)
        // mean another version: other artists ("feat. X"), a medley, a different song...
        val known = (wantedTitle.split(' ') + wantedArtist.split(' ')).toSet() + DECORATION
        val extra = name.split(' ').count { it.isNotEmpty() && it !in known }
        if (extra > MAX_EXTRA_WORDS) return null
        return (if (byArtist) 1 else 0) - extra * 2
    }

    private fun hasWord(text: String, word: String) = " $text ".contains(" $word ")

    /** Title and channel from oEmbed, which still answers for blocked videos. */
    private fun titleAndAuthor(url: String): Pair<String, String>? {
        val body = DownloaderImpl.getInstance().get(
            "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
        ).responseBody()
        val json = JsonParser.`object`().from(body)
        val title = json.getString("title") ?: return null
        val author = json.getString("author_name") ?: return null
        return title to author
    }

    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(NOT_WORD, " ")
        .trim()
}
