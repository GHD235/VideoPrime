package org.schabi.newpipe.videoprime

import android.content.Context
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * VideoPrime: who was live on Twitch the last time anyone asked (the home screen, or the check
 * in the background every few minutes), so that "En directo ahora" shows as soon as the app opens.
 */
object HomeLiveCache {
    private const val FILE = "videoprime_home_live.bin"

    /** Older than this, the list is not trusted (the home screen asks Twitch again anyway). */
    private const val MAX_AGE_MILLIS = 15 * 60_000L

    /** The saved list and when it was saved, if it is recent and newer than [newerThan]. */
    @JvmStatic
    fun read(context: Context, newerThan: Long): Pair<List<StreamInfoItem>, Long>? {
        val file = File(context.cacheDir, FILE)
        val savedAt = file.lastModified()
        if (savedAt <= newerThan || System.currentTimeMillis() - savedAt > MAX_AGE_MILLIS) return null
        return runCatching {
            ObjectInputStream(file.inputStream().buffered()).use { input ->
                (input.readObject() as List<*>).filterIsInstance<StreamInfoItem>() to savedAt
            }
        }.getOrNull()
    }

    @JvmStatic
    @Synchronized
    fun write(context: Context, live: List<StreamInfoItem>) {
        runCatching {
            val file = File(context.cacheDir, FILE)
            val temp = File(context.cacheDir, "$FILE.tmp")
            ObjectOutputStream(temp.outputStream().buffered()).use { it.writeObject(ArrayList(live)) }
            temp.renameTo(file)
        }
    }
}
