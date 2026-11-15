package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

/**
 * PulsePlay: while the start screen is still showing, it waits for the pictures of the first
 * cards of the Home (the covers and channel pictures of what fits on screen) so that the Home
 * appears with them instead of empty grey cards that fill in a moment later. The Home is already
 * laid out behind the start screen, so its own cards are the ones loading them: this only
 * listens ([settled], called by [SmoothImage]): pictures already on the phone arrive within
 * [MAX_WAIT_MILLIS]; ones that must come from the network do not hold the start screen (a failed
 * one counts as done). Waiting for those made the start screen last ~3,5 s every time.
 */
object HomeWarmup {
    private const val MAX_WAIT_MILLIS = 350L

    /** A live card is 260 dp wide plus its gap: only the ones on screen are composed. */
    private const val LIVE_CARD_STEP_DP = 272
    private const val NARROW_DP = 600
    private const val WIDE_DP = 1100

    private val main = Handler(Looper.getMainLooper())

    /** The pictures still awaited, or null when nothing is being waited for. Main thread only. */
    private var awaited: MutableSet<String>? = null
    private var done: (() -> Unit)? = null
    private val timeout = Runnable { finish() }

    /** Waits for the first pictures and calls [onDone] once, when they are in or the time is up. */
    @JvmStatic
    fun run(context: Context, state: HomeState, onDone: () -> Unit) {
        if (awaited != null) return // (already waiting: a second read of the Home)
        val metrics = context.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).roundToInt()
        val columns = when {
            widthDp >= WIDE_DP -> 3
            widthDp >= NARROW_DP -> 2
            else -> 1
        }
        val wanted = mutableSetOf<String>()
        // only cards that are on screen at once (the others are not composed and would never load)
        val liveCards = widthDp / LIVE_CARD_STEP_DP + 1
        val videoCards = if (columns == 1) 2 else columns
        (state.live.take(liveCards) + state.videos.take(videoCards)).forEach { item ->
            item.thumbnailUrl?.let(wanted::add)
            item.avatarUrl?.let(wanted::add)
        }
        if (wanted.isEmpty()) {
            onDone()
            return
        }
        awaited = wanted
        done = onDone
        main.postDelayed(timeout, MAX_WAIT_MILLIS)
    }

    /** A card's picture loaded or failed (called on the main thread by [SmoothImage]). */
    @JvmStatic
    fun settled(model: String?) {
        val set = awaited ?: return
        if (set.remove(model) && set.isEmpty()) finish()
    }

    private fun finish() {
        main.removeCallbacks(timeout)
        awaited = null
        done?.also { done = null }?.invoke()
    }
}
