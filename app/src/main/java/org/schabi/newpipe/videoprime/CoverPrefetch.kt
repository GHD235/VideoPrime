package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.Handler
import android.os.Looper
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import kotlin.math.roundToInt

/**
 * PulsePlay: the home screen's covers are only asked for when a card comes on screen, so moving
 * down the list (above all with a TV remote, a card at a time) showed empty cards while each one
 * downloaded. Once the visible ones have had their turn, the next covers are downloaded to the
 * disk cache, a few at a time: the card then only has to decode them, without waiting for the
 * network. Only the disk: decoding them into memory would push out the covers on screen (a TV's
 * memory for images is small) and take the TV's two decoding slots from the visible cards.
 *
 * They are asked at the size the card draws them: [BestThumbnail] picks the address by size, so a
 * download at another size could be a different file (a YouTube Music cover at 360 px when the
 * card wants 1200) and the card would still go to the network. When the size does not change the
 * address (YouTube thumbnails, Twitch previews: almost all) it is decoded tiny, which costs nothing.
 */
object CoverPrefetch {
    /**
     * The cards on screen at once are already being loaded by the screen itself (on a phone only
     * about two; it was 6, so the 3rd to 6th card, the first ones a finger reaches, were never
     * downloaded ahead).
     */
    private const val SKIP_PER_COLUMN = 2
    private const val LIVE_SKIP = 2
    private const val COUNT = 30
    private const val BATCH = 4
    private const val DELAY_MILLIS = 1500L
    private const val MAX_REMEMBERED = 600

    private const val NARROW_DP = 600
    private const val WIDE_DP = 1100
    private const val SIDE_PADDING_DP = 32
    private const val LIVE_CARD_DP = 260

    /** Decoding at this size costs almost nothing; the disk keeps the whole downloaded file. */
    private const val TINY_PX = 64

    private val main = Handler(Looper.getMainLooper())
    private val asked = LinkedHashSet<String>()
    private var pending: Runnable? = null
    private var queue: ArrayDeque<Pair<String, Int>> = ArrayDeque()
    private var running = 0

    /** Called on the main thread. */
    @JvmStatic
    fun warm(context: Context, live: List<HomeItem>, videos: List<HomeItem>) {
        val metrics = context.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).roundToInt()
        val columns = when {
            widthDp >= WIDE_DP -> 3
            widthDp >= NARROW_DP -> 2
            else -> 1
        }
        val videoWidth = ((widthDp - SIDE_PADDING_DP) / columns * metrics.density).roundToInt()
        val liveWidth = (LIVE_CARD_DP * metrics.density).roundToInt()
        // nearest first: the next videos down, then the live row further right
        val wanted = (
            videos.drop(SKIP_PER_COLUMN * columns).map { it to videoWidth } +
                live.drop(LIVE_SKIP).map { it to liveWidth }
            )
            .mapNotNull { (item, width) -> item.thumbnailUrl?.let { it to width } }
            .filter { it.first !in asked }
            .distinctBy { it.first }
            .take(COUNT)
        if (wanted.isEmpty()) return
        val app = context.applicationContext
        pending?.let(main::removeCallbacks)
        pending = Runnable {
            pending = null
            queue = ArrayDeque(wanted)
            repeat(BATCH - running) { next(app) }
        }.also { main.postDelayed(it, DELAY_MILLIS) }
    }

    private fun prefetchSize(url: String, width: Int): Size {
        val px = if (BestThumbnail.of(url, TINY_PX) == BestThumbnail.of(url, width)) TINY_PX else width
        return Size(px, px * 9 / 16)
    }

    private fun next(app: Context) {
        val (url, width) = queue.removeFirstOrNull() ?: return
        if (asked.size >= MAX_REMEMBERED) asked.remove(asked.first())
        if (!asked.add(url)) {
            next(app)
            return
        }
        running++
        val done = { failed: Boolean ->
            main.post {
                running--
                // tried again next time if it failed (no network for a moment...)
                if (failed) asked.remove(url)
                next(app)
            }
            Unit
        }
        app.imageLoader.enqueue(
            ImageRequest.Builder(app)
                .data(url)
                .size(prefetchSize(url, width))
                .memoryCachePolicy(CachePolicy.DISABLED)
                .listener(
                    onSuccess = { _, _ -> done(false) },
                    onError = { _, _ -> done(true) },
                    onCancel = { done(true) }
                )
                .build()
        )
    }
}
