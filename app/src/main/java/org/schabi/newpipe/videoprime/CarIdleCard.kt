package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import org.schabi.newpipe.R

/**
 * PulsePlay: what the car's PulsePlay card shows while nothing plays. Android Auto draws the card
 * itself (its texts, buttons and darkening) over the session's artwork: without a player the
 * session has no metadata and the card stayed grey. Here it gets the app's own background (the
 * violet gradient with the cyan wave of the logo). While something plays, the player's metadata
 * (the song's cover) replaces it.
 *
 * The card only uses the metadata if the session has a playback state: with none (STATE_NONE,
 * what the session has without a player) Android Auto draws its own "Toca para abrir" card and
 * ignores the background. So the idle session says "paused at the start" and offers only what
 * works without a player (playing something chosen from the car's lists or by voice).
 */
object CarIdleCard {
    private const val TAG = "PulsePlayCarCard"

    /** Handled by the session's playback preparer, which works before a player exists. */
    private const val IDLE_ACTIONS = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
        PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH

    @JvmStatic
    fun show(context: Context, session: MediaSessionCompat) {
        try {
            val background = BitmapFactory.decodeResource(context.resources, R.drawable.pulseplay_car_background)
            val title = context.getString(R.string.app_name)
            val subtitle = context.getString(R.string.pulseplay_car_idle_subtitle)
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, background)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, background)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, background)
                    .build()
            )
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f)
                    .setActions(IDLE_ACTIONS)
                    .build()
            )
        } catch (e: RuntimeException) {
            // only looks: the card stays as Android Auto draws it by default
            Log.w(TAG, "Could not set the car card's background", e)
        }
    }
}
