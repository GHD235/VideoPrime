package org.schabi.newpipe.videoprime

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * PulsePlay: the pictures of the car (Android Auto). The car wants local pictures
 * (content://, as Android's guide for media apps says) and draws them in square tiles: a web
 * address of a 16:9 thumbnail came out squashed or blurry on some phones. This provider hands
 * the car the picture already downloaded (through Coil, so in the best size [BestThumbnail]
 * finds), cut to its middle square (for YouTube Music songs, the cover itself) at 512 px.
 *
 * It is exported (the car is another app) but only serves pictures from YouTube and Twitch.
 */
class CarArt : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val context = context ?: throw FileNotFoundException()
        val url = uri.getQueryParameter(PARAM_URL)?.takeIf(::isAllowed) ?: throw FileNotFoundException()
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, "${sha1(url)}.jpg")
        if (!file.exists()) {
            val bitmap = load(context, url) ?: throw FileNotFoundException(url)
            val square = squareOf(bitmap) ?: throw FileNotFoundException(url)
            val part = File(dir, "${file.name}.${Thread.currentThread().id}.part")
            runCatching {
                part.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (!part.renameTo(file)) part.delete()
            }.onFailure { part.delete() }
            if (!file.exists()) throw FileNotFoundException(url)
            trim(dir)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val PARAM_URL = "u"
        private const val DIR = "car_art"
        private const val SIZE = 512
        private const val JPEG_QUALITY = 88
        private const val LOAD_TIMEOUT_MILLIS = 4_000L
        private const val MAX_FILES = 500

        /** Loads at once: the binder threads the car calls on are shared by the whole app. */
        private val loads = Semaphore(4)
        private const val SLOT_WAIT_MILLIS = 1_500L

        /** Pictures that failed lately (address → when), not asked for again for a while. */
        private val failed = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 300
        }
        private const val FAILED_FOR_MILLIS = 10 * 60 * 1000L

        /**
         * The picture through Coil (only a real success: never a placeholder saved as the art),
         * with a few at a time and a short wait, so slow pictures cannot hold the car's calls.
         */
        private fun load(context: Context, url: String): Bitmap? {
            synchronized(failed) {
                val at = failed[url]
                if (at != null && System.currentTimeMillis() - at < FAILED_FOR_MILLIS) return null
            }
            // busy or slow is not "failed": the car asks again and then it is served
            if (!loads.tryAcquire(SLOT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) return null
            var reallyFailed = false
            val bitmap = try {
                runCatching {
                    runBlocking {
                        withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) {
                            val request = ImageRequest.Builder(context).data(url).build()
                            val result = context.imageLoader.execute(request)
                            if (result !is SuccessResult) reallyFailed = true
                            (result as? SuccessResult)?.image?.toBitmap()
                        }
                    }
                }.getOrNull()
            } finally {
                loads.release()
            }
            if (bitmap == null && reallyFailed) synchronized(failed) { failed[url] = System.currentTimeMillis() }
            return bitmap
        }

        private val ALLOWED_HOST_ENDS = listOf(
            ".ytimg.com",
            ".ggpht.com",
            ".googleusercontent.com",
            ".jtvnw.net"
        )

        /** The address the car gets for a picture: ours for YouTube/Twitch, any other as it is. */
        @JvmStatic
        fun uriFor(context: Context, url: String?): Uri? {
            if (url.isNullOrBlank()) return null
            if (!isAllowed(url)) return runCatching { url.toUri() }.getOrNull()
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".carart")
                .appendPath("art")
                .appendQueryParameter(PARAM_URL, url)
                .build()
        }

        /** Checked as OkHttp (which downloads it) reads the address, with nothing odd in it. */
        private fun isAllowed(url: String): Boolean {
            if (url.contains('\\') || url.contains('@')) return false
            val parsed = url.toHttpUrlOrNull() ?: return false
            val host = parsed.host.lowercase()
            return parsed.isHttps && parsed.port == 443 && ALLOWED_HOST_ENDS.any { host.endsWith(it) }
        }

        /** The middle square, at [SIZE] px at most. */
        private fun squareOf(bitmap: Bitmap): Bitmap? = runCatching {
            val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val side = minOf(readable.width, readable.height)
            if (side <= 0) return@runCatching null
            val square = Bitmap.createBitmap(readable, (readable.width - side) / 2, (readable.height - side) / 2, side, side)
            if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        }.getOrNull()

        /** Keeps the [MAX_FILES] newest. */
        private fun trim(dir: File) {
            val files = dir.listFiles { file -> file.name.endsWith(".jpg") } ?: return
            if (files.size <= MAX_FILES) return
            files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
        }

        private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
