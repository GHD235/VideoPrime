package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.Handler
import android.os.Looper
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * PulsePlay: the home screen's covers are only asked for when a card comes on screen, so moving
 * down the list (above all with a TV remote, a card at a time) showed empty cards while each one
 * downloaded. Once the visible ones have had their turn, the next covers are downloaded to the
 * disk cache, a few at a time: the card then only has to decode them, without waiting for the
 * network. Only the disk: decoding them into memory would push out the covers on screen (a TV's
 * memory for images is small) and take the TV's two decoding slots from the visible cards.
 */
object CoverPrefetch {
    /** The first cards are already being loaded by the screen itself. */
    private const val SKIP = 6
    private const val COUNT = 24
    private const val BATCH = 4
    private const val DELAY_MILLIS = 1500L
    private const val MAX_REMEMBERED = 600

    /** Decoding at this size costs almost nothing; the disk keeps the whole downloaded file. */
    private val TINY = Size(64, 36)

    private val main = Handler(Looper.getMainLooper())
    private val asked = LinkedHashSet<String>()
    private var pending: Runnable? = null
    private var queue: ArrayDeque<String> = ArrayDeque()
    private var running = 0

    /** Called on the main thread. */
    @JvmStatic
    fun warm(context: Context, items: List<HomeItem>) {
        val urls = items.asSequence()
            .drop(SKIP)
            .mapNotNull { it.thumbnailUrl }
            .filter { it !in asked }
            .distinct()
            .take(COUNT)
            .toList()
        if (urls.isEmpty()) return
        val app = context.applicationContext
        pending?.let(main::removeCallbacks)
        pending = Runnable {
            pending = null
            queue = ArrayDeque(urls)
            repeat(BATCH - running) { next(app) }
        }.also { main.postDelayed(it, DELAY_MILLIS) }
    }

    private fun next(app: Context) {
        val url = queue.removeFirstOrNull() ?: return
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
                .size(TINY)
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
