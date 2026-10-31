package org.schabi.newpipe.videoprime

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.image.ImageStrategy

/** One item of a saved queue. */
data class SavedItem(
    val serviceId: Int,
    val url: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val streamType: StreamType,
    val thumbnailUrl: String?
) {
    companion object {
        fun from(item: StreamInfoItem) = SavedItem(
            serviceId = item.serviceId,
            url = item.url,
            title = item.name,
            uploader = item.uploaderName ?: "",
            duration = item.duration,
            streamType = item.streamType,
            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails)
        )
    }
}

/** A queue saved by the user, with the item and position where it was left. */
data class SavedQueue(
    val id: Long,
    val name: String,
    val items: List<SavedItem>,
    val index: Int,
    val positionMillis: Long,
    val updated: Long
) {
    val current: SavedItem? get() = items.getOrNull(index)

    fun toPlayQueue(): PlayQueue {
        val infoItems = items.map { item ->
            StreamInfoItem(item.serviceId, item.url, item.title, item.streamType).also {
                it.uploaderName = item.uploader
                it.duration = item.duration
                it.thumbnails = ImageStrategy.dbUrlToImageList(item.thumbnailUrl)
            }
        }
        val start = index.coerceIn(0, (infoItems.size - 1).coerceAtLeast(0))
        return SinglePlayQueue(infoItems, start).also { queue ->
            if (positionMillis > 0) queue.setRecovery(start, positionMillis)
        }
    }

    companion object {
        fun from(id: Long, name: String, queue: PlayQueue, positionMillis: Long) = SavedQueue(
            id = id,
            name = name,
            items = queue.streams.map { item ->
                SavedItem(
                    serviceId = item.serviceId,
                    url = item.url,
                    title = item.title,
                    uploader = item.uploader,
                    duration = item.duration,
                    streamType = item.streamType,
                    thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails)
                )
            },
            index = queue.index,
            positionMillis = positionMillis,
            updated = System.currentTimeMillis()
        )
    }
}

/**
 * VideoPrime: the user's saved play queues (like Musicolet's multiple queues), kept as JSON in
 * the app's files, and which of them is playing.
 */
class QueueStore(private val file: File) {
    companion object {
        /** One lock for every QueueStore: several screens create their own. */
        private val LOCK = Any()
        private const val TAG = "PulsePlayQueues"

        /** PulsePlay: told after every save (the sync uploads the change). */
        @Volatile
        var onSaved: (() -> Unit)? = null

        /** An id for a new queue: two queues made in the same millisecond still differ. */
        fun newId(content: Content): Long = maxOf(System.currentTimeMillis(), (content.queues.maxOfOrNull { it.id } ?: 0L) + 1)
    }

    constructor(context: Context) :
        this(File(context.applicationContext.filesDir, "videoprime_queues.json"))

    data class Content(val queues: List<SavedQueue>, val activeId: Long?)

    fun load(): Content = synchronized(LOCK) {
        if (!file.exists()) return Content(emptyList(), null)
        return runCatching {
            val root = JSONObject(String(AtomicFile(file).readFully()))
            val queues = root.getJSONArray("queues").let { array ->
                (0 until array.length()).map { parseQueue(array.getJSONObject(it)) }
            }
            val active = root.optLong("active", -1).takeIf { it >= 0 }
            Content(queues, active)
        }.getOrElse { e ->
            // a damaged file is kept aside (it would be overwritten by the next save) instead of
            // looking like "no queues" for good
            runCatching { file.copyTo(File(file.path + ".damaged-" + System.currentTimeMillis())) }
            // (not available in unit tests)
            runCatching { Log.e(TAG, "Could not read the saved queues", e) }
            Content(emptyList(), null)
        }
    }

    fun save(content: Content): Unit = synchronized(LOCK) {
        val root = JSONObject()
            .put("queues", JSONArray(content.queues.map(::queueToJson)))
            .put("active", content.activeId ?: -1)
        writeAtomically(file, root.toString())
        onSaved?.invoke()
    }

    /** Adds items at the end of a saved queue. */
    fun append(queueId: Long, items: List<SavedItem>): Unit = synchronized(LOCK) {
        val content = load()
        save(
            content.copy(
                queues = content.queues.map { queue ->
                    if (queue.id == queueId) {
                        queue.copy(items = queue.items + items, updated = System.currentTimeMillis())
                    } else {
                        queue
                    }
                }
            )
        )
    }

    /** Saves a new queue with the given items, not playing yet. */
    fun create(name: String, items: List<SavedItem>): Unit = synchronized(LOCK) {
        val content = load()
        val now = System.currentTimeMillis()
        save(content.copy(queues = content.queues + SavedQueue(newId(content), name, items, 0, 0, now)))
    }

    internal fun queueToJson(queue: SavedQueue) = JSONObject()
        .put("id", queue.id)
        .put("name", queue.name)
        .put("index", queue.index)
        .put("position", queue.positionMillis)
        .put("updated", queue.updated)
        .put(
            "items",
            JSONArray(
                queue.items.map { item ->
                    JSONObject()
                        .put("service", item.serviceId)
                        .put("url", item.url)
                        .put("title", item.title)
                        .put("uploader", item.uploader)
                        .put("duration", item.duration)
                        .put("type", item.streamType.name)
                        .put("thumbnail", item.thumbnailUrl ?: "")
                }
            )
        )

    internal fun parseQueue(json: JSONObject): SavedQueue {
        val items = json.getJSONArray("items")
        return SavedQueue(
            id = json.getLong("id"),
            name = json.getString("name"),
            items = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                SavedItem(
                    serviceId = item.getInt("service"),
                    url = item.getString("url"),
                    title = item.optString("title"),
                    uploader = item.optString("uploader"),
                    duration = item.optLong("duration"),
                    streamType = runCatching { StreamType.valueOf(item.getString("type")) }
                        .getOrDefault(StreamType.VIDEO_STREAM),
                    thumbnailUrl = item.optString("thumbnail").ifEmpty { null }
                )
            },
            index = json.optInt("index"),
            positionMillis = json.optLong("position"),
            updated = json.optLong("updated")
        )
    }
}

/**
 * VideoPrime: writes a whole file so that it is either the old or the new version, never half
 * of it, even if the app is closed while writing.
 */
internal fun writeAtomically(file: File, text: String) {
    val atomic = AtomicFile(file)
    val out = atomic.startWrite()
    try {
        out.write(text.toByteArray())
        atomic.finishWrite(out)
    } catch (e: java.io.IOException) {
        atomic.failWrite(out)
        throw e
    }
}
