package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.Handler
import android.os.Looper
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlin.math.roundToInt

/**
 * PulsePlay: while the start screen is still showing, the pictures of the first cards of the
 * Home (the covers and channel pictures of what fits on screen) are loaded into memory at the
 * size they are drawn at, so that the Home appears with them instead of empty grey cards that
 * fill in a moment later. It never makes the start screen wait more than [MAX_WAIT_MILLIS].
 */
object HomeWarmup {
    private const val MAX_WAIT_MILLIS = 500L
    private const val LIVE_CARDS = 3
    private const val ROWS = 2
    private const val LIVE_CARD_DP = 260
    private const val LIVE_AVATAR_DP = 34
    private const val VIDEO_AVATAR_DP = 38
    private const val NARROW_DP = 600
    private const val WIDE_DP = 1100

    /** Loads the first pictures and calls [done] once, when they are in or the time is up. */
    @JvmStatic
    fun run(context: Context, state: HomeState, done: () -> Unit) {
        val app = context.applicationContext
        val metrics = app.resources.displayMetrics
        val density = metrics.density
        val widthDp = (metrics.widthPixels / density).roundToInt()
        val columns = when {
            widthDp >= WIDE_DP -> 3
            widthDp >= NARROW_DP -> 2
            else -> 1
        }
        // never smaller than the card (a bigger picture in memory also serves a smaller card)
        val cardPx = metrics.widthPixels / columns
        // (address, width, height): covers 16:9, channel pictures square
        val wanted = mutableListOf<Triple<String, Int, Int>>()
        fun cover(url: String?, width: Int) = url?.let { wanted += Triple(it, width, width * 9 / 16) }
        fun avatar(url: String?, side: Int) = url?.let { wanted += Triple(it, side, side) }
        state.live.take(LIVE_CARDS).forEach { item ->
            cover(item.thumbnailUrl, px(LIVE_CARD_DP, density))
            avatar(item.avatarUrl, px(LIVE_AVATAR_DP, density))
        }
        state.videos.take(columns * ROWS).forEach { item ->
            cover(item.thumbnailUrl, cardPx)
            avatar(item.avatarUrl, px(VIDEO_AVATAR_DP, density))
        }
        val main = Handler(Looper.getMainLooper())
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                done()
            }
        }
        val unique = wanted.distinctBy { it.first }
        if (unique.isEmpty()) {
            finish()
            return
        }
        var left = unique.size
        main.postDelayed({ finish() }, MAX_WAIT_MILLIS)
        val one = {
            main.post {
                left--
                if (left == 0) finish()
            }
            Unit
        }
        for ((url, width, height) in unique) {
            app.imageLoader.enqueue(
                ImageRequest.Builder(app)
                    .data(url)
                    .size(width, height)
                    .listener(onSuccess = { _, _ -> one() }, onError = { _, _ -> one() })
                    .build()
            )
        }
    }

    private fun px(dp: Int, density: Float) = (dp * density).roundToInt()
}
