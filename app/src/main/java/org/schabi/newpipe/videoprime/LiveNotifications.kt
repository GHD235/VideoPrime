package org.schabi.newpipe.videoprime

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.twitch.TwitchLiveChannels
import org.schabi.newpipe.extractor.services.twitch.linkHandler.TwitchChannelLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: tells when a subscribed Twitch channel (with notifications on) goes live. Its own
 * check runs every 5 minutes ([LiveCheckWorker]: one small request for all the channels), apart
 * from the slower check for new videos; each live stream is told only once.
 */
object LiveNotifications {
    /** A channel of its own, that pops up on the screen like the Twitch app's. */
    const val CHANNEL_ID = "videoprime_live"

    private const val TAG = "VideoPrimeLive"
    private const val PREF_LIVE_NOW = "videoprime_twitch_live_now"

    /** Channel URL and when it was last seen live, as "url|millis". */
    private const val PREF_LIVE_SEEN = "videoprime_twitch_live_seen"

    /**
     * A channel missing from one check (a reconnection, a hiccup of Twitch's answer) that comes
     * back within this time is the same live stream: it is not told again.
     */
    private const val SAME_STREAM_MILLIS = 30 * 60 * 1000L
    private const val WORK_NAME = "videoprime_live_check"
    private const val INTERVAL_MINUTES = 5L

    /** With nothing to watch (no Twitch channels, notifications off), look again later. */
    private const val IDLE_INTERVAL_MINUTES = 30L

    /**
     * Asks Twitch who is live and tells the new ones. Must run off the main thread.
     * @return whether there is something to watch (Twitch channels with notifications on)
     */
    @Synchronized
    fun check(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!NotificationHelper.areNewStreamsNotificationsEnabled(context)) return false
        val logins = NewPipeDatabase.getInstance(context).subscriptionDAO().getAll()
            .blockingFirst(emptyList())
            .filter {
                it.serviceId == ServiceList.Twitch.serviceId &&
                    it.notificationMode == NotificationMode.ENABLED
            }
            .mapNotNull {
                runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(it.url) }
                    .getOrNull()
            }
        if (logins.isEmpty()) {
            prefs.edit { remove(PREF_LIVE_SEEN) }
            return false
        }

        val live = TwitchLiveChannels.fetch(logins)
        BackgroundStatus.markTwitchCheck(context)
        val now = System.currentTimeMillis()
        val seen = prefs.getStringSet(PREF_LIVE_SEEN, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val at = entry.substringAfterLast('|').toLongOrNull() ?: return@mapNotNull null
                entry.substringBeforeLast('|') to at
            }
            .filter { (_, at) -> now - at < SAME_STREAM_MILLIS }
            .toMap()
        val started = live.filter { it.url !in seen }
        // remembered before telling: a crash while notifying must not repeat the alerts
        val updated = seen + live.associate { it.url to now }
        prefs.edit {
            remove(PREF_LIVE_NOW)
            putStringSet(PREF_LIVE_SEEN, updated.map { (url, at) -> "$url|$at" }.toSet())
        }
        started.forEach { notify(context, it) }
        Log.i(TAG, "${logins.size} Twitch channels, ${live.size} live, ${started.size} new")
        return true
    }

    /** Starts the checks (keeps the one already waiting, if any). */
    @JvmStatic
    fun schedule(context: Context) = enqueue(context, 0, ExistingWorkPolicy.KEEP)

    private fun enqueue(context: Context, delayMinutes: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<LiveCheckWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private fun notify(context: Context, item: StreamInfoItem) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val avatar = ImageStrategy.choosePreferredImage(item.uploaderAvatars)
        val icon = CoilHelper.loadBitmapBlocking(context, avatar, R.drawable.ic_live_tv)
        val title = context.getString(R.string.videoprime_live_now, item.uploaderName)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setLargeIcon(icon)
            .setContentTitle(title)
            .setContentText(item.name)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.name))
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setWhen(item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(
                PendingIntentCompat.getActivity(
                    context,
                    item.url.hashCode(),
                    NavigationHelper.getStreamIntent(context, item.serviceId, item.url, item.name),
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false
                )
            )
            .build()
        runCatching { manager.notify(item.url.hashCode(), notification) }
    }

    /** Checks who is live, then plans the next check (a periodic work cannot run this often). */
    class LiveCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val watching = try {
                check(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Could not check Twitch live channels", e)
                true
            }
            // stopped by the system (no network...): it runs again by itself, and planning the
            // next check here too would leave two of them, then three...
            if (isStopped) return Result.success()
            // the next one runs after this one (APPEND: this work is still running now), unless
            // one is already waiting
            val waiting = runCatching {
                WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWork(WORK_NAME).get()
            }.getOrDefault(emptyList()).any {
                it.id != id && (it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED)
            }
            if (!waiting) {
                enqueue(
                    applicationContext,
                    if (watching) INTERVAL_MINUTES else IDLE_INTERVAL_MINUTES,
                    ExistingWorkPolicy.APPEND_OR_REPLACE
                )
            }
            return Result.success()
        }
    }
}
