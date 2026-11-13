package org.schabi.newpipe.videoprime

import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedLoadManager

/**
 * PulsePlay: the home screen's update of the subscriptions, run by the app itself.
 *
 * It used to go through a service. Android refuses to start a service when it still counts the
 * app as "in the background", which now and then is the case right when the app comes back to
 * the front: the update silently did not run, and it was not tried again for minutes (the videos
 * only showed up after going to another screen and back, or not at all). With the app in front of
 * the user no service is needed: the work runs here, and reports through the same events
 * ([FeedEventManager]) the screens already listen to. If the app is closed half way, what was
 * loaded is saved and the rest is fetched the next time.
 */
object QuietFeedUpdate {
    private const val TAG = "PulsePlayFeedUpdate"

    @Volatile
    private var running: Disposable? = null

    /** A stuck update (a request that never answers) must not block the next ones for good. */
    private const val TIMEOUT_MINUTES = 10L

    /**
     * Called on the main thread.
     *
     * @return true if an update is running after the call (this one, or one already under way)
     */
    @JvmStatic
    fun start(context: Context, smart: Boolean): Boolean {
        if (running?.isDisposed == false) return true
        return try {
            running = FeedLoadManager(context.applicationContext)
                .startLoading(FeedGroupEntity.GROUP_ALL_ID, smart = smart)
                .timeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .subscribe(
                    { /* the manager has already sent its "done" event */ },
                    { error ->
                        Log.w(TAG, "Feed update failed", error)
                        FeedEventManager.postEvent(FeedEventManager.Event.ErrorResultEvent(error))
                    }
                )
            true
        } catch (e: Exception) {
            // (a broken setting read while preparing the update...): told like any other failure
            Log.w(TAG, "Feed update could not start", e)
            FeedEventManager.postEvent(FeedEventManager.Event.ErrorResultEvent(e))
            false
        }
    }

    @JvmStatic
    fun isRunning(): Boolean = running?.isDisposed == false
}
