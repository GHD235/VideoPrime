package org.schabi.newpipe.videoprime

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.notification.NotificationConstants

/**
 * PulsePlay: the home screen widget of what is playing: cover, song, artist and previous /
 * play-pause / next. With nothing playing it shows the app's name and any touch opens the app.
 * It is redrawn by the player at the same moments as its notification.
 */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, PlayerService.getCurrentPlayer())
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != null && action in ACTIONS) {
            val player = PlayerService.getCurrentPlayer()
            if (player != null) {
                player.runAction(action)
            } else {
                // the player went away without telling: the widget goes back to "nothing playing"
                update(context, null)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private val ACTIONS = setOf(
            NotificationConstants.ACTION_PLAY_PREVIOUS,
            NotificationConstants.ACTION_PLAY_PAUSE,
            NotificationConstants.ACTION_PLAY_NEXT
        )
        private const val COVER_PIXELS = 160

        /** Redraws every widget on the home screen ([player] null = nothing is playing). */
        @JvmStatic
        fun update(context: Context, player: Player?) {
            val app = context.applicationContext
            runCatching {
                val manager = AppWidgetManager.getInstance(app) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(app, PlayerWidget::class.java))
                if (ids.isEmpty()) return
                manager.updateAppWidget(ids, views(app, player))
            }
        }

        private fun views(app: Context, player: Player?): RemoteViews {
            val views = RemoteViews(app.packageName, R.layout.pulseplay_widget)
            val playing = player != null && player.playQueue != null
            if (playing) {
                views.setTextViewText(R.id.widget_title, player!!.videoTitle)
                views.setTextViewText(R.id.widget_artist, player.uploaderName)
                val cover = player.thumbnail?.let { runCatching { rounded(it) }.getOrNull() }
                if (cover != null) {
                    views.setImageViewBitmap(R.id.widget_cover, cover)
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
                }
            } else {
                views.setTextViewText(R.id.widget_title, app.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, app.getString(R.string.pulseplay_widget_idle))
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            }
            views.setImageViewResource(
                R.id.widget_play,
                if (playing && player!!.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )
            // the rest of the widget opens the app (the music player if music is playing)
            val open = if (playing && player!!.audioPlayerSelected()) {
                Intent(app, MusicPlayerActivity::class.java)
            } else {
                Intent(app, MainActivity::class.java)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val openApp = PendingIntentCompat.getActivity(app, 0, open, PendingIntent.FLAG_UPDATE_CURRENT, false)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            // with nothing playing the buttons open the app too (a broadcast receiver may not
            // open screens on Android 10+)
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                if (playing) action(app, NotificationConstants.ACTION_PLAY_PREVIOUS) else openApp
            )
            views.setOnClickPendingIntent(
                R.id.widget_play,
                if (playing) action(app, NotificationConstants.ACTION_PLAY_PAUSE) else openApp
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                if (playing) action(app, NotificationConstants.ACTION_PLAY_NEXT) else openApp
            )
            return views
        }

        private fun action(app: Context, action: String): PendingIntent? = PendingIntentCompat.getBroadcast(
            app,
            action.hashCode(),
            Intent(app, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )

        /** A small square copy of the cover with round corners (widgets take small bitmaps only). */
        private fun rounded(source: Bitmap): Bitmap {
            val side = minOf(source.width, source.height)
            val square = Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side
            )
            val scaled = Bitmap.createScaledBitmap(square, COVER_PIXELS, COVER_PIXELS, true)
            val out = Bitmap.createBitmap(COVER_PIXELS, COVER_PIXELS, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val radius = COVER_PIXELS * 0.22f
            Canvas(out).drawRoundRect(
                RectF(0f, 0f, COVER_PIXELS.toFloat(), COVER_PIXELS.toFloat()),
                radius,
                radius,
                paint
            )
            return out
        }
    }
}
