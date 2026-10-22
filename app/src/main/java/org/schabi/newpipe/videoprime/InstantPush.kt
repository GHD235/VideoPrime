package org.schabi.newpipe.videoprime

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.services.twitch.linkHandler.TwitchChannelLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.ExtractorHelper

/**
 * VideoPrime: instant notifications. Our push server (push-server/ in the repository) hears
 * from YouTube (WebSub) when a channel uploads and from Twitch (EventSub) when a channel goes
 * live, and passes it on with Firebase Cloud Messaging, one topic per channel. The app
 * subscribes to the topics of the channels with the bell on and tells the server which channels
 * to watch; neither Firebase nor the server learn who the user is. The usual checks go on as
 * they were (a push can be lost), and a video is never told twice.
 */
object InstantPush {
    private const val TAG = "VideoPrimePush"
    const val PREF_ENABLED = "videoprime_instant_push"
    private const val PREF_TOPICS = "videoprime_push_topics"
    private const val PREF_REGISTERED_AT = "videoprime_push_registered_at"
    private const val PREF_REGISTERED_HASH = "videoprime_push_registered_hash"
    private const val PREF_LAST_PUSH = "videoprime_last_push"

    /** Videos told by a push, as "url|millis": the usual check does not tell them again. */
    private const val PREF_PUSHED = "videoprime_pushed_videos"
    private const val PUSHED_KEEP_MILLIS = 3 * 24 * 3600 * 1000L

    /** The server forgets channels nobody asked for in 30 days: they are asked again daily. */
    private const val REGISTER_EVERY_MILLIS = 24 * 3600 * 1000L

    /** Firebase allows 2000 topics per app install. */
    private const val MAX_TOPICS = 1900
    private const val TIMEOUT_SECONDS = 30L
    private const val SYNC_DELAY_SECONDS = 5L
    private const val UPDATES_TOPIC = "vp_updates"
    private const val DETAILS_TIMEOUT_SECONDS = 12L

    private val YOUTUBE_CHANNEL = Regex("UC[A-Za-z0-9_-]{22}")
    private val TWITCH_LOGIN = Regex("[a-z0-9_]{1,25}")
    private val YOUTUBE_VIDEO = Regex("[A-Za-z0-9_-]{11}")

    @Volatile
    private var initialized = false

    /** Syncing may take minutes: it has its own lock, so that pushes are never kept waiting. */
    private val syncLock = Any()
    private val pushedLock = Any()

    /** Firebase gave a new token: the topics of the old one are gone, all are subscribed again. */
    private val tokenChanged = AtomicBoolean(false)

    /** Whether this build has the Firebase config and the server's address (see CI secrets). */
    @JvmStatic
    fun isConfigured(): Boolean = BuildConfig.FIREBASE_APP_ID.isNotEmpty() &&
        BuildConfig.PUSH_SERVER_URL.isNotEmpty()

