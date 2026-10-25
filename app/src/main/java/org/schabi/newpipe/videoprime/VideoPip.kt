package org.schabi.newpipe.videoprime

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.app.PendingIntentCompat
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.notification.NotificationConstants

/**
 * VideoPrime: leaving the app while a video plays shrinks the app itself into Android's
 * picture-in-picture window, as the Twitch app does: the same player and the same surface go on,
 * so the picture never turns black or loads again (the old popup is a different window, and the
 * video has to move to it).
 */
object VideoPip {
    private const val TAG = "VideoPrimePip"
    private const val REQUEST_PLAY_PAUSE = 0x7069

    // Android only accepts aspect ratios between 1:2.39 and 2.39:1
    private const val MAX_RATIO = 2.39f

    /** Whether the app is in the picture-in-picture window (set by the activity). */
    @JvmStatic
    var active = false

    /**
     * Android 12+: the window opens by itself when the user leaves the app (home, recent apps),
     * growing out of exactly the video. Opening it by hand at that moment made Android shrink
     * the whole page first (a flash of the page), see [arm].
     */
    @JvmStatic
    var armed = false
        private set

    /**
     * "Popup" was chosen: go into the window as soon as the main player plays (forgotten after three
     * minutes, so that a video that never loaded does not jump into the window later).
     */
    private var pendingUrl: String? = null
    private var pendingSince = 0L

    /** Once the video page took the request, it lasts a while longer (loading may be slow). */
    private var accepted = false

    /** Asks for the window as soon as the video at [url] plays in the main player. */
    @JvmStatic
    fun requestFor(url: String) {
        pendingUrl = url
        pendingSince = System.currentTimeMillis()
        accepted = false
    }

    /** The video page is opening the requested video (loading it may take a while). */
    @JvmStatic
    fun accept() {
        if (pendingUrl != null) {
            accepted = true
            pendingSince = System.currentTimeMillis()
        }
    }

    /** Whether the window was asked for the video at [url] (and not too long ago). */
    @JvmStatic
    fun isRequestedFor(url: String?): Boolean {
        val pending = pendingUrl ?: return false
        val limit = if (accepted) ACCEPTED_MILLIS else PENDING_MILLIS
        if (url == null || System.currentTimeMillis() - pendingSince >= limit) {
            return false
        }
        // the same video under another address (youtu.be, m.youtube.com, a shared link...)
        return url == pending || streamId(url).let { it != null && it == streamId(pending) }
    }

    private fun streamId(url: String): String? = runCatching {
        NewPipe.getServiceByUrl(url).streamLHFactory.getId(url)
    }.getOrNull()

    @JvmStatic
    fun clearRequest() {
        pendingUrl = null
        accepted = false
    }
    private const val PENDING_MILLIS = 180_000L
    private const val ACCEPTED_MILLIS = 300_000L

    /** The window's "remove the video" button: go on as sound in the background player. */
    const val ACTION_AUDIO_ONLY = "org.schabi.newpipe.videoprime.PIP_AUDIO_ONLY"
    private const val REQUEST_AUDIO_ONLY = 0x7070

    @JvmStatic
    fun isSupported(activity: Activity): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * @param videoView the player's view, the window grows out of it
     * @return whether the window was opened (the user can turn it off for the app)
     */
    @JvmStatic
    fun enter(activity: Activity, player: Player, videoView: View?): Boolean {
        if (!isSupported(activity)) return false
        return try {
            activity.enterPictureInPictureMode(params(activity, player, videoView))
        } catch (e: IllegalStateException) {
            // picture-in-picture turned off for the app in the system settings
            Log.w(TAG, "Could not enter picture-in-picture", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not enter picture-in-picture", e)
            false
        }
    }

    /**
     * Android 12+: whether leaving the app now should open the window by itself (a video playing
     * in the big player), and which part of the screen is the video. Called when the playback,
     * the page or the video's place change.
     */
    @JvmStatic
    fun arm(activity: Activity, player: Player, videoView: View?, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isSupported(activity) || active) return
        if (!enabled && !armed) return
        try {
            activity.setPictureInPictureParams(params(activity, player, videoView, autoEnter = enabled))
            armed = enabled
        } catch (e: IllegalStateException) {
            armed = false
        } catch (e: IllegalArgumentException) {
            armed = false
        }
    }

