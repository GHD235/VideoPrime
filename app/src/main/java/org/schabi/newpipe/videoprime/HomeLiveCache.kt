package org.schabi.newpipe.videoprime

import android.content.Context
import android.util.Log
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: who was live on Twitch the last time anyone asked (the home screen, or the check
 * in the background every few minutes), so that "En directo ahora" shows as soon as the app opens.
 * Saved as plain JSON with only what the home screen draws.
 */
object HomeLiveCache {
    private const val TAG = "VideoPrimeLiveCache"
    private const val FILE = "videoprime_home_live.json"

    /**
     * Older than this, the list is not shown. A list a bit old is better than an empty row: the
     * home screen asks Twitch again at once and corrects it in a second.
     */
    private const val MAX_AGE_MILLIS = 6 * 3600_000L

    /** The saved list and when it was saved, if it is recent and newer than [newerThan]. */
    @JvmStatic
    fun read(context: Context, newerThan: Long): Pair<List<StreamInfoItem>, Long>? {
        val file = File(context.cacheDir, FILE)
        val savedAt = file.lastModified()
        if (savedAt <= newerThan || System.currentTimeMillis() - savedAt > MAX_AGE_MILLIS) return null
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) } to savedAt
        }.onFailure { Log.w(TAG, "Could not read the live list", it) }.getOrNull()
    }

    @JvmStatic
    @Synchronized
    fun write(context: Context, live: List<StreamInfoItem>) {
        runCatching {
            val array = JSONArray()
            live.forEach { array.put(toJson(it)) }
            val temp = File(context.cacheDir, "$FILE.tmp")
            temp.writeText(array.toString())
            temp.renameTo(File(context.cacheDir, FILE))
        }.onFailure { Log.w(TAG, "Could not save the live list", it) }
        prefetch(context, live)
    }

    /**
     * Twitch gives a live's preview a new address every few minutes, so on opening the app it was
     * almost never in the cache: the check in the background leaves it (and the avatar) ready.
     */
    private fun prefetch(context: Context, live: List<StreamInfoItem>) {
        val loader = context.imageLoader
        live.flatMap {
            listOfNotNull(
                ImageStrategy.choosePreferredImage(it.thumbnails),
                ImageStrategy.choosePreferredImage(it.uploaderAvatars)
            )
        }.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    // only to the disk: nothing is drawn now
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
    }

    /** How many channels were live at the last check and when it was, for the status screen. */
    @JvmStatic
    fun lastSaved(context: Context): Pair<Int, Long>? {
        val file = File(context.cacheDir, FILE)
        if (!file.exists()) return null
        val count = runCatching { JSONArray(file.readText()).length() }.getOrNull() ?: return null
        return count to file.lastModified()
    }

    private fun toJson(item: StreamInfoItem) = JSONObject()
        .put("s", item.serviceId)
        .put("u", item.url)
        .put("n", item.name)
        .put("un", item.uploaderName)
        .put("uu", item.uploaderUrl)
        .put("t", ImageStrategy.choosePreferredImage(item.thumbnails))
        .put("a", ImageStrategy.choosePreferredImage(item.uploaderAvatars))
        .put("v", item.viewCount)

    private fun fromJson(json: JSONObject): StreamInfoItem {
        val item = StreamInfoItem(
            json.getInt("s"),
            json.getString("u"),
            json.optString("n"),
            StreamType.LIVE_STREAM
        )
        item.uploaderName = json.optString("un")
        item.uploaderUrl = json.optString("uu").ifEmpty { null }
        item.thumbnails = image(json.optString("t"))
        item.uploaderAvatars = image(json.optString("a"))
        item.viewCount = json.optLong("v", -1)
        return item
    }

    private fun image(url: String): List<Image> = if (url.isEmpty()) {
        emptyList()
    } else {
        listOf(Image(url, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN))
    }
}
