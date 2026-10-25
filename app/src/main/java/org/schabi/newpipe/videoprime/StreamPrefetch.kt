package org.schabi.newpipe.videoprime

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.core.content.getSystemService
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.ExtractorHelper

/**
 * VideoPrime: the first unwatched videos of the home screen are read in advance (on Wi-Fi), so
 * that tapping one opens it at once: its details and streams are already in the app's cache
 * (they last an hour there). Each video is read once an hour at most, one after the other.
 */
object StreamPrefetch {
    private const val TAG = "PulsePlayPrefetch"
    private const val MAX = 3
    private const val DELAY_MILLIS = 1500L
    private const val AGAIN_AFTER_MILLIS = 50 * 60 * 1000L

    /** url → when it was read in advance. */
    private val done = HashMap<String, Long>()

    @JvmStatic
    fun prefetch(context: Context, items: List<Pair<Int, String>>) {
        val app = context.applicationContext
        val connectivity = app.getSystemService<ConnectivityManager>() ?: return
        if (connectivity.isActiveNetworkMetered) return
        val now = System.currentTimeMillis()
        val todo = synchronized(done) {
            items.asSequence()
                .filter { (serviceId, _) -> serviceId == ServiceList.YouTube.serviceId }
                .filter { (_, url) -> now - (done[url] ?: 0L) > AGAIN_AFTER_MILLIS }
                .take(MAX)
                .toList()
                .onEach { (_, url) -> done[url] = now }
        }
        if (todo.isEmpty()) return
        Observable.fromIterable(todo)
            .delaySubscription(DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .concatMapSingle { (serviceId, url) ->
                ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .subscribeOn(Schedulers.io())
                    .map { true }
                    .onErrorReturn { error ->
                        // not tried again for a while: opening it will just load it as usual
                        Log.d(TAG, "Could not read $url in advance", error)
                        false
                    }
            }
            .subscribeOn(Schedulers.io())
            .subscribe({ }, { Log.d(TAG, "Prefetch stopped", it) })
    }
}
