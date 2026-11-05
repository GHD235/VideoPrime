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
 * next", the list beside the video and the count at the end leave them out (as YouTube does).
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
     * [items] without the watched ones; if every one was watched the list stays whole (better
     * something seen than nothing).
     */
    @JvmStatic
    fun <T> leaveOut(items: List<T>, watched: Set<String>?, url: (T) -> String): List<T> {
        if (watched.isNullOrEmpty()) return items
        val fresh = items.filter { url(it) !in watched }
        return fresh.ifEmpty { items }
    }

    /**
     * For a list on a background thread: [items] without the videos already watched (other
     * items, like channels or playlists, stay). The list is not touched if the history can't be
     * read or if every video was watched.
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
        if (watched.isEmpty() || urls.all { it in watched }) return items
        return items.filter { it !is StreamInfoItem || it.url !in watched }
    }

    private fun suggestions(info: StreamInfo) = info.relatedItems.filterIsInstance<StreamInfoItem>()
}
