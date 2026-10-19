package org.schabi.newpipe.videoprime

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: saves the songs that [OfflineMusic] is waiting for, one by one, while the
 * connection allowed by the settings lasts. The songs of the playlists and queues marked
 * "available offline" go first, then the songs listened to.
 */
class OfflineMusicWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val started = System.currentTimeMillis()
        val pinned = runCatching { resolveCollections(context) }.getOrNull()
        if (pinned != null) {
            val keys = pinned.map { it.serviceId to it.url }.toSet()
            OfflineMusic.update(context) { index ->
                index.copy(items = index.items.map { it.copy(pinned = (it.serviceId to it.url) in keys) })
            }
        }

        val limit = OfflineMusic.limitMb(context) * OfflineMusic.MB
        var full = false
        var networkErrors = 0
        while (!isStopped) {
            val index = OfflineMusic.load(context)
            val saved = index.items.map { it.serviceId to it.url }.toSet()
            val missing = pinned.orEmpty().filter { (it.serviceId to it.url) !in saved }
            if (index.pinnedPending != missing.size) {
                OfflineMusic.update(context) { it.copy(pinnedPending = missing.size) }
            }
            if (System.currentTimeMillis() - started > TIME_BUDGET_MILLIS) {
                // WorkManager stops workers after 10 minutes: carry on in a new one
                OfflineMusic.schedule(context)
                break
            }
            val next = missing.firstOrNull { it.url !in failed }?.let { it to true }
                ?: index.wanted.firstOrNull { it.url !in failed }?.let { it to false }
                ?: break
            val (song, isPinned) = next
            if (full && isPinned) {
                failed += song.url
                continue
            }
            val item = try {
                save(context, song, isPinned, limit).also { networkErrors = 0 }
            } catch (e: HttpStatusException) {
                // YouTube answered (e.g. 403): the network works, this song cannot be saved
                Log.w(TAG, "Could not save ${song.url}", e)
                null
            } catch (e: IOException) {
                Log.w(TAG, "Could not save ${song.url}", e)
                networkErrors++
                null
            } catch (e: Exception) {
                Log.w(TAG, "Could not save ${song.url}", e)
                null
            }
            if (networkErrors >= MAX_NETWORK_ERRORS) {
                // the connection is gone: try again later instead of giving up on every song
                return Result.retry()
            }
            if (item == null) {
                failed += song.url
                if (isPinned && OfflineMusic.load(context).usedBytes >= limit) full = true
            }
            OfflineMusic.update(context) { current ->
                current.copy(
                    items = if (item != null) current.items + item else current.items,
                    wanted = current.wanted.filterNot { it.url == song.url },
                    pinnedPending = if (item != null && isPinned) {
                        (current.pinnedPending - 1).coerceAtLeast(0)
                    } else {
                        current.pinnedPending
                    }
                )
            }
        }
        return Result.success()
    }

    private val failed = HashSet<String>()

    /** @return all the songs of the playlists and queues marked as available offline */
    private fun resolveCollections(context: Context): List<OfflineWanted> {
        val result = LinkedHashMap<String, OfflineWanted>()
        for (collection in OfflineMusic.load(context).collections) {
            val songs = runCatching {
                when (collection.type) {
                    OfflineCollection.TYPE_LOCAL -> localPlaylist(context, collection.ref.toLong())
                    OfflineCollection.TYPE_REMOTE -> remotePlaylist(collection.ref)
                    OfflineCollection.TYPE_QUEUE -> queue(context, collection.ref.toLong())
                    else -> emptyList()
                }
            }.getOrElse {
                // keep what was saved for a list that cannot be loaded now
                OfflineMusic.load(context).items.filter { it.pinned }.map {
                    OfflineWanted(it.serviceId, it.url, it.title, it.uploader, it.thumbnailUrl, it.duration)
                }
            }
            songs.filter { it.serviceId == org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId }
                .forEach { result.putIfAbsent(it.url, it) }
        }
        return result.values.toList()
    }

    private fun localPlaylist(context: Context, id: Long): List<OfflineWanted> = LocalPlaylistManager(NewPipeDatabase.getInstance(context))
        .getPlaylistStreams(id)
        .blockingFirst()
        .map { entry ->
            val stream = entry.streamEntity
            OfflineWanted(
                stream.serviceId,
                stream.url,
                stream.title,
                stream.uploader,
                stream.thumbnailUrl,
                stream.duration
            )
        }

    private fun remotePlaylist(url: String): List<OfflineWanted> {
        val service = NewPipe.getServiceByUrl(url)
        val info = PlaylistInfo.getInfo(service, url)
        val items = ArrayList<StreamInfoItem>(info.relatedItems)
        var page: Page? = info.nextPage
        while (page != null && items.size < MAX_PLAYLIST_SONGS && !isStopped) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            items += more.items
            page = more.nextPage
        }
        return items.map {
            OfflineWanted(
                it.serviceId,
                it.url,
                it.name,
                it.uploaderName ?: "",
                ImageStrategy.choosePreferredImage(it.thumbnails),
                it.duration
            )
        }
    }

    private fun queue(context: Context, id: Long): List<OfflineWanted> = QueueStore(context).load().queues.firstOrNull { it.id == id }?.items.orEmpty().map {
        OfflineWanted(it.serviceId, it.url, it.title, it.uploader, it.thumbnailUrl, it.duration)
    }

    /** Downloads the audio of a song into the offline folder, making room for it first. */
    private fun save(
        context: Context,
        song: OfflineWanted,
        pinned: Boolean,
        limit: Long
    ): OfflineItem? {
        val info = StreamInfo.getInfo(NewPipe.getService(song.serviceId), song.url)
        if (!OfflineMusic.isSavable(info)) return null
        val audio = pickAudio(info.audioStreams) ?: return null
        val expected = audio.itagItem?.contentLength?.takeIf { it > 0 }
            ?: (audio.averageBitrate.coerceAtLeast(128) * 1000L / 8 * info.duration)

        // it does not fit even removing every song that may be removed: keep them
        val current = OfflineMusic.load(context)
        if (current.items.filter { it.pinned }.sumOf { it.size } + expected > limit) return null
        // make room, removing the songs listened to longest ago (never the pinned ones)
        val index = OfflineMusic.update(context) { OfflineMusic.evict(context, it, limit - expected) }
        if (index.usedBytes + expected > limit) return null

        val suffix = audio.format?.suffix ?: "m4a"
        val name = "${song.url.hashCode().toUInt()}_${System.currentTimeMillis()}.$suffix"
        val file = File(OfflineMusic.dir(context), name)
        val partial = File(file.path + ".part")
        try {
            download(audio.content, partial)
            if (!partial.renameTo(file)) return null
        } finally {
            partial.delete()
        }
        return OfflineItem(
            serviceId = song.serviceId,
            url = song.url,
            title = info.name ?: song.title,
            uploader = info.uploaderName ?: song.uploader,
            thumbnailUrl = ImageStrategy.choosePreferredImage(info.thumbnails) ?: song.thumbnailUrl,
            duration = info.duration.takeIf { it > 0 } ?: song.duration,
            file = name,
            size = file.length(),
            lastPlayed = System.currentTimeMillis(),
            pinned = pinned
        )
    }

    /** M4A plays everywhere; the original language and the best quality come first. */
    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        val usable = streams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format != null
        }
        val original = usable.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { usable }
        return original.sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                .thenByDescending { it.averageBitrate }
        ).firstOrNull()
    }

    /** Downloads in parts: YouTube slows down the requests for a whole file. */
    private fun download(url: String, target: File) {
        val client = DownloaderImpl.getInstance().client
        target.outputStream().use { out ->
            var start = 0L
            var total = -1L
            while (total < 0 || start < total) {
                if (isStopped) throw IOException("Stopped")
                val end = start + CHUNK - 1
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DownloaderImpl.USER_AGENT)
                    .header("Range", "bytes=$start-$end")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) {
                        throw HttpStatusException(response.code)
                    }
                    val body = response.body ?: throw IOException("No body")
                    total = if (response.code == 200) {
                        body.contentLength().takeIf { it > 0 } ?: Long.MAX_VALUE
                    } else {
                        response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            ?: throw IOException("No Content-Range")
                    }
                    val read = body.byteStream().use { it.copyTo(out) }
                    if (read == 0L) throw IOException("Empty part")
                    start += read
                    if (response.code == 200) total = start
                }
            }
        }
    }

    companion object {
        private const val TAG = "OfflineMusicWorker"
        private const val CHUNK = 1024L * 1024L
        private const val MAX_PLAYLIST_SONGS = 300
        private const val TIME_BUDGET_MILLIS = 8 * 60 * 1000L
        private const val MAX_NETWORK_ERRORS = 3
    }
}

/** The server answered with an error status: the connection works, the song cannot be saved. */
private class HttpStatusException(code: Int) : IOException("HTTP $code")
