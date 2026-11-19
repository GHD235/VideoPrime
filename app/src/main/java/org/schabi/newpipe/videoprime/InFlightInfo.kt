package org.schabi.newpipe.videoprime

import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/**
 * PulsePlay: one reading of a video at a time. On the TV the video under the remote's focus is
 * read in advance; pressing OK while that reading was still going started a second one of the
 * same video in parallel (twice the requests and work on a weak processor, and the video took
 * longer to open). Now the second one waits for the first and gets its result.
 *
 * The callers already run on background threads (they block on the network anyway). If the
 * reading they waited for was cancelled (the advance reading stops when the home screen goes),
 * they do their own.
 */
object InFlightInfo {
    private val running = ConcurrentHashMap<String, FutureTask<Any>>()

    /** What reads it (from Java it may throw the extractor's checked exceptions). */
    fun interface Loader<T : Any> {
        @Throws(Exception::class)
        fun load(): T
    }

    @JvmStatic
    @Throws(Exception::class)
    fun <T : Any> load(key: String, loader: Loader<T>): T {
        val mine = FutureTask<Any> { loader.load() }
        val other = running.putIfAbsent(key, mine)
        if (other == null) {
            try {
                mine.run()
                return unwrap(mine)
            } finally {
                running.remove(key, mine)
            }
        }
        val result = try {
            other.get()
        } catch (e: InterruptedException) {
            // this waiter was told to stop: it does not read the video itself
            Thread.currentThread().interrupt()
            throw e
        } catch (e: CancellationException) {
            null
        } catch (e: ExecutionException) {
            val cause = e.cause
            // only when the reading waited for was cancelled is it done again here
            if (cause == null || !wasCancelled(cause)) throw (cause as? Exception) ?: e
            null
        }
        @Suppress("UNCHECKED_CAST")
        return (result as T?) ?: loader.load()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unwrap(task: FutureTask<Any>): T = try {
        task.get() as T
    } catch (e: ExecutionException) {
        throw (e.cause as? Exception) ?: e
    }

    private fun wasCancelled(error: Throwable): Boolean {
        var e: Throwable? = error
        repeat(8) {
            when (e) {
                null -> return false
                is InterruptedException, is InterruptedIOException, is CancellationException ->
                    return true
            }
            e = e?.cause
        }
        return false
    }
}
