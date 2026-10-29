package org.schabi.newpipe.videoprime

import android.content.Context
import android.util.Log
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Dimension
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
     * Covers and avatars are asked for at the size they are drawn (the server scales them),
     * rounded up to one of these few sizes: the same song in a list, the player and the
     * notification shares its download instead of one per size. Unknown size: the biggest.
     */
    private val SIZES = intArrayOf(360, 720, 1200)
    private const val MAX_SIZE = 1200
    private const val MAX_MISSING = 2000

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
        return SIZES.firstOrNull { it >= targetPx } ?: MAX_SIZE
    }

    /**
     * Applied to every image the app loads (lists, home, player, notification...): asks for the
     * big version, and if that fails (a video without hq720), for the original one.
     */
    class CoilInterceptor(context: Context? = null) : Interceptor {
        /**
         * Big versions that do not exist (old videos, many songs, some Shorts), with when that was
         * seen: not asked for again for a while. YouTube takes 1-1.6 s to say an image does not
         * exist (one that does comes in 0.1-0.2 s), so they are remembered on disk too. Only for
         * [MISSING_FOR_MILLIS]: a video just uploaded has no big image for a few minutes, and it
         * must get it later.
         */
        private val missing: MutableMap<String, Long> =
            object : LinkedHashMap<String, Long>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_MISSING
            }
        private val missingFile: File? = context?.let { File(it.applicationContext.cacheDir, MISSING_FILE) }
        private val fileLock = Any()

        // read once, off the main thread (Coil may run this on it); every caller waits for it
        private val loaded = lazy { loadMissing() }

        private suspend fun isMissing(url: String): Boolean {
            if (!loaded.isInitialized()) withContext(Dispatchers.IO) { loaded.value }
            val seenAt = synchronized(missing) { missing[url] } ?: return false
            if (System.currentTimeMillis() - seenAt < MISSING_FOR_MILLIS) return true
            synchronized(missing) { missing.remove(url) }
            return false
        }

        private fun loadMissing() {
            val file = missingFile ?: return
            synchronized(fileLock) {
                runCatching {
                    if (!file.exists()) return
                    val now = System.currentTimeMillis()
                    val lines = file.readLines()
                    val recent = lines.mapNotNull { line ->
                        val at = line.substringAfterLast('\t', "").toLongOrNull() ?: return@mapNotNull null
                        if (now - at < MISSING_FOR_MILLIS) line.substringBeforeLast('\t') to at else null
                    }.takeLast(MAX_MISSING)
                    synchronized(missing) { recent.forEach { (url, at) -> missing[url] = at } }
                    // an append-only file: rewritten with only the recent ones when it grows
                    if (lines.size > recent.size + MAX_MISSING / 2) {
                        file.writeText(recent.joinToString("") { (url, at) -> "$url\t$at\n" })
                    }
                }.onFailure { Log.w(TAG, "Could not read the missing thumbnails", it) }
            }
        }

        private suspend fun markMissing(url: String) {
            val now = System.currentTimeMillis()
            synchronized(missing) { missing[url] = now }
            // saved even if the image is no longer wanted (scrolled away): the 404 is known
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { synchronized(fileLock) { missingFile?.appendText("$url\t$now\n") } }
                    .onFailure { Log.w(TAG, "Could not save a missing thumbnail", it) }
            }
        }

        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val original = chain.request.data as? String ?: return chain.proceed()
            // the size the image is drawn at (the bigger side), when Coil knows it
            val target = maxOf(chain.size.width.pxOrZero(), chain.size.height.pxOrZero())
            val best = of(original, target)
            if (best == null || best == original || isMissing(best)) {
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
            // only when the server said it does not exist: a moment without network is no proof
            if (result.isNotFound()) markMissing(best)
            return chain.proceed()
        }
    }

    /**
     * Debug builds only: how long each image took and where it came from (memory, disk or
     * network), in the log under "PulsePlayImages", to compare before/after changes.
     */
    @JvmStatic
    fun debugTimings(): coil3.EventListener.Factory = coil3.EventListener.Factory {
        object : coil3.EventListener() {
            private var startedAt = 0L

            override fun onStart(request: coil3.request.ImageRequest) {
                startedAt = android.os.SystemClock.elapsedRealtime()
            }

            override fun onSuccess(request: coil3.request.ImageRequest, result: SuccessResult) {
                Log.d(TIMINGS_TAG, "${elapsed()} ms ${result.dataSource} ${request.data}")
            }

            override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                Log.d(TIMINGS_TAG, "${elapsed()} ms failed ${request.data}: ${result.throwable.message}")
            }

            private fun elapsed() = android.os.SystemClock.elapsedRealtime() - startedAt
        }
    }

    private const val TIMINGS_TAG = "PulsePlayImages"

    private fun Dimension.pxOrZero(): Int = (this as? Dimension.Pixels)?.px ?: 0

    private fun ImageResult.isNotFound(): Boolean {
        val error = (this as? coil3.request.ErrorResult)?.throwable ?: return false
        return (error as? HttpException)?.response?.code == 404
    }

    private const val TAG = "BestThumbnail"
    private const val MISSING_FILE = "videoprime_missing_thumbnails.txt"
    private const val MISSING_FOR_MILLIS = 3L * 24 * 60 * 60 * 1000
}
