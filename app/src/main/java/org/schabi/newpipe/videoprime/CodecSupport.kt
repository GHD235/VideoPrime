package org.schabi.newpipe.videoprime

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * PulsePlay: YouTube offers each resolution in several codecs (H.264, VP9, AV1). The stream was
 * picked by container alone, and AV1 comes in the same container as H.264: a phone or TV whose
 * chip cannot decode AV1 ended up decoding it in software (jerky picture, hot device, battery).
 * Of the streams of one resolution, those the device decodes in hardware are kept when there is
 * one; with none (or when the device cannot be asked) the list is left as it was.
 */
object CodecSupport {
    private val decoders: List<MediaCodecInfo>? by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && isHardware(it) }
        }.getOrNull()
    }
    private val answers = ConcurrentHashMap<String, Boolean>()

    @JvmStatic
    fun preferHardware(streams: List<VideoStream>): List<VideoStream> {
        if (streams.size < 2 || decoders == null) return streams
        val hardware = streams.filter { runCatching { decodedInHardware(it) }.getOrDefault(true) }
        if (hardware.size == streams.size) return streams
        val resolutionsCovered = hardware.mapTo(HashSet()) { it.getResolution() }
        return streams.filter { it in hardware || it.getResolution() !in resolutionsCovered }
    }

    /** True too when the codec is not known: only what is surely software is set aside. */
    private fun decodedInHardware(stream: VideoStream): Boolean {
        val mime = mimeOf(stream.codec) ?: return true
        val width = stream.width
        val height = stream.height
        return answers.getOrPut("$mime/$width/$height") {
            decoders.orEmpty().any { info ->
                val type = info.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
                    ?: return@any false
                if (width <= 0 || height <= 0) return@any true
                val video = runCatching { info.getCapabilitiesForType(type).videoCapabilities }
                    .getOrNull() ?: return@any true
                // a portrait video is listed with its sides swapped on some decoders
                video.isSizeSupported(width, height) || video.isSizeSupported(height, width)
            }
        }
    }

    private fun mimeOf(codec: String?): String? = when {
        codec.isNullOrEmpty() -> null
        codec.startsWith("av01") -> "video/av01"
        codec.startsWith("vp9") || codec.startsWith("vp09") -> "video/x-vnd.on2.vp9"
        codec.startsWith("avc") -> "video/avc"
        codec.startsWith("hev") || codec.startsWith("hvc") -> "video/hevc"
        else -> null
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        val name = info.name.lowercase()
        return !(
            name.startsWith("omx.google.") || name.startsWith("c2.android.") ||
                name.startsWith("c2.google.") || name.contains(".sw.") || name.contains("ffmpeg")
            )
    }
}
