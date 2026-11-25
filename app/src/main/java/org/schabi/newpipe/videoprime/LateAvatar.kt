package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationManagerCompat
import kotlin.concurrent.thread
import org.schabi.newpipe.util.image.CoilHelper

/**
 * PulsePlay: an alert never waits for the channel's picture (it goes out at once), but when the
 * picture was not in the cache (a new phone, a channel not seen in a while) it is fetched after
 * and put into the alerts still showing, without sounding again.
 */
object LateAvatar {
    private const val WAIT_MILLIS = 15_000L

    /** Fetches [url] in the background and hands it to [onLoaded] (not called if it fails). */
    fun load(context: Context, url: String?, onLoaded: (Bitmap) -> Unit) {
        if (url.isNullOrEmpty()) return
        val app = context.applicationContext
        thread(isDaemon = true, name = "LateAvatar") {
            val icon = CoilHelper.loadBitmapWithin(app, url, WAIT_MILLIS) ?: return@thread
            runCatching { onLoaded(icon) }
        }
    }

    /** The ids of this app's notifications still showing (one the user swept away is not redone). */
    fun shownIds(context: Context): Set<Int> = runCatching {
        NotificationManagerCompat.from(context).activeNotifications.map { it.id }.toSet()
    }.getOrDefault(emptySet())
}
