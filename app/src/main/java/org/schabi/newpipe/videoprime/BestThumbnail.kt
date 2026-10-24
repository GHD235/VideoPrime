package org.schabi.newpipe.videoprime

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Dimension
import java.util.Collections
import kotlin.math.roundToInt

/**
 * VideoPrime: every cover and avatar at the highest quality.
 * - YouTube video thumbnails: the feed saves one address per video, and depending on where it
 *   came from (RSS, channel tab, notification) it can be a small one (mqdefault, hqdefault with
 *   black bars...). Every video has the same images under its id, so the 1280x720 one is asked
 *   for (in WebP, much lighter), falling back to the given address if that video has none (old videos, some Shorts).
 * - YouTube Music covers and channel avatars (googleusercontent / ggpht) come 60-120 px wide; the
 *   size is part of the address and the server scales to any size, so a big one is asked for.
 */
object BestThumbnail {
    // only the usual still images: the "_live" ones of a broadcast change and must stay as they are
    private val YOUTUBE = Regex(
        "^https://i\\d?\\.ytimg\\.com/(vi|vi_webp)/([A-Za-z0-9_-]{11})/" +
            "(default|mqdefault|hqdefault|sddefault|maxresdefault|hq720|[0-3])\\.(jpg|webp)$"
    )
    private val GOOGLE_IMAGE =
        Regex("^(https://(?:lh\\d+|yt\\d+)\\.(?:googleusercontent|ggpht)\\.com/[^?#=]+)=([^/?#]*)$")
    private val WIDTH_HEIGHT = Regex("^w(\\d+)-h(\\d+)")
    private val SQUARE_SIZE = Regex("^s(\\d+)")

    /** 1280x720 in WebP: the same picture as the JPEG in less than half the bytes. */
    private const val VIDEO_SIZE = "hq720.webp"

    /**
     * Covers and avatars are asked for at the size they are drawn (the server scales them), never
     * smaller than this nor bigger than [MAX_SIZE]; when the size is not known, [MAX_SIZE].
     */
    private const val MIN_SIZE = 240
    private const val MAX_SIZE = 1200

    /** Sizes are rounded up to steps, so that nearby sizes share the cached image. */
    private const val SIZE_STEP = 120
    private const val MAX_MISSING = 500

    /** The best version of [url] for an image drawn [targetPx] wide (0 = unknown). */
    @JvmStatic
    @JvmOverloads
    fun of(url: String?, targetPx: Int = 0): String? {
        if (url == null) return null
        YOUTUBE.find(url.substringBefore('?'))?.let {
            return "https://i.ytimg.com/vi_webp/${it.groupValues[2]}/$VIDEO_SIZE"
        }
        val google = GOOGLE_IMAGE.find(url) ?: return url
        val (base, params) = google.destructured
        WIDTH_HEIGHT.find(params)?.let { size ->
            val width = size.groupValues[1].toInt()
            val height = size.groupValues[2].toInt()
            val wanted = wantedSize(targetPx)
            if (width <= 0 || width >= wanted) return url
            val newHeight = (height * wanted.toDouble() / width).roundToInt()
            return "$base=w$wanted-h$newHeight${params.substring(size.range.last + 1)}"
        }
        SQUARE_SIZE.find(params)?.let { size ->
            val wanted = wantedSize(targetPx)
            if (size.groupValues[1].toInt() >= wanted) return url
            return "$base=s$wanted${params.substring(size.range.last + 1)}"
        }
        return url
    }

    private fun wantedSize(targetPx: Int): Int {
        if (targetPx <= 0) return MAX_SIZE
        val stepped = (targetPx + SIZE_STEP - 1) / SIZE_STEP * SIZE_STEP
        return stepped.coerceIn(MIN_SIZE, MAX_SIZE)
    }

    /**
     * Applied to every image the app loads (lists, home, player, notification...): asks for the
     * big version, and if that fails (a video without hq720), for the original one.
     */
    class CoilInterceptor : Interceptor {
        /** Big versions that do not exist (old videos, some Shorts): not asked for again. */
        private val missing: MutableSet<String> =
            Collections.newSetFromMap(object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > MAX_MISSING
            })

        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val original = chain.request.data as? String ?: return chain.proceed()
            // the size the image is drawn at (the bigger side), when Coil knows it
            val target = maxOf(chain.size.width.pxOrZero(), chain.size.height.pxOrZero())
            val best = of(original, target)
            if (best == null || best == original || synchronized(missing) { best in missing }) {
                return chain.proceed()
            }
            val bigger = chain.request.newBuilder()
                .data(best)
                // under its own keys: a small copy cached before must not answer for it
                .memoryCacheKey(best)
                .diskCacheKey(best)
                .build()
            val result = chain.withRequest(bigger).proceed()
            if (result is SuccessResult) return result
            synchronized(missing) { missing += best }
            return chain.proceed()
        }
    }

    private fun Dimension.pxOrZero(): Int = (this as? Dimension.Pixels)?.px ?: 0
}
