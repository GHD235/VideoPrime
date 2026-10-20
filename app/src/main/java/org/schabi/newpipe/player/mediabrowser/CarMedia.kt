package org.schabi.newpipe.player.mediabrowser

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.utils.MediaConstants
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.videoprime.CarText
import org.schabi.newpipe.videoprime.LocalFile
import org.schabi.newpipe.videoprime.LocalMedia
import org.schabi.newpipe.videoprime.OfflineMusic

/**
 * VideoPrime: the car (Android Auto) sections added to NewPipe's media browser: What's new and
 * downloads by folder. In the car everything plays in the background player, so videos play as
 * audio.
 */
internal object CarMedia {
    const val ID_FEED = "feed"
    const val ID_DOWNLOADS = "downloads"
    private const val ID_FOLDER = "folder"
    private const val ID_FILE = "file"
    private const val PARAM_PATH = "path"
    private const val PARAM_INDEX = "index"
    private const val PARAM_ITEM_URL = "url"
    private const val MAX_FEED_ITEMS = 100
    private const val ID_OFFLINE = "offline"
    private const val MAX_SEARCH_ITEMS = 30
    private const val ID_QUEUE = "queue"
    private const val MAX_UP_NEXT = 6

    /** Watched this much, a video counts as seen. */
    private const val WATCHED_FRACTION = 0.9

    private fun mediaId(): Uri.Builder = Uri.Builder().authority(ID_AUTHORITY)

    /** @return the children of one of our sections, or null if [path] is not ours */
    fun children(context: Context, parent: Uri): Single<List<MediaItem>>? {
        val path = parent.pathSegments
        return when (path.firstOrNull()) {
            ID_FEED -> Single.fromCallable { feedEntries(context) }
                .map { entries ->
                    val offline = offlineUrls(context)
                    entries.mapIndexed { i, entry ->
                        val item = entry.stream.toStreamInfoItem()
                        playable(
                            feedId(i, item.url),
                            item,
                            group = dayGroup(context, entry.stream.uploadDate),
                            progress = completion(entry.stateProgressMillis, entry.stream.duration),
                            downloaded = item.url in offline
                        )
                    }
                }

            ID_DOWNLOADS -> Single.fromCallable {
                val files = LocalMedia.listDownloads(context)
                if (path.size == 1) {
                    // the songs kept offline first: they play without coverage
                    val offline = offlineSongs(context)
                    val offlineFolder = if (offline.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(
                            browsable(
                                mediaId().appendPath(ID_DOWNLOADS).appendPath(ID_OFFLINE).build()
                                    .toString(),
                                context.getString(R.string.offline_title),
                                context.resources.getQuantityString(
                                    R.plurals.videoprime_songs,
                                    offline.size,
                                    offline.size
                                )
                            )
                        )
                    }
                    offlineFolder + files.groupBy { it.folderPath }.map { (folderPath, inFolder) ->
                        browsable(
                            mediaId().appendPath(ID_DOWNLOADS).appendPath(ID_FOLDER)
                                .appendQueryParameter(PARAM_PATH, folderPath).build().toString(),
                            inFolder.first().folder,
                            folderPath
                        )
                    }
                } else if (path.getOrNull(1) == ID_OFFLINE) {
                    offlineSongs(context).mapIndexed { i, item ->
                        playable(offlineId(i, item.url), item, downloaded = true)
                    }
                } else {
                    val folderPath = parent.getQueryParameter(PARAM_PATH) ?: ""
                    files.filter { it.folderPath == folderPath }.mapIndexed { i, file ->
                        playable(fileId(folderPath, i, file.url), file.toInfoItem(), downloaded = true)
                    }
                }
            }

            else -> null
        }?.subscribeOn(Schedulers.io())
    }

    /** @return the queue to play for one of our items, or null if [mediaId] is not ours */
    fun playQueue(context: Context, mediaId: Uri): Single<PlayQueue>? {
        val path = mediaId.pathSegments
        return when (path.firstOrNull()) {
            ID_FEED -> Single.fromCallable<PlayQueue> {
                val items = feed(context)
                if (items.isEmpty()) throw ContentNotAvailableException("No new videos")
                SinglePlayQueue(items, indexOf(items, mediaId, path.getOrNull(1)?.toIntOrNull()))
            }

            ID_DOWNLOADS -> Single.fromCallable<PlayQueue> {
                if (path.getOrNull(1) == ID_OFFLINE) {
                    val songs = offlineSongs(context)
                    if (songs.isEmpty()) throw ContentNotAvailableException("No offline songs")
                    return@fromCallable SinglePlayQueue(songs, indexOf(songs, mediaId, null))
                }
                val folderPath = mediaId.getQueryParameter(PARAM_PATH) ?: ""

                val files: List<LocalFile> = LocalMedia.listDownloads(context)
                    .filter { it.folderPath == folderPath }
                if (files.isEmpty()) throw ContentNotAvailableException("No downloads here")
                val items = files.map { it.toInfoItem() }
                SinglePlayQueue(items, indexOf(items, mediaId, null))
            }

            else -> null
        }?.subscribeOn(Schedulers.io())
    }