    /** Android 12+: leaving the app no longer opens the window by itself. */
    @JvmStatic
    fun disarm(activity: Activity?) {
        if (!armed) return
        armed = false
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not disarm picture-in-picture", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not disarm picture-in-picture", e)
        }
    }

    /** Updates the window's play/pause button and shape (called when the playback changes). */
    @JvmStatic
    fun update(activity: Activity, player: Player) {
        if (!active || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            activity.setPictureInPictureParams(params(activity, player, null))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not update picture-in-picture", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not update picture-in-picture", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun params(
        activity: Activity,
        player: Player,
        videoView: View?,
        autoEnter: Boolean? = null
    ): PictureInPictureParams {
        val ratio = aspectRatio(player)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(listOf(audioOnly(activity), playPause(activity, player)))
        videoRect(videoView, ratio)?.let { builder.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // the video keeps playing while the window is resized (false froze it in a
            // cross-fade); the ripple came from the video's size lagging behind the window,
            // see ExpandableSurfaceView.setFillWindow
            builder.setSeamlessResizeEnabled(true)
            // also when only updating, the window must not stop opening by itself
            builder.setAutoEnterEnabled(autoEnter ?: armed)
        }
        return builder.build()
    }

    /**
     * Where the picture is on the screen: the player's view, narrowed to the video's own shape
     * (Android only animates from a hint with the window's aspect ratio; otherwise it shows the
     * whole page shrinking).
     */
    private fun videoRect(videoView: View?, ratio: Rational): Rect? {
        if (videoView == null || !videoView.isAttachedToWindow) return null
        val rect = Rect()
        if (!videoView.getGlobalVisibleRect(rect) || rect.isEmpty) return null
        val wanted = ratio.toFloat()
        val width = rect.width()
        val height = rect.height()
        if (width.toFloat() / height > wanted) {
            val newWidth = (height * wanted).toInt()
            rect.left += (width - newWidth) / 2
            rect.right = rect.left + newWidth
        } else {
            val newHeight = (width / wanted).toInt()
            rect.top += (height - newHeight) / 2
            rect.bottom = rect.top + newHeight
        }
        return rect
    }

    private fun aspectRatio(player: Player): Rational {
        val size = player.exoPlayer?.videoSize
        // the shape seen on screen: anamorphic videos have non-square pixels
        val pixelRatio = size?.pixelWidthHeightRatio?.takeIf { it > 0f } ?: 1f
        var width = ((size?.width ?: 0) * pixelRatio).roundToInt()
        var height = size?.height ?: 0
        if (width <= 0 || height <= 0) {
            width = 16
            height = 9
        }
        val ratio = width.toFloat() / height
        return when {
            ratio > MAX_RATIO -> Rational(239, 100)
            ratio < 1 / MAX_RATIO -> Rational(100, 239)
            else -> Rational(width, height)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun audioOnly(activity: Activity): RemoteAction {
        val label = activity.getString(R.string.videoprime_popup_audio_only)
        val intent = PendingIntentCompat.getBroadcast(
            activity,
            REQUEST_AUDIO_ONLY,
            Intent(ACTION_AUDIO_ONLY).setPackage(activity.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )!!
        return RemoteAction(Icon.createWithResource(activity, R.drawable.ic_headset), label, label, intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun playPause(activity: Activity, player: Player): RemoteAction {
        val playing = player.playWhenReady
        val label = activity.getString(if (playing) R.string.pause else R.string.play)
        val intent = PendingIntentCompat.getBroadcast(
            activity,
            REQUEST_PLAY_PAUSE,
            Intent(NotificationConstants.ACTION_PLAY_PAUSE).setPackage(activity.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )!!
        return RemoteAction(
            Icon.createWithResource(activity, if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
            label,
            label,
            intent
        )
    }
}
