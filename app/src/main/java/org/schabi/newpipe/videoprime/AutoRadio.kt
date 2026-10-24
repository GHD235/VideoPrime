package org.schabi.newpipe.videoprime

import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.Player.REPEAT_MODE_OFF
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.lang.ref.WeakReference
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

/**
 * VideoPrime: a single song played as audio (from a search, the car, a voice search) goes on
 * with its YouTube Music radio: about 25 similar songs, of the same artist and others, and more
 * when they run out. Lists and saved queues are never touched; only a queue that started with
 * one song becomes a radio.
 */
object AutoRadio {
    private const val TAG = "VideoPrimeRadio"

    /** When this few songs are left, the radio of the one playing is added. */
    private const val REFILL_WHEN_LEFT = 3

    /** The queue that is a radio (started with one song), so that it is refilled. */
    private var radioQueue = WeakReference<PlayQueue>(null)
    private var loading: Disposable? = null
    private var loadingFor = WeakReference<PlayQueue>(null)

    /** The song whose radio was last added: not asked for again (if it brought nothing new). */
    private var lastSeed: String? = null

    /** Called when a stream starts playing (its details are known). */
    @JvmStatic
    fun onStreamStarted(player: Player, info: StreamInfo) {
        val queue = player.playQueue ?: return
        val context = player.context
        val enabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.videoprime_auto_radio_key), true)
        if (!enabled || !player.audioPlayerSelected() || player.repeatMode != REPEAT_MODE_OFF ||
            info.serviceId != ServiceList.YouTube.serviceId ||
            info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        ) {
            return
        }
        val isRadio = radioQueue.get() === queue
        // only songs: a podcast or a talk sent to the background stays alone
        val starts = !isRadio && queue.size() == 1 && isSong(info)
        val runningOut = isRadio && queue.index >= queue.size() - REFILL_WHEN_LEFT && info.url != lastSeed
        if (!starts && !runningOut) return
        if (loading?.isDisposed == false) {
            if (loadingFor.get() === queue) return
            loading?.dispose() // a request for a queue that is no longer playing
        }
        if (starts) radioQueue = WeakReference(queue)
        lastSeed = info.url
        loadingFor = WeakReference(queue)

        val videoId = runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(info.url) }
            .getOrNull() ?: return
        loading = Single.fromCallable { radioOf(videoId) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { songs -> append(queue, songs) },
                { Log.w(TAG, "No radio for $videoId", it) }
            )
    }

    private fun isSong(info: StreamInfo): Boolean = info.category.equals("Music", ignoreCase = true) ||
        info.uploaderName?.endsWith(" - Topic") == true

    /** The radio of a song: YouTube Music's first, the plain YouTube mix if that fails. */
    private fun radioOf(videoId: String): List<StreamInfoItem> {
        var lastError: Exception? = null
        for (list in listOf("RDAMVM$videoId", "RD$videoId")) {
            try {
                val info = PlaylistInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=$videoId&list=$list"
                )
                if (info.relatedItems.isNotEmpty()) return info.relatedItems
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Empty radio")
    }

    private fun append(queue: PlayQueue, songs: List<StreamInfoItem>) {
        if (radioQueue.get() !== queue || queue.isDisposed) return
        val present = queue.streams.map { it.url }.toHashSet()
        val fresh = songs
            .filter {
                it.streamType != StreamType.LIVE_STREAM &&
                    it.streamType != StreamType.AUDIO_LIVE_STREAM &&
                    present.add(it.url)
            }
        if (fresh.isEmpty()) return
        queue.append(SinglePlayQueue(fresh, 0).streams)
        Log.i(TAG, "${fresh.size} songs added to the radio")
    }
}