    /**
     * The top of the playlists tab: the song playing and the next ones in the queue, with how
     * long until each starts (empty when nothing plays). Reads the player, so it runs on the
     * main thread.
     */
    fun nowPlaying(context: Context): Single<List<MediaItem>> = Single.fromCallable {
        val player = PlayerService.getCurrentPlayer()
        val queue = player?.playQueue
        val current = queue?.item
        if (player == null || current == null) return@fromCallable emptyList<MediaItem>()

        val nowGroup = context.getString(R.string.videoprime_now_playing)
        val nextGroup = context.getString(R.string.videoprime_up_next)
        val offline = offlineUrls(context)
        val items = mutableListOf(
            queueItem(
                queue.index,
                current.url,
                current.title,
                CarText.artist(current.uploader),
                current.thumbnails,
                nowGroup,
                current.url in offline
            )
        )
        val exo = player.exoPlayer
        val position = if (exo != null) exo.currentPosition / 1000 else 0
        var startsIn = (current.duration - position).coerceAtLeast(0)
        for (i in queue.index + 1 until minOf(queue.size(), queue.index + 1 + MAX_UP_NEXT)) {
            val next = queue.getItem(i) ?: break
            val artist = CarText.artist(next.uploader)
            val subtitle = if (current.duration > 0) {
                artist + " · " + context.getString(R.string.videoprime_in_time, CarText.duration(startsIn))
            } else {
                artist
            }
            items += queueItem(i, next.url, next.title, subtitle, next.thumbnails, nextGroup, next.url in offline)
            startsIn += next.duration.coerceAtLeast(0)
        }
        items.toList()
    }.subscribeOn(AndroidSchedulers.mainThread())

    private fun queueItem(
        index: Int,
        url: String,
        title: String,
        subtitle: String,
        thumbnails: List<Image>,
        group: String,
        downloaded: Boolean
    ): MediaItem {
        val extras = Bundle()
        if (downloaded) {
            extras.putLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS, MediaDescriptionCompat.STATUS_DOWNLOADED)
        }
        extras.putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        extras.putInt(
            MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
            MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(
                mediaId().appendPath(ID_QUEUE).appendPath(index.toString())
                    .appendQueryParameter(PARAM_ITEM_URL, url).build().toString()
            )
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconUri(
                MediaBrowserImpl.imageUriOrNullIfDisabled(ImageStrategy.choosePreferredImage(thumbnails))
            )
            .setExtras(extras)
            .build()
        return MediaItem(description, MediaItem.FLAG_PLAYABLE)
    }

    /**
     * Plays a song of the queue shown in the playlists tab, without building a new queue.
     * @return false if [mediaId] is not one of those songs
     */
    fun playFromQueue(mediaId: Uri, playWhenReady: Boolean): Boolean {
        if (mediaId.pathSegments.firstOrNull() != ID_QUEUE) return false
        val player = PlayerService.getCurrentPlayer() ?: return true
        val queue = player.playQueue ?: return true
        val url = mediaId.getQueryParameter(PARAM_ITEM_URL)
        // the queue may have moved since the car drew it: look the song up by its URL
        val index = queue.streams.indexOfFirst { it.url == url }
            .takeIf { it >= 0 } ?: mediaId.pathSegments.getOrNull(1)?.toIntOrNull() ?: return true
        if (index != queue.index && index < queue.size()) {
            queue.setIndex(index)
        }
        if (playWhenReady) player.play()
        return true
    }

    private fun feedEntries(context: Context): List<StreamWithState> = FeedDatabaseManager(context)
        .getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
        .blockingGet(emptyList())
        .take(MAX_FEED_ITEMS)

    private fun feed(context: Context): List<StreamInfoItem> = feedEntries(context).map { it.stream.toStreamInfoItem() }

