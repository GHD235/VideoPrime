package org.schabi.newpipe.videoprime

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.image.ImageStrategy
import us.shandian.giga.get.sqlite.FinishedMissionStore

/** A finished download of VideoPrime. */
data class LocalFile(
    /** `file://` or `content://` URI, also used as the player URL. */
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    /** In seconds, or 0 if unknown. */
    val duration: Long,
    val isAudio: Boolean,
    val size: Long,
    val timestamp: Long,
    /** Name of the folder that holds the file, e.g. "NewPipe". */
    val folder: String,
    /** Full path of that folder, e.g. "Download/NewPipe". */
    val folderPath: String
) {
    fun toInfoItem(): StreamInfoItem = StreamInfoItem(
        ServiceList.Local.serviceId,
        url,
        title,
        if (isAudio) StreamType.AUDIO_STREAM else StreamType.VIDEO_STREAM
    ).also { item ->
        item.uploaderName = uploader
        item.duration = duration
        item.thumbnails = thumbnailUrl?.let { ImageStrategy.dbUrlToImageList(it) } ?: emptyList()
    }
}

/**
 * VideoPrime: the app's downloads, for the downloads library and for playing them in the app's
 * own player. Titles, channels and thumbnails come from the watch history of the downloaded
 * video when it is there, since the files themselves usually lack them.
 */
object LocalMedia {
    /** @return the finished downloads whose file still exists, newest first */
    @JvmStatic
    fun listDownloads(context: Context): List<LocalFile> {
        val app = context.applicationContext
        val streamDao = NewPipeDatabase.getInstance(app).streamDAO()
        return FinishedMissionStore(app).loadFinishedMissions().mapNotNull { mission ->
            val storage = mission.storage ?: return@mapNotNull null
            if (storage.isInvalid || !runCatching { storage.existsAsFile() }.getOrDefault(false)) {
                return@mapNotNull null
            }
            val uri = storage.uri
            val known: StreamEntity? = mission.source?.let { source ->
                NewPipe.getServices().firstNotNullOfOrNull { service ->
                    streamDao.getStream(service.serviceId.toLong(), source)
                        .blockingFirst(emptyList()).firstOrNull()
                }
            }
            val (folder, folderPath) = folderOf(uri)
            LocalFile(
                url = uri.toString(),
                title = known?.title ?: storage.name.substringBeforeLast('.'),
                uploader = known?.uploader ?: "",
                thumbnailUrl = known?.thumbnailUrl?.let {
                    ImageStrategy.choosePreferredImage(ImageStrategy.dbUrlToImageList(it))
                },
                duration = known?.duration ?: 0,
                isAudio = mission.kind == 'a',
                size = mission.length,
                timestamp = mission.timestamp,
                folder = folder,
                folderPath = folderPath
            )
        }
    }

    /**
     * Stream info of a local file, for the player. Downloads keep their title, channel and
     * thumbnail; other files get what their metadata says.
     */
    @JvmStatic
    fun getStreamInfo(context: Context, url: String): StreamInfo {
        val download = listDownloads(context).firstOrNull { it.url == url }
        val uri = Uri.parse(url)
        val metadata = readMetadata(context, uri)
        val isAudio = download?.isAudio ?: !metadata.hasVideo
        val title = download?.title ?: metadata.title ?: uri.lastPathSegment ?: url

        val info = StreamInfo(
            ServiceList.Local.serviceId,
            url,
            url,
            if (isAudio) StreamType.AUDIO_STREAM else StreamType.VIDEO_STREAM,
            url,
            title,
            0
        )
        info.uploaderName = download?.uploader?.takeIf { it.isNotEmpty() } ?: metadata.artist ?: ""
        info.duration = download?.duration?.takeIf { it > 0 } ?: metadata.durationSeconds
        info.thumbnails = download?.thumbnailUrl?.let { ImageStrategy.dbUrlToImageList(it) }
            ?: emptyList<Image>()

        val format = MediaFormat.getFromSuffix(
            (download?.url ?: url).substringAfterLast('.', "").lowercase()
        )
        if (isAudio) {
            info.audioStreams = listOf(
                AudioStream.Builder()
                    .setId(Stream.ID_UNKNOWN)
                    .setContent(url, true)
                    .setMediaFormat(format ?: MediaFormat.M4A)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .setAverageBitrate(AudioStream.UNKNOWN_BITRATE)
                    .build()
            )
        } else {
            info.videoStreams = listOf(
                VideoStream.Builder()
                    .setId(Stream.ID_UNKNOWN)
                    .setContent(url, true)
                    .setMediaFormat(format ?: MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .setIsVideoOnly(false)
                    .setResolution(metadata.resolution ?: "")
                    .build()
            )
        }
        return info
    }

    private data class Metadata(
        val title: String?,
        val artist: String?,
        val durationSeconds: Long,
        val hasVideo: Boolean,
        val resolution: String?
    )

    private fun readMetadata(context: Context, uri: Uri): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                durationSeconds = (
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0
                    ) / 1000,
                hasVideo = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                ) == "yes",
                resolution = height?.let { "${it}p" }
            )
        } catch (e: Exception) {
            Metadata(null, null, 0, hasVideo = true, resolution = null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** @return the folder's name and its path, for a file or a Storage Access Framework URI */
    private fun folderOf(uri: Uri): Pair<String, String> {
        val path = when (uri.scheme) {
            "file" -> uri.path?.let { File(it).parent }

            else -> runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                ?.substringAfter(':')
                ?.substringBeforeLast('/', "")
        } ?: ""
        val name = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
        return (name.ifEmpty { "/" }) to path
    }
}
