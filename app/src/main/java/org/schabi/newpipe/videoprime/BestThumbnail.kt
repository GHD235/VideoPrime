package org.schabi.newpipe.videoprime

/**
 * VideoPrime: YouTube thumbnails always at the highest quality. The feed saves one address per
 * video, and depending on where it came from (RSS, channel tab, notification) it can be a small
 * one (mqdefault, hqdefault with black bars...): a later refresh could replace a sharp cover with
 * a blurry one. Every YouTube video has the same images under its id, so the 1280x720 one is
 * asked for, falling back to the saved address if that video has none (old videos, some Shorts).
 */
object BestThumbnail {
    private val YOUTUBE = Regex("^https://i\\d?\\.ytimg\\.com/(vi|vi_webp)/([A-Za-z0-9_-]{11})/[^/]+$")

    /** The 1280x720 version of a YouTube thumbnail, or the same address for anything else. */
    @JvmStatic
    fun of(url: String?): String? {
        if (url == null) return null
        val match = YOUTUBE.find(url.substringBefore('?')) ?: return url
        return "https://i.ytimg.com/vi/${match.groupValues[2]}/hq720.jpg"
    }
}
