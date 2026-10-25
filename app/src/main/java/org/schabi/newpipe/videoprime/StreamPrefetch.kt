package org.schabi.newpipe.videoprime

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.util.ExtractorHelper

/**
 * VideoPrime: the first unwatched videos of the home screen are read in advance (on Wi-Fi), so
 * that tapping one opens it at once: its details and streams are already in the app's cache
 * (they last an hour there). Each video is read once an hour at most, one after the other; not
 * with the battery saver on or while something plays, and the reading stops when the home
 * screen goes.
 */
object StreamPrefetch {
    private const val TAG = "PulsePlayPrefetch"
    private const val MAX = 3
    private const val DELAY_MILLIS = 1500L
    private const val AGAIN_AFTER_MILLIS = 50 * 60 * 1000L
    private const val AGAIN_AFTER_FAILURE_MILLIS = 5 * 60 * 1000L
    private const val REMEMBERED = 200

    /** url → when it may be read in advance again. */
    private val nextTry = LinkedHashMap<String, Long>()
    private val running = CompositeDisposable()

    @JvmStatic
    fun prefetch(context: Context, items: List<Pair<Int, String>>) {
        val app = context.applicationContext
        val connectivity = app.getSystemService<ConnectivityManager>() ?: return
        if (connectivity.isActiveNetworkMetered) return
        if (app.getSystemService<PowerManager>()?.isPowerSaveMode == true) return
        // the bandwidth is for what plays
        if (PlayerService.getCurrentPlayer()?.isPlaying == true) return
        val now = System.currentTimeMillis()
        val todo = synchronized(nextTry) {
            items.asSequence()
                .filter { (serviceId, _) -> serviceId == ServiceList.YouTube.serviceId }
                .filter { (_, url) -> now >= (nextTry[url] ?: 0L) }
                .take(MAX)
                .toList()
                // a short mark while waiting (it may be cancelled), the long one once read
                .onEach { (_, url) -> remember(url, now + AGAIN_AFTER_FAILURE_MILLIS) }
        }
        if (todo.isEmpty()) return
        running.add(
            Observable.fromIterable(todo)
                .delaySubscription(DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .concatMapSingle { (serviceId, url) ->
                    ExtractorHelper.getStreamInfo(serviceId, url, false)
                        .subscribeOn(Schedulers.io())
                        .map {
                            synchronized(nextTry) {
                                remember(url, System.currentTimeMillis() + AGAIN_AFTER_MILLIS)
                            }
                            true
                        }
                        .onErrorReturn { error ->
                            // tried again a little later; opening it just loads it as usual
                            Log.d(TAG, "Could not read $url in advance", error)
                            synchronized(nextTry) {
                                remember(url, System.currentTimeMillis() + AGAIN_AFTER_FAILURE_MILLIS)
                            }
                            false
                        }
                }
                .subscribeOn(Schedulers.io())
                .subscribe({ }, { Log.d(TAG, "Prefetch stopped", it) })
        )
    }

    /** The home screen is gone: what was still to be read is not needed. */
    @JvmStatic
    fun cancel() {
        running.clear()
    }

    private fun remember(url: String, time: Long) {
        nextTry.remove(url)
        nextTry[url] = time
        while (nextTry.size > REMEMBERED) {
            nextTry.remove(nextTry.keys.first())
        }
    }
}