    /** What's new comes in sections: today, yesterday, this week, earlier. */
    private fun dayGroup(context: Context, date: OffsetDateTime?): String {
        val days = date?.let {
            ChronoUnit.DAYS.between(it.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
        } ?: Long.MAX_VALUE
        return context.getString(
            when {
                days <= 0 -> R.string.videoprime_today
                days == 1L -> R.string.videoprime_yesterday
                days < 7 -> R.string.videoprime_this_week
                else -> R.string.videoprime_earlier
            }
        )
    }

    /** How much of a video was watched, from 0 to 1 (null if never started). */
    private fun completion(progressMillis: Long?, durationSeconds: Long): Double? {
        if (progressMillis == null || progressMillis <= 0 || durationSeconds <= 0) return null
        return (progressMillis / 1000.0 / durationSeconds).coerceIn(0.0, 1.0)
    }

    private fun offlineUrls(context: Context): Set<String> = OfflineMusic.load(context).items.map { it.url }.toSet()

    /** The What's new section, to play when the car asks for music without saying which. */
    fun feedRoot(): Uri = mediaId().appendPath(ID_FEED).appendPath("0").build()

    /**
     * Songs for a voice search in the car: YouTube Music songs, or YouTube videos if there are
     * none (must run off the main thread).
     */
    fun search(query: String): List<StreamInfoItem> {
        val youtube = ServiceList.YouTube
        val songs = listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS)
            .asSequence()
            .map { filter ->
                runCatching {
                    SearchInfo.getInfo(youtube, youtube.searchQHFactory.fromQuery(query, listOf(filter), ""))
                        .relatedItems.filterIsInstance<StreamInfoItem>()
                }.getOrDefault(emptyList())
            }
            .firstOrNull { it.isNotEmpty() }
        return songs.orEmpty().take(MAX_SEARCH_ITEMS)
    }

    /** The songs kept for listening without connection, last played first. */
    private fun offlineSongs(context: Context): List<StreamInfoItem> = OfflineMusic.load(context).items
        .sortedByDescending { it.lastPlayed }
        .map { song ->
            StreamInfoItem(song.serviceId, song.url, song.title, StreamType.AUDIO_STREAM).also {
                it.uploaderName = song.uploader
                it.duration = song.duration
                it.thumbnails = song.thumbnailUrl?.let(ImageStrategy::dbUrlToImageList).orEmpty()
            }
        }

    /**
     * Where the chosen item is now: lists change (What's new every hour, the offline songs each
     * time one is played) while the car keeps showing the old one, so the item is looked up by
     * its URL, and only by its old position if it is gone.
     */
    private fun indexOf(items: List<StreamInfoItem>, mediaId: Uri, pathIndex: Int?): Int {
        val url = mediaId.getQueryParameter(PARAM_ITEM_URL)
        val found = items.indexOfFirst { it.url == url }
        if (found >= 0) return found
        val index = pathIndex ?: mediaId.getQueryParameter(PARAM_INDEX)?.toIntOrNull() ?: 0
        return index.coerceIn(0, items.size - 1)
    }

    private fun offlineId(index: Int, url: String) = mediaId()
        .appendPath(ID_DOWNLOADS).appendPath(ID_OFFLINE)
        .appendQueryParameter(PARAM_INDEX, index.toString())
        .appendQueryParameter(PARAM_ITEM_URL, url)
        .build().toString()

    private fun feedId(index: Int, url: String) = mediaId().appendPath(ID_FEED).appendPath(index.toString())
        .appendQueryParameter(PARAM_ITEM_URL, url)
        .build().toString()

    private fun fileId(folderPath: String, index: Int, url: String) = mediaId()
        .appendPath(ID_DOWNLOADS).appendPath(ID_FILE)
        .appendQueryParameter(PARAM_PATH, folderPath)
        .appendQueryParameter(PARAM_INDEX, index.toString())
        .appendQueryParameter(PARAM_ITEM_URL, url)
        .build().toString()

    /**
     * @param group the section the car shows it in
     * @param progress how much was watched (the car draws a bar, or a tick when finished)
     * @param downloaded plays without connection (the car shows a download mark)
     */
    private fun playable(
        id: String,
        item: StreamInfoItem,
        group: String? = null,
        progress: Double? = null,
        downloaded: Boolean = false
    ): MediaItem {
        val extras = Bundle()
        group?.let { extras.putString(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
        if (progress != null) {
            if (progress >= WATCHED_FRACTION) {
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                )
            } else {
                extras.putInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS,
                    MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                )
                extras.putDouble(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, progress)
            }
        }
        if (downloaded) {
            extras.putLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS, MediaDescriptionCompat.STATUS_DOWNLOADED)
        }
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(item.name)
            .setSubtitle(CarText.artist(item.uploaderName))
            .setExtras(extras)
            .setIconUri(
                MediaBrowserImpl.imageUriOrNullIfDisabled(
                    ImageStrategy.choosePreferredImage(item.thumbnails)
                )
            )
            .build()
        return MediaItem(description, MediaItem.FLAG_PLAYABLE)
    }

    private fun browsable(id: String, title: String, subtitle: String): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaItem(description, MediaItem.FLAG_BROWSABLE)
    }
}
