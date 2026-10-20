package org.schabi.newpipe.videoprime

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import androidx.core.util.AtomicFile
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.image.ImageStrategy

/** A song kept on the phone to listen to it without connection. */
data class OfflineItem(
    val serviceId: Int,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val duration: Long,
    /** Name of the file in [OfflineMusic.dir]. */
    val file: String,
    val size: Long,
    val lastPlayed: Long,
    /** Kept because it is in a playlist or queue marked "available offline". */
    val pinned: Boolean
)

/** A song to save, with what is known about it until it is downloaded. */
data class OfflineWanted(
    val serviceId: Int,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val duration: Long
)

/** A playlist or queue whose songs are kept offline. */
data class OfflineCollection(
    /** [TYPE_LOCAL] (a playlist of the app), [TYPE_REMOTE] (a YouTube playlist) or [TYPE_QUEUE]. */
    val type: String,
    /** Playlist id, playlist URL or queue id. */
    val ref: String,
    val name: String
) {
    companion object {
        const val TYPE_LOCAL = "local"
        const val TYPE_REMOTE = "remote"
        const val TYPE_QUEUE = "queue"
    }
}

/**
 * VideoPrime: music to listen to without connection, without downloading it by hand. The songs
 * listened to are saved (when on Wi-Fi, by default) and so are all the songs of the playlists and
 * queues marked "available offline". Everything goes to a private folder of the app, with a size
 * limit: when it is full the songs listened to longest ago are removed first. Without connection,
 * or when the connection fails, the player takes the saved copy.
 */
object OfflineMusic {
    private const val KEY_ENABLED = "videoprime_offline_enabled"
    private const val KEY_LIMIT_MB = "videoprime_offline_limit_mb"
    private const val KEY_WIFI_ONLY = "videoprime_offline_wifi_only"
    private const val WORK_NAME = "videoprime_offline_music"

    /** Size choices in MB, 2 GB by default: about 500 songs. */
    val LIMITS_MB = intArrayOf(500, 1024, 2048, 5120, 10240)
    private const val DEFAULT_LIMIT_MB = 2048

    /** Longer streams are not music (podcasts, streams, long videos), unless a list asks for them. */
    const val MAX_LISTENED_SECONDS = 20 * 60L

    private val lock = Any()

    /** Index changes made from the player happen off the main thread, one after another. */
    private val io = Executors.newSingleThreadExecutor()

    data class Index(
        val items: List<OfflineItem> = emptyList(),
        val wanted: List<OfflineWanted> = emptyList(),
        val collections: List<OfflineCollection> = emptyList(),
        /** Songs of the marked lists that are not saved yet, as last counted by the worker. */
        val pinnedPending: Int = 0
    ) {
        val usedBytes: Long get() = items.sumOf { it.size }
    }

