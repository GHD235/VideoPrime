package org.schabi.newpipe.player.mediabrowser

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.image.ImageStrategy
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

    private fun mediaId(): Uri.Builder = Uri.Builder().authority(ID_AUTHORITY)

    /** @return the children of one of our sections, or null if [path] is not ours */
    fun children(context: Context, parent: Uri): Single<List<MediaItem>>? {
        val path = parent.pathSegments
        return when (path.firstOrNull()) {
            ID_FEED -> Single.fromCallable { feed(context) }
                .map { items -> items.mapIndexed { i, item -> playable(feedId(i, item.url), item) } }

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
                    offlineSongs(context).mapIndexed { i, item -> playable(offlineId(i, item.url), item) }
                } else {
                    val folderPath = parent.getQueryParameter(PARAM_PATH) ?: ""
                    files.filter { it.folderPath == folderPath }.mapIndexed { i, file ->
                        playable(fileId(folderPath, i, file.url), file.toInfoItem())
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

    private fun feed(context: Context): List<StreamInfoItem> = FeedDatabaseManager(context)
        .getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
        .blockingGet(emptyList())
        .take(MAX_FEED_ITEMS)
        .map { it.stream.toStreamInfoItem() }

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

    private fun playable(id: String, item: StreamInfoItem): MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(item.name)
            .setSubtitle(item.uploaderName)
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
