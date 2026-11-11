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
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.util.DeviceUtils
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

    /**
     * PulsePlay: with the instant notifications working, Twitch's own notice reaches the phone in
     * about 20 s through the server: this check is then only the safety net.
     */
    private const val BACKUP_INTERVAL_MINUTES = 15L

    /** With nothing to watch (no Twitch channels, notifications off), look again later. */
    private const val IDLE_INTERVAL_MINUTES = 30L

    /** Who was told (PREF_LIVE_SEEN) is read and written under this, by check() and onPush(). */
    private val seenLock = Any()

    /**
     * Asks Twitch who is live and tells the new ones. Must run off the main thread.
     * @return whether there is something to watch (Twitch channels with notifications on)
     */
    fun check(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!NotificationHelper.areNewStreamsNotificationsEnabled(context)) return false
        val twitch = NewPipeDatabase.getInstance(context).subscriptionDAO().getAll()
            .blockingFirst(emptyList())
            .filter { it.serviceId == ServiceList.Twitch.serviceId }
        val loginOfChannel = twitch.associateWith {
            runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(it.url) }.getOrNull()
        }
        val logins = twitch.filter { it.notificationMode == NotificationMode.ENABLED }
            .mapNotNull { loginOfChannel[it] }
        if (logins.isEmpty()) {
            prefs.edit { remove(PREF_LIVE_SEEN) }
            return false
        }

        // all the Twitch channels in the same request: the home screen shows them at once
        val allLive = TwitchLiveChannels.fetch(loginOfChannel.values.filterNotNull().distinct())
        HomeLiveCache.write(context, allLive)
        val bellLogins = logins.map { it.lowercase() }.toSet()
        val live = allLive.filter { loginOf(it.url) in bellLogins }
        BackgroundStatus.markTwitchCheck(context)
        // PulsePlay: only the memory of who was told is locked (shared with onPush), not the
        // request above: a push arriving during a check is told at once, not after it
        val started = synchronized(seenLock) {
            val now = System.currentTimeMillis()
            val seen = seen(context, now)
            // compared by login: the push (onPush) writes the address in its own way
            val seenLogins = seen.keys.mapNotNull(::loginOf).toSet()
            val started = live.filter { (loginOf(it.url) ?: it.url) !in seenLogins && it.url !in seen }
            // remembered before telling: a crash while notifying must not repeat the alerts
            val updated = seen + live.associate { it.url to now }
            prefs.edit(commit = true) {
                remove(PREF_LIVE_NOW)
                putStringSet(PREF_LIVE_SEEN, updated.map { (url, at) -> "$url|$at" }.toSet())
            }
            started
        }
        started.forEach { notify(context, it) }
        Log.i(TAG, "${logins.size} Twitch channels, ${live.size} live, ${started.size} new")
        return true
    }

    private fun seen(context: Context, now: Long): Map<String, Long> = PreferenceManager.getDefaultSharedPreferences(context)
        .getStringSet(PREF_LIVE_SEEN, emptySet()).orEmpty()
        .mapNotNull { entry ->
            val at = entry.substringAfterLast('|').toLongOrNull() ?: return@mapNotNull null
            entry.substringBeforeLast('|') to at
        }
        .filter { (_, at) -> now - at < SAME_STREAM_MILLIS }
        .toMap()

    private fun loginOf(streamUrl: String): String? = runCatching {
        ServiceList.Twitch.streamLHFactory.getId(streamUrl)
    }.getOrNull()?.lowercase()

    /**
     * The push server says [login] has just gone live: told AT ONCE from what the push says (the
     * title comes from Twitch's own API on the server), with the same memory as the usual check,
     * so it is never told twice; [InstantPush] runs the check afterwards (home screen's live row). Before,
     * the check (a request to Twitch and the live images) ran first and delayed the alert, and a
     * slow network could use up the time Android gives a push. Must run off the main thread.
     */
    fun onPush(context: Context, login: String, name: String, title: String): String = synchronized(seenLock) {
        // PulsePlay: what happened is returned, for the notifications' status (a missing alert
        // could not be explained before: bell off, notifications blocked or already told?)
        val now = System.currentTimeMillis()
        val seen = seen(context, now)
        if (seen.keys.any { loginOf(it) == login }) return@synchronized InstantPush.RESULT_TOLD
        val subscription = NewPipeDatabase.getInstance(context).subscriptionDAO().getAll()
            .blockingFirst(emptyList())
            .firstOrNull {
                it.serviceId == ServiceList.Twitch.serviceId &&
                    it.notificationMode == NotificationMode.ENABLED &&
                    runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(it.url) }
                        .getOrNull()?.lowercase() == login
            } ?: return@synchronized InstantPush.RESULT_NO_BELL
        val url = runCatching { ServiceList.Twitch.streamLHFactory.getUrl(login) }.getOrNull()
            ?: return@synchronized InstantPush.RESULT_ERROR
        val item = StreamInfoItem(ServiceList.Twitch.serviceId, url, title.ifBlank { name }, StreamType.LIVE_STREAM)
        item.uploaderName = subscription.name ?: name
        item.uploaderAvatars = ImageStrategy.dbUrlToImageList(subscription.avatarUrl)
        // remembered before telling: the check below (or a crash) must not repeat it
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putStringSet(PREF_LIVE_SEEN, (seen + (url to now)).map { (u, at) -> "$u|$at" }.toSet())
        }
        val shown = notify(context, item)
        Log.i(TAG, "$login told from the push (shown: $shown)")
        if (shown) InstantPush.RESULT_SHOWN else InstantPush.RESULT_BLOCKED
    }

    /**
     * PulsePlay: whether a live alert can be seen: notifications allowed for the app and the
     * "Directos" channel not blocked.
     */
    @JvmStatic
    fun canShow(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return true
        val channel = context.getSystemService(android.app.NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID) ?: return true
        return channel.importance != android.app.NotificationManager.IMPORTANCE_NONE
    }

    /** PulsePlay: whether live alerts would not pop up on the screen (channel lowered by the user). */
    @JvmStatic
    fun popupsOff(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(android.app.NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance != android.app.NotificationManager.IMPORTANCE_NONE &&
            channel.importance < android.app.NotificationManager.IMPORTANCE_HIGH
    }

    /** Starts the checks (keeps the one already waiting, if any). */
    @JvmStatic
    fun schedule(context: Context) = enqueue(context, 0, ExistingWorkPolicy.KEEP)

    private fun enqueue(context: Context, delayMinutes: Long, policy: ExistingWorkPolicy) {
        // PulsePlay: not on a TV (its alerts are hardly seen; Inicio asks Twitch on opening)
        if (DeviceUtils.isTv(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<LiveCheckWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private fun notify(context: Context, item: StreamInfoItem): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!canShow(context)) return false
        val avatar = ImageStrategy.choosePreferredImage(item.uploaderAvatars)
        // PulsePlay: 3 s at most for the avatar, so the alert is never held back by it
        val icon = CoilHelper.loadBitmapWithin(context, avatar, NotificationHelper.ICON_TIMEOUT_MILLIS)
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
        return runCatching { manager.notify(item.url.hashCode(), notification) }.isSuccess
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
                    when {
                        !watching -> IDLE_INTERVAL_MINUTES
                        CheckAlarm.pushProven(applicationContext) -> BACKUP_INTERVAL_MINUTES
                        else -> INTERVAL_MINUTES
                    },
                    ExistingWorkPolicy.APPEND_OR_REPLACE
                )
            }
            return Result.success()
        }
    }
}
