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
import org.schabi.newpipe.R
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
    private fun params(activity: Activity, player: Player, videoView: View?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio(player))
            .setActions(listOf(playPause(activity, player)))
        if (videoView != null && videoView.isAttachedToWindow) {
            val hint = Rect()
            if (videoView.getGlobalVisibleRect(hint)) builder.setSourceRectHint(hint)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun aspectRatio(player: Player): Rational {
        val size = player.exoPlayer?.videoSize
        var width = size?.width ?: 0
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