    @JvmStatic
    fun isEnabled(context: Context): Boolean = isConfigured() &&
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLED, false)

    /** Sets Firebase up (no network: nothing is asked of Firebase until the user turns it on). */
    @JvmStatic
    fun initialize(context: Context) {
        if (!isConfigured()) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build()
                )
            }
            initialized = true
            watchSubscriptions(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Could not set Firebase up", e)
        }
    }

    private var watcher: Disposable? = null

    /**
     * Following a channel, leaving it, importing or ringing a bell: the topics and the server
     * follow a few seconds later, not at the next background check.
     */
    private fun watchSubscriptions(app: Context) {
        if (watcher != null) return
        watcher = NewPipeDatabase.getInstance(app).subscriptionDAO().getAll()
            .map { list ->
                list.filter { it.notificationMode == NotificationMode.ENABLED }.mapNotNull { it.url }.toSet()
            }
            .distinctUntilChanged()
            // the first list is the one at start, synced when the app opens
            .skip(1)
            .debounce(SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
            .subscribe({ syncInBackground(app) }, { Log.w(TAG, "Could not watch the subscriptions", it) })
    }

    @JvmStatic
    fun syncInBackground(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        Schedulers.io().scheduleDirect {
            try {
                sync(app, force)
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync the push topics", e)
            }
        }
    }

    /**
     * Subscribes to the topics of the channels with the bell on (and leaves the others), and
     * tells the server which channels to watch when they changed or once a day.
     */
    @WorkerThread
    fun sync(context: Context, force: Boolean = false): Unit = synchronized(syncLock) {
        syncLocked(context, force)
    }

    private fun syncLocked(context: Context, force: Boolean) {
        if (!isConfigured() || !initialized) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val messaging = FirebaseMessaging.getInstance()
        if (!isEnabled(context) || !NotificationHelper.areNewStreamsNotificationsEnabled(context)) {
            if (messaging.isAutoInitEnabled || prefs.contains(PREF_TOPICS)) {
                // without the token, the install is off every topic at once
                messaging.isAutoInitEnabled = false
                Tasks.await(messaging.deleteToken(), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                prefs.edit {
                    remove(PREF_TOPICS)
                    remove(PREF_REGISTERED_HASH)
                }
                Log.i(TAG, "Instant notifications off")
            }
            return
        }

        messaging.isAutoInitEnabled = true
        val (youtube, twitch) = watchedChannels(context)
        // plus the new versions of the app, told as soon as they are published
        val wanted = (listOf(UPDATES_TOPIC) + youtube.map { "yt_$it" } + twitch.map { "tw_$it" })
            .take(MAX_TOPICS).toSet()
        val current = if (tokenChanged.getAndSet(false)) {
            mutableSetOf()
        } else {
            prefs.getStringSet(PREF_TOPICS, emptySet()).orEmpty().toMutableSet()
        }
        try {
            for (topic in wanted - current) {
                Tasks.await(messaging.subscribeToTopic(topic), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                current += topic
            }
            for (topic in current - wanted) {
                Tasks.await(messaging.unsubscribeFromTopic(topic), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                current -= topic
            }
        } finally {
            // what was done is kept, the rest is done next time
            prefs.edit { putStringSet(PREF_TOPICS, current.toSet()) }
        }

        val hash = wanted.sorted().joinToString(",").hashCode()
        val registeredAt = prefs.getLong(PREF_REGISTERED_AT, 0L)
        if (force || hash != prefs.getInt(PREF_REGISTERED_HASH, 0) ||
            System.currentTimeMillis() - registeredAt > REGISTER_EVERY_MILLIS
        ) {
            register(youtube, twitch)
            prefs.edit {
                putInt(PREF_REGISTERED_HASH, hash)
                putLong(PREF_REGISTERED_AT, System.currentTimeMillis())
            }
        }
        Log.i(TAG, "${youtube.size} YouTube and ${twitch.size} Twitch channels on instant notifications")
    }

    private fun bellSubscriptions(context: Context): List<SubscriptionEntity> = NewPipeDatabase.getInstance(context).subscriptionDAO().getAll()
        .blockingFirst(emptyList())
        .filter { it.notificationMode == NotificationMode.ENABLED }

    private fun youtubeChannelOf(subscription: SubscriptionEntity): String? = if (subscription.serviceId == ServiceList.YouTube.serviceId) {
        subscription.url?.let { YOUTUBE_CHANNEL.find(it)?.value }
    } else {
        null
    }

    private fun twitchLoginOf(subscription: SubscriptionEntity): String? = if (subscription.serviceId == ServiceList.Twitch.serviceId) {
        runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(subscription.url) }
            .getOrNull()?.lowercase()?.takeIf { TWITCH_LOGIN.matches(it) }
    } else {
        null
    }

    private fun watchedChannels(context: Context): Pair<List<String>, List<String>> {
        val subscriptions = bellSubscriptions(context)
        return subscriptions.mapNotNull(::youtubeChannelOf).distinct() to
            subscriptions.mapNotNull(::twitchLoginOf).distinct()
    }

    private fun register(youtube: List<String>, twitch: List<String>) {
        val body = JSONObject()
            .put("youtube", JSONArray(youtube))
            .put("twitch", JSONArray(twitch))
            .toString()
            .toByteArray()
        val connection = URL(BuildConfig.PUSH_SERVER_URL + "/register").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) error("The push server answered $code")
        } finally {
            connection.disconnect()
        }
    }

    // region received pushes

    /** Called by [Service] for each push. Runs on Firebase's background thread. */
    @WorkerThread
    fun onPush(context: Context, data: Map<String, String>) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putLong(PREF_LAST_PUSH, System.currentTimeMillis())
        }
        if (!isEnabled(context) || !NotificationHelper.areNewStreamsNotificationsEnabled(context)) return
        when (data["t"]) {
            "yt" -> onYouTubeVideo(context, data)

            "up" -> UpdateChecker.onPush(context, data["c"].orEmpty(), data["u"].orEmpty())

            "tw" -> {
                val login = data["l"]?.lowercase()?.takeIf { TWITCH_LOGIN.matches(it) } ?: return
                LiveNotifications.onPush(context, login, data["n"].orEmpty(), data["s"].orEmpty())
            }
        }
    }

    private fun onYouTubeVideo(context: Context, data: Map<String, String>) {
        val channel = data["c"]?.takeIf { YOUTUBE_CHANNEL.matches(it) } ?: return
        val video = data["v"]?.takeIf { YOUTUBE_VIDEO.matches(it) } ?: return
        val subscription = bellSubscriptions(context).firstOrNull { youtubeChannelOf(it) == channel } ?: return
        val channelUrl = subscription.url ?: return
        val url = "https://www.youtube.com/watch?v=$video"
        val serviceId = ServiceList.YouTube.serviceId
        // already told by the usual check (it found the video first), or by an earlier push
        if (wasPushed(context, url) ||
            NewPipeDatabase.getInstance(context).streamDAO().exists(serviceId, url)
        ) {
            return
        }
        markPushed(context, url)

        val item = StreamInfoItem(serviceId, url, data["n"].orEmpty(), StreamType.VIDEO_STREAM)
        item.uploaderName = subscription.name ?: data["a"].orEmpty()
        item.uploaderUrl = channelUrl
        item.thumbnails = listOf(
            Image("https://i.ytimg.com/vi/$video/hqdefault.jpg", 360, 480, Image.ResolutionLevel.MEDIUM)
        )
        runCatching { OffsetDateTime.parse(data["p"]) }.getOrNull()?.let {
            item.uploadDate = DateWrapper(it)
        }
        val info = FeedUpdateInfo(
            uid = subscription.uid,
            notificationMode = NotificationMode.ENABLED,
            name = subscription.name ?: item.uploaderName,
            avatarUrl = subscription.avatarUrl,
            url = channelUrl,
            serviceId = serviceId,
            description = null,
            subscriberCount = null,
            streams = listOf(item),
            errors = emptyList()
        )
        info.newStreams = listOf(item)
        NotificationHelper(context).displayNewStreamsNotifications(info)
        Log.i(TAG, "New video of $channel told from the push")
        saveToFeed(context, subscription.uid, item)
    }

    /**
     * The video shows in Inicio at once, with what the push says; then its details (length, the
     * best thumbnail) from YouTube, if it answers in time.
     */
    private fun saveToFeed(context: Context, subscriptionId: Long, item: StreamInfoItem) {
        val feed = FeedDatabaseManager(context)
        try {
            feed.addStream(subscriptionId, StreamEntity(item))
        } catch (e: Exception) {
            Log.w(TAG, "Could not add the pushed video to the feed", e)
            return
        }
        try {
            val info = ExtractorHelper.getStreamInfo(item.serviceId, item.url, false)
                // Firebase gives a push about 20 s
                .timeout(DETAILS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .blockingGet()
            feed.addStream(subscriptionId, StreamEntity(info))
        } catch (e: Exception) {
            Log.w(TAG, "Could not load the pushed video's details", e)
        }
    }

    private fun pushed(context: Context): Map<String, Long> = synchronized(pushedLock) {
        val now = System.currentTimeMillis()
        PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(PREF_PUSHED, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val at = entry.substringAfterLast('|').toLongOrNull() ?: return@mapNotNull null
                entry.substringBeforeLast('|') to at
            }
            .filter { (_, at) -> now - at < PUSHED_KEEP_MILLIS }
            .toMap()
    }

    private fun markPushed(context: Context, url: String) = synchronized(pushedLock) {
        val updated = pushed(context) + (url to System.currentTimeMillis())
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putStringSet(PREF_PUSHED, updated.map { (u, at) -> "$u|$at" }.toSet())
        }
    }

    /** Whether a push already told this video (the usual check then leaves it out). */
    @JvmStatic
    fun wasPushed(context: Context, url: String): Boolean = url in pushed(context)

    /** For the notifications' status: when the last push arrived (0 = never). */
    @JvmStatic
    fun lastPush(context: Context): Long = PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_LAST_PUSH, 0L)

    @JvmStatic
    fun watchedCount(context: Context): Int = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(PREF_TOPICS, emptySet())
        ?.count { it != UPDATES_TOPIC } ?: 0
    // endregion

    /** Receives the pushes from Firebase. */
    class Service : FirebaseMessagingService() {
        override fun onMessageReceived(message: RemoteMessage) {
            try {
                onPush(applicationContext, message.data)
            } catch (e: Exception) {
                Log.e(TAG, "Could not handle a push", e)
            }
        }

        override fun onNewToken(token: String) {
            // the topics belong to the old token: all of them are subscribed again (by the next
            // sync, which may wait for the one running now)
            tokenChanged.set(true)
            syncInBackground(applicationContext, force = true)
        }
    }
}
