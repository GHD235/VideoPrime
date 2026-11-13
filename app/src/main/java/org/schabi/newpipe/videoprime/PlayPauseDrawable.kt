package org.schabi.newpipe.videoprime

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.core.content.ContextCompat
import kotlin.math.min
import org.schabi.newpipe.R

/**
 * PulsePlay: the play and pause icons as one shape that changes into the other (the triangle
 * splits into the two bars and back), as the big video apps do, instead of swapping pictures.
 * Drawn by hand, so it needs no library and is the same on the phone, the TV and in Compose.
 */
object PlayPauseShape {
    /** 0 = play (triangle), 1 = pause (two bars); in a 24x24 box, as Material's icons. */
    private val PLAY = floatArrayOf(
        8f, 5f, 13.5f, 8.5f, 13.5f, 15.5f, 8f, 19f,
        13.5f, 8.5f, 19f, 12f, 19f, 12f, 13.5f, 15.5f
    )
    private val PAUSE = floatArrayOf(
        6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f,
        14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f
    )
    const val DURATION_MILLIS = 180

    /** The two four-corner shapes at [progress], moved to fill a [size] box at ([dx], [dy]). */
    fun points(progress: Float, size: Float, dx: Float, dy: Float): FloatArray {
        val scale = size / 24f
        return FloatArray(PLAY.size) { i ->
            val v = PLAY[i] + (PAUSE[i] - PLAY[i]) * progress
            v * scale + if (i % 2 == 0) dx else dy
        }
    }

    inline fun trace(p: FloatArray, moveTo: (Float, Float) -> Unit, lineTo: (Float, Float) -> Unit, close: () -> Unit) {
        for (shape in 0..1) {
            val o = shape * 8
            moveTo(p[o], p[o + 1])
            lineTo(p[o + 2], p[o + 3])
            lineTo(p[o + 4], p[o + 5])
            lineTo(p[o + 6], p[o + 7])
            close()
        }
    }
}

/** The icon for an ImageButton; [setPlaying] changes it (with the animation if asked). */
class PlayPauseDrawable(context: Context) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.defaultIconTint)
    }
    private val path = Path()
    private val intrinsic = (24 * context.resources.displayMetrics.density).toInt()
    private var tint: ColorStateList? = null
    private var progress = 0f
    private var animator: TimeAnimator? = null
    private var target: Boolean? = null

    private companion object {
        /** A frame counts as this at most: two frames at 60 Hz. */
        const val MAX_FRAME_MILLIS = 34L
        const val HELD_FRAMES = 3
        val EASING = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }

    /** @param pause true for the pause icon (something plays), false for play */
    fun setPlaying(pause: Boolean, animate: Boolean) {
        if (target == pause) return
        target = pause
        animator?.cancel()
        val end = if (pause) 1f else 0f
        if (!animate) {
            progress = end
            invalidateSelf()
            return
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progress = end
            invalidateSelf()
            return
        }
        // Stepped by frames, not by the clock: pressing pause makes the phone busy for a moment
        // (the notification, the session, saving the place; more so the first time) and an
        // animation by the clock jumped over its first half. A late frame now only delays it.
        val from = progress
        val length = PlayPauseShape.DURATION_MILLIS * kotlin.math.abs(end - from)
        var elapsed = 0f
        var frames = 0
        animator = TimeAnimator().apply {
            setTimeListener { animation, _, deltaTime ->
                // only the first frames are held back (the busy moment right after the press);
                // after them the animation follows the clock, so a slow device is not slow motion
                frames++
                elapsed += if (frames <= HELD_FRAMES) min(deltaTime, MAX_FRAME_MILLIS).toFloat() else deltaTime.toFloat()
                val fraction = if (length <= 0f) 1f else min(elapsed / length, 1f)
                progress = from + (end - from) * EASING.getInterpolation(fraction)
                invalidateSelf()
                if (fraction >= 1f) animation.cancel()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val size = min(b.width(), b.height()).toFloat()
        val p = PlayPauseShape.points(
            progress,
            size,
            b.left + (b.width() - size) / 2f,
            b.top + (b.height() - size) / 2f
        )
        path.rewind()
        PlayPauseShape.trace(p, path::moveTo, path::lineTo, path::close)
        canvas.drawPath(path, paint)
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        if (updateColor()) invalidateSelf()
    }

    override fun isStateful(): Boolean = tint?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean = updateColor()

    private fun updateColor(): Boolean {
        val t = tint ?: return false
        val color = t.getColorForState(state, t.defaultColor)
        if (color == paint.color) return false
        paint.color = color
        return true
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = intrinsic

    override fun getIntrinsicHeight(): Int = intrinsic
}

/** The same icon in Compose: changes with an animation when [playing] changes. */
@Composable
fun PlayPauseIcon(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(PlayPauseShape.DURATION_MILLIS, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "playPause"
    )
    val path = remember { ComposePath() }
    Canvas(modifier) {
        val size = min(this.size.width, this.size.height)
        val p = PlayPauseShape.points(
            progress,
            size,
            (this.size.width - size) / 2f,
            (this.size.height - size) / 2f
        )
        path.reset()
        PlayPauseShape.trace(p, path::moveTo, path::lineTo, path::close)
        drawPath(path, color)
    }
}
