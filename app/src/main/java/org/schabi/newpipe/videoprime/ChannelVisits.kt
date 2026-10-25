package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.StreamTypeUtil

/**
 * VideoPrime: when each channel was last visited, to show how many videos it published since
 * (the number on its avatar in Suscripciones) and to mark them NUEVO inside the channel, once.
 */
object ChannelVisits {
    private const val TAG = "ChannelVisits"
    private const val READ_WAIT_MILLIS = 300L

    /** Only videos of the last days count as new (on the avatar and inside the channel). */
    const val RECENT_DAYS = 30L
    private const val PREFS = "videoprime_channel_visits"
    private const val KEY_START = "start"

    /** The first time, what came out in the last day counts as new. */
    private const val START_BACK_MILLIS = 24 * 60 * 60 * 1000L

    /**
     * The channel opened last and its videos counted as new on its avatar (from the feed, read in
     * the background: until then nothing is marked).
     */
    private class Highlight(val url: String) {
        @Volatile
        var newUrls: Set<String> = emptySet()
        val read = CountDownLatch(1)
        var waited = false
    }

    @Volatile
    private var highlight: Highlight? = null

    private fun key(serviceId: Int, url: String) = "$serviceId|$url"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Before the first visit to a channel, the day before the app started counting. */
    private fun start(context: Context): Long {
        val prefs = prefs(context)
        prefs.getLong(KEY_START, 0L).takeIf { it > 0 }?.let { return it }
        val start = System.currentTimeMillis() - START_BACK_MILLIS
        prefs.edit { putLong(KEY_START, start) }
        return start
    }

    @JvmStatic
    fun lastVisit(context: Context, serviceId: Int, url: String): Long = prefs(context).getLong(key(serviceId, url), 0L).takeIf { it > 0 } ?: start(context)

    /** The channel is opened: what it published until now is not new any more. */
    @JvmStatic
    fun open(context: Context, serviceId: Int, url: String, name: String) {
        val since = lastVisit(context, serviceId, url)
        val opened = Highlight(url)
        highlight = opened
        prefs(context).edit { putLong(key(serviceId, url), System.currentTimeMillis()) }
        // the same query as the number on the avatar: watched or half-watched videos are not new,
        // and the channel page's approximate dates ("hace 1 día") are not used
        Thread {
            try {
                val zone = ZoneOffset.UTC
                val now = OffsetDateTime.now(zone)
                val from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(since), zone)
                    .coerceAtLeast(now.minusDays(RECENT_DAYS))
                opened.newUrls = NewPipeDatabase.getInstance(context).feedDAO()
                    .getUnwatchedUrlsSince(
                        serviceId,
                        url,
                        from,
                        now
                    )
                    .toSet()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the new videos of $name", e)
            } finally {
                opened.read.countDown()
            }
        }.start()
    }

    /** The channel page was closed: nothing is marked NUEVO elsewhere. */
    @JvmStatic
    fun close(url: String) {
        if (highlight?.url == url) highlight = null
    }

    /** Whether [item] is one of the videos counted as new for the channel just opened. */
    @JvmStatic
    fun isNew(item: StreamInfoItem): Boolean {
        val current = highlight ?: return false
        if (StreamTypeUtil.isLiveStream(item.streamType)) return false
        // a cached channel page can be shown before the query ends: wait for it a moment, once
        if (!current.waited && current.read.count > 0L) {
            current.waited = true
            try {
                current.read.await(READ_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return item.url in current.newUrls
    }

    /** The corners of the list thumbnails (and of the NUEVO border around them). */
    private const val CORNER_DP = 12f

    /** Rounds a list thumbnail's corners. */
    @JvmStatic
    fun roundCorners(view: View) {
        val radius = CORNER_DP * view.resources.displayMetrics.density
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        view.clipToOutline = true
    }

    /** The NUEVO mark over a thumbnail: a rounded cyan border and the label in a corner. */
    @JvmStatic
    fun badge(context: Context): Drawable = NewBadge(context)

    private class NewBadge(context: Context) : Drawable() {
        private val density = context.resources.displayMetrics.density
        private val label = context.getString(R.string.videoprime_new_label)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = VideoPrimeColors.New.toArgb()
        }
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VideoPrimeColors.New.toArgb() }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = VideoPrimeColors.OnNew.toArgb()
            textSize = 10f * density
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val inset = border.strokeWidth / 2
            val radius = CORNER_DP * density - inset
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            canvas.drawRoundRect(rect, radius, radius, border)
            // a fully rounded pill
            val padX = 8 * density
            val height = 18 * density
            val margin = 6 * density
            val width = text.measureText(label) + padX * 2
            rect.set(b.left + margin, b.top + margin, b.left + margin + width, b.top + margin + height)
            canvas.drawRoundRect(rect, height / 2, height / 2, pill)
            val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2
            canvas.drawText(label, rect.left + padX, baseline, text)
        }

        override fun setAlpha(alpha: Int) {
            border.alpha = alpha
            pill.alpha = alpha
            text.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            border.colorFilter = colorFilter
            pill.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
