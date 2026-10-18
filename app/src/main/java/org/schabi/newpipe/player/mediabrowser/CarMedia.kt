package org.schabi.newpipe.player.mediabrowser

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.twitch.TwitchLiveChannels
import org.schabi.newpipe.extractor.services.twitch.linkHandler.TwitchChannelLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.videoprime.LocalFile
import org.schabi.newpipe.videoprime.LocalMedia
import org.schabi.newpipe.videoprime.QueueStore

/**
 * VideoPrime: the car (Android Auto) sections added to NewPipe's media browser: What's new,
 * live subscribed channels, downloads by folder and saved queues. In the car everything plays
 * in the background player, so videos play as audio.
 */
internal object CarMedia {
    const val ID_FEED = "feed"
    const val ID_LIVE = "live"
    const val ID_DOWNLOADS = "downloads"
    const val ID_QUEUES = "queues"
    private const val ID_FOLDER = "folder"
    private const val ID_FILE = "file"
    private const val PARAM_PATH = "path"
    private const val PARAM_INDEX = "index"
    private const val MAX_FEED_ITEMS = 100

    private fun mediaId(): Uri.Builder = Uri.Builder().authority(ID_AUTHORITY)

    /** @return the children of one of our sections, or null if [path] is not ours */
    fun children(context: Context, parent: Uri): Single<List<MediaItem>>? {
        val path = parent.pathSegments
        return when (path.firstOrNull()) {
            ID_FEED -> Single.fromCallable { feed(context) }
                .map { items -> items.mapIndexed { i, item -> playable(feedId(i), item) } }

            ID_LIVE -> Single.fromCallable { liveChannels(context) }
                .map { items -> items.map { playable(infoItemId(it), it) } }

            ID_DOWNLOADS -> Single.fromCallable {
                val files = LocalMedia.listDownloads(context)
                if (path.size == 1) {
                    files.groupBy { it.folderPath }.map { (folderPath, inFolder) ->
                        browsable(
                            mediaId().appendPath(ID_DOWNLOADS).appendPath(ID_FOLDER)
                                .appendQueryParameter(PARAM_PATH, folderPath).build().toString(),
                            inFolder.first().folder,
                            folderPath
                        )
                    }
                } else {
                    val folderPath = parent.getQueryParameter(PARAM_PATH) ?: ""
                    files.filter { it.folderPath == folderPath }.mapIndexed { i, file ->
                        playable(fileId(folderPath, i), file.toInfoItem())
                    }
                }
            }

            ID_QUEUES -> Single.fromCallable {
                QueueStore(context).load().queues.map { queue ->
                    val description = MediaDescriptionCompat.Builder()
                        .setMediaId(mediaId().appendPath(ID_QUEUES).appendPath(queue.id.toString()).build().toString())
                        .setTitle(queue.name)
                        .setSubtitle(queue.current?.title)
                        .setIconUri(MediaBrowserImpl.imageUriOrNullIfDisabled(queue.current?.thumbnailUrl))
                        .build()
                    MediaItem(description, MediaItem.FLAG_PLAYABLE)
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
                SinglePlayQueue(feed(context), path.getOrNull(1)?.toIntOrNull() ?: 0)
            }

            ID_DOWNLOADS -> Single.fromCallable<PlayQueue> {
                val folderPath = mediaId.getQueryParameter(PARAM_PATH) ?: ""
                val index = mediaId.getQueryParameter(PARAM_INDEX)?.toIntOrNull() ?: 0
                val files: List<LocalFile> = LocalMedia.listDownloads(context)
                    .filter { it.folderPath == folderPath }
                SinglePlayQueue(files.map { it.toInfoItem() }, index.coerceIn(0, files.size - 1))
            }

            ID_QUEUES -> Single.fromCallable<PlayQueue> {
                val id = path.getOrNull(1)?.toLongOrNull()
                val content = QueueStore(context).load()
                val queue = content.queues.first { it.id == id }
                QueueStore(context).save(content.copy(activeId = queue.id))
                queue.toPlayQueue()
            }

            else -> null
        }?.subscribeOn(Schedulers.io())
    }

    private fun feed(context: Context): List<StreamInfoItem> = FeedDatabaseManager(context)
        .getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
        .blockingGet(emptyList())
        .take(MAX_FEED_ITEMS)
        .map { it.stream.toStreamInfoItem() }

    private fun liveChannels(context: Context): List<StreamInfoItem> {
        val logins = NewPipeDatabase.getInstance(context).subscriptionDAO().getAll()
            .blockingFirst(emptyList())
            .filter { it.serviceId == ServiceList.Twitch.serviceId }
            .mapNotNull {
                runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(it.url) }
                    .getOrNull()
            }
        return if (logins.isEmpty()) emptyList() else TwitchLiveChannels.fetch(logins)
    }

    private fun feedId(index: Int) = mediaId().appendPath(ID_FEED).appendPath(index.toString()).build().toString()

    private fun fileId(folderPath: String, index: Int) = mediaId()
        .appendPath(ID_DOWNLOADS).appendPath(ID_FILE)
        .appendQueryParameter(PARAM_PATH, folderPath)
        .appendQueryParameter(PARAM_INDEX, index.toString())
        .build().toString()

    /** Same format as NewPipe's info item ids, so that its preparer plays them. */
    private fun infoItemId(item: InfoItem) = mediaId()
        .appendPath(ID_INFO_ITEM)
        .appendPath(infoItemTypeToString(item.infoType))
        .appendPath(item.serviceId.toString())
        .appendQueryParameter(ID_URL, item.url)
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
