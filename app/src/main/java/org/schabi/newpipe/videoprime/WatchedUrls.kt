package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * PulsePlay: which of the videos suggested after one are already in the history, so that "Up
 * next", the list beside the video and the count at the end put them after the new ones.
 *
 * Light on the database: one query per video (its suggestions, 40 at most, found through the
 * (service, address) index), off the main thread, and the answer kept in memory for a while.
 */
object WatchedUrls {
    private const val TAG = "WatchedUrls"
    private const val MAX_ASKED = 40
    private const val MAX_KEPT = 30
    private const val KEEP_MILLIS = 10 * 60 * 1000L

    private class Known(val urls: Set<String>, val at: Long)

    private val known = object : LinkedHashMap<String, Known>(MAX_KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Known>?) = size > MAX_KEPT
    }

    /**
     * What is known for this video's suggestions, or null if never read. Kept while the video
     * plays, however long it is (what is watched meanwhile is added by [markWatched]).
     */
    @JvmStatic
    @Synchronized
    fun cached(info: StreamInfo): Set<String>? = known[info.url]?.urls

    @Synchronized
    private fun fresh(info: StreamInfo): Set<String>? {
        val entry = known[info.url] ?: return null
        return entry.urls.takeIf { SystemClock.elapsedRealtime() - entry.at < KEEP_MILLIS }
    }

    /** A video was just watched: it counts at once for the suggestions already read. */
    @JvmStatic
    @Synchronized
    fun markWatched(url: String) {
        for ((key, entry) in known.entries.toList()) {
            if (url !in entry.urls) known[key] = Known(entry.urls + url, entry.at)
        }
    }

    /**
     * Reads which of [info]'s suggestions were watched and calls [onReady] on the main thread
     * (at once, without the database, if already known).
     */
    @JvmStatic
    fun load(context: Context, info: StreamInfo, onReady: (Set<String>) -> Unit): Disposable? {
        fresh(info)?.let {
            onReady(it)
            return null
        }
        val urls = suggestions(info).map { it.url }.distinct().take(MAX_ASKED)
        if (urls.isEmpty()) {
            onReady(emptySet())
            return null
        }
        val app = context.applicationContext
        return Single.fromCallable {
            NewPipeDatabase.getInstance(app).streamHistoryDAO()
                .getWatchedUrls(info.serviceId, urls).toSet()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ watched ->
                // (anything marked watched while the query ran is kept)
                val all = synchronized(this) {
                    val merged = watched + known[info.url]?.urls.orEmpty()
                    known[info.url] = Known(merged, SystemClock.elapsedRealtime())
                    merged
                }
                runCatching { onReady(all) }.onFailure { Log.w(TAG, "Could not use the history", it) }
            }, { Log.w(TAG, "Could not read the history", it) })
    }

    /**
     * [items] with the ones not watched first, in YouTube's order, and the watched ones after
     * them (never hidden: when most suggestions were already seen the list doesn't go short or
     * empty, but what is new always comes first).
     */
    @JvmStatic
    fun <T> leaveOut(items: List<T>, watched: Set<String>?, url: (T) -> String): List<T> {
        if (watched.isNullOrEmpty()) return items
        val (seen, fresh) = items.partition { url(it) in watched }
        return fresh + seen
    }

    /**
     * For a list on a background thread: [items] with the videos already watched moved to the
     * end (other items, like channels or playlists, keep their place among the new ones). The
     * list is not touched if the history can't be read.
     */
    @JvmStatic
    fun leaveOutWatched(context: Context, serviceId: Int, items: List<InfoItem>): List<InfoItem> {
        val urls = items.filterIsInstance<StreamInfoItem>().map { it.url }.distinct().take(MAX_ASKED)
        if (urls.isEmpty()) return items
        val watched = runCatching {
            NewPipeDatabase.getInstance(context).streamHistoryDAO()
                .getWatchedUrls(serviceId, urls).toSet()
        }.getOrElse {
            Log.w(TAG, "Could not read the history", it)
            return items
        }
        if (watched.isEmpty()) return items
        val (seen, fresh) = items.partition { it is StreamInfoItem && it.url in watched }
        return fresh + seen
    }

    private fun suggestions(info: StreamInfo) = info.relatedItems.filterIsInstance<StreamInfoItem>()
}