    // region Settings

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }

    fun limitMb(context: Context) = prefs(context).getInt(KEY_LIMIT_MB, DEFAULT_LIMIT_MB)

    fun setLimitMb(context: Context, mb: Int) {
        prefs(context).edit { putInt(KEY_LIMIT_MB, mb) }
        trimTo(context, mb * MB)
        schedule(context, replace = true)
    }

    fun isWifiOnly(context: Context) = prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        prefs(context).edit { putBoolean(KEY_WIFI_ONLY, wifiOnly) }
        // the work waiting for Wi-Fi is replaced, so mobile data is used right away
        schedule(context, replace = true)
    }

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    // endregion
    // region Index

    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "offline_music").apply { mkdirs() }

    private fun indexFile(context: Context) = File(context.applicationContext.filesDir, "videoprime_offline.json")

    fun load(context: Context): Index = synchronized(lock) {
        val file = indexFile(context)
        if (!file.exists()) return Index()
        return runCatching { parse(JSONObject(String(AtomicFile(file).readFully()))) }
            .getOrDefault(Index())
    }

    /** Changes the index atomically. */
    fun update(context: Context, change: (Index) -> Index): Index = synchronized(lock) {
        val updated = change(load(context))
        writeAtomically(indexFile(context), toJson(updated).toString())
        return updated
    }

    // endregion
    // region Playback

    /** @return the saved copy of a song whose file is still there, or null */
    fun find(context: Context, serviceId: Int, url: String): OfflineItem? = load(context).items.firstOrNull { it.serviceId == serviceId && it.url == url }
        ?.takeIf { File(dir(context), it.file).exists() }

    /** @return whether the phone has a connection to the internet */
    @JvmStatic
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Stream info that plays the saved copy of a song: its own title, channel and thumbnail and
     * the audio file. It is marked as listened to now, so it is kept longer.
     *
     * @return the info, or null if the song is not saved
     */
    @JvmStatic
    fun offlineStreamInfo(context: Context, serviceId: Int, url: String): StreamInfo? {
        val item = find(context, serviceId, url) ?: return null
        update(context) { index ->
            index.copy(
                items = index.items.map {
                    if (it === item || it.url == item.url && it.serviceId == item.serviceId) {
                        it.copy(lastPlayed = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            )
        }
        val fileUrl = File(dir(context), item.file).toURI().toString()
        val info = StreamInfo(
            item.serviceId,
            item.url,
            item.url,
            StreamType.AUDIO_STREAM,
            videoIdOf(item.url),
            item.title,
            0
        )
        info.uploaderName = item.uploader
        info.duration = item.duration
        info.thumbnails = ImageStrategy.dbUrlToImageList(item.thumbnailUrl)
        info.audioStreams = listOf(
            AudioStream.Builder()
                .setId(Stream.ID_UNKNOWN)
                .setContent(fileUrl, true)
                .setMediaFormat(
                    MediaFormat.getFromSuffix(item.file.substringAfterLast('.')) ?: MediaFormat.M4A
                )
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setAverageBitrate(AudioStream.UNKNOWN_BITRATE)
                .build()
        )
        return info
    }

    /**
     * Called when the player starts a stream. Music (what plays in the background, YouTube
     * Music songs) is saved to listen to it later without connection.
     */
    @JvmStatic
    fun onPlayed(context: Context, info: StreamInfo, audioOnly: Boolean) {
        if (!isEnabled(context) || !isSavable(info)) return
        val isMusic = audioOnly || info.category.equals("Music", ignoreCase = true) ||
            info.uploaderName?.endsWith(" - Topic") == true
        if (!isMusic || info.duration !in 1..MAX_LISTENED_SECONDS) return
        if (info.audioStreams.any { it.content.startsWith("file:") }) return

        val app = context.applicationContext
        io.execute { remember(app, info) }
    }

    private fun remember(app: Context, info: StreamInfo) {
        update(app) { index ->
            if (index.items.any { it.url == info.url && it.serviceId == info.serviceId }) {
                index.copy(
                    items = index.items.map {
                        if (it.url == info.url && it.serviceId == info.serviceId) {
                            it.copy(lastPlayed = System.currentTimeMillis())
                        } else {
                            it
                        }
                    }
                )
            } else if (index.wanted.any { it.url == info.url }) {
                index
            } else {
                index.copy(
                    wanted = index.wanted + OfflineWanted(
                        info.serviceId,
                        info.url,
                        info.name,
                        info.uploaderName ?: "",
                        ImageStrategy.choosePreferredImage(info.thumbnails),
                        info.duration
                    )
                )
            }
        }
        schedule(app, LISTENED_DELAY_SECONDS)
    }

    /** Time for the player to start a song before saving it, using the same connection. */
    private const val LISTENED_DELAY_SECONDS = 90L

    /** Only YouTube songs can be saved: live streams and Twitch have no audio file to keep. */
    fun isSavable(info: StreamInfo) = info.serviceId == ServiceList.YouTube.serviceId &&
        info.streamType != StreamType.LIVE_STREAM &&
        info.streamType != StreamType.AUDIO_LIVE_STREAM

    // endregion
    // region Playlists and queues

    @JvmStatic
    fun isPinned(context: Context, type: String, ref: String) = load(context).collections.any { it.type == type && it.ref == ref }

    /** Marks or unmarks a playlist or queue as available offline. */
    @JvmStatic
    fun setPinned(context: Context, collection: OfflineCollection, pinned: Boolean) {
        update(context) { index ->
            val others = index.collections.filterNot {
                it.type == collection.type && it.ref == collection.ref
            }
            val collections = if (pinned) others + collection else others
            // with no list left the worker may not run again: free its songs right away so
            // they can make room (otherwise the worker recalculates them)
            val items = if (collections.isEmpty()) index.items.map { it.copy(pinned = false) } else index.items
            index.copy(collections = collections, items = items, pinnedPending = if (collections.isEmpty()) 0 else index.pinnedPending)
        }
        schedule(context)
    }

    /**
     * Marks a playlist or queue as available offline, or unmarks it, telling the user.
     *
     * @return whether it is marked now
     */
    @JvmStatic
    fun togglePinned(context: Context, collection: OfflineCollection): Boolean {
        val pinned = !isPinned(context, collection.type, collection.ref)
        setPinned(context, collection, pinned)
        if (pinned) {
            android.widget.Toast.makeText(context, R.string.offline_pinned, android.widget.Toast.LENGTH_LONG)
                .show()
        }
        return pinned
    }

    // endregion
    // region Space

    /** Removes the songs listened to longest ago, never the pinned ones, to fit in [bytes]. */
    fun trimTo(context: Context, bytes: Long) {
        update(context) { index -> evict(context, index, bytes) }
    }

    /** @return the index without the oldest songs that are not pinned, until it fits in [bytes] */
    fun evict(context: Context, index: Index, bytes: Long): Index {
        var used = index.usedBytes
        if (used <= bytes) return index
        val removed = HashSet<OfflineItem>()
        for (item in index.items.filterNot { it.pinned }.sortedBy { it.lastPlayed }) {
            if (used <= bytes) break
            File(dir(context), item.file).delete()
            used -= item.size
            removed += item
        }
        return index.copy(items = index.items.filterNot { it in removed })
    }

    /** Deletes every saved song and what was waiting to be saved; the lists stay marked. */
    fun clear(context: Context) {
        update(context) { index ->
            dir(context).listFiles()?.forEach { it.delete() }
            index.copy(items = emptyList(), wanted = emptyList())
        }
    }

    const val MB = 1024L * 1024L

    // endregion

    /**
     * Starts saving what is waiting, on Wi-Fi or any connection depending on the settings.
     *
     * @param delaySeconds how long to wait first, so that saving a song does not take bandwidth
     *                     from the player while it starts playing it
     */
    @JvmStatic
    @JvmOverloads
    fun schedule(context: Context, delaySeconds: Long = 0, replace: Boolean = false) {
        val app = context.applicationContext
        // nothing to save: no work waiting for a connection piles up
        val index = load(app)
        if (index.collections.isEmpty() && index.wanted.isEmpty()) return
        val network = if (isWifiOnly(app)) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<OfflineMusicWorker>()
            .setInitialDelay(delaySeconds, java.util.concurrent.TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(network)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        val workManager = WorkManager.getInstance(app)
        val states = runCatching { workManager.getWorkInfosForUniqueWork(WORK_NAME).get() }
            .getOrDefault(emptyList())
            .map { it.state }
        val running = WorkInfo.State.RUNNING in states
        val waiting = states.count { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }
        val policy = when {
            // the settings changed: the queued work has the old network constraint
            replace -> ExistingWorkPolicy.REPLACE

            // a request made while the worker runs (a list just marked) runs after it; one is
            // enough, it reads everything that is pending
            running -> if (waiting == 0) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP

            // earlier versions chained one work per song while waiting for Wi-Fi: start clean
            waiting > 1 -> ExistingWorkPolicy.REPLACE

            // one already waits for its network: it will save this too
            else -> ExistingWorkPolicy.KEEP
        }
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private fun videoIdOf(url: String): String = Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1) ?: url

    // region JSON

    private fun parse(json: JSONObject) = Index(
        items = json.optJSONArray("items").objects().map {
            OfflineItem(
                serviceId = it.getInt("service"),
                url = it.getString("url"),
                title = it.optString("title"),
                uploader = it.optString("uploader"),
                thumbnailUrl = it.optString("thumbnail").ifEmpty { null },
                duration = it.optLong("duration"),
                file = it.getString("file"),
                size = it.optLong("size"),
                lastPlayed = it.optLong("played"),
                pinned = it.optBoolean("pinned")
            )
        },
        wanted = json.optJSONArray("wanted").objects().map {
            OfflineWanted(
                serviceId = it.getInt("service"),
                url = it.getString("url"),
                title = it.optString("title"),
                uploader = it.optString("uploader"),
                thumbnailUrl = it.optString("thumbnail").ifEmpty { null },
                duration = it.optLong("duration")
            )
        },
        collections = json.optJSONArray("collections").objects().map {
            OfflineCollection(it.getString("type"), it.getString("ref"), it.optString("name"))
        },
        pinnedPending = json.optInt("pinnedPending")
    )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun toJson(index: Index) = JSONObject()
        .put("pinnedPending", index.pinnedPending)
        .put(
            "items",
            JSONArray(
                index.items.map {
                    JSONObject()
                        .put("service", it.serviceId)
                        .put("url", it.url)
                        .put("title", it.title)
                        .put("uploader", it.uploader)
                        .put("thumbnail", it.thumbnailUrl ?: "")
                        .put("duration", it.duration)
                        .put("file", it.file)
                        .put("size", it.size)
                        .put("played", it.lastPlayed)
                        .put("pinned", it.pinned)
                }
            )
        )
        .put(
            "wanted",
            JSONArray(
                index.wanted.map {
                    JSONObject()
                        .put("service", it.serviceId)
                        .put("url", it.url)
                        .put("title", it.title)
                        .put("uploader", it.uploader)
                        .put("thumbnail", it.thumbnailUrl ?: "")
                        .put("duration", it.duration)
                }
            )
        )
        .put(
            "collections",
            JSONArray(
                index.collections.map {
                    JSONObject().put("type", it.type).put("ref", it.ref).put("name", it.name)
                }
            )
        )

    // endregion
}
